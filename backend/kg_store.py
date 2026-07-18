"""SQLite persistence layer for the KnowledgeGraph module (RFC-knowledge-graph §3.3).

Merge notes (package → real backend adaptation):
- kg_* five tables follow RFC §3.3 verbatim (INTEGER ms timestamps, soft-delete
  tombstones, partial unique indexes) so Android Room v9 and this backend share DDL.
- Cross-module foreign keys to `nodes` are intentionally NOT declared: the desktop
  content pipeline rebuilds `nodes` from markdown on every ingest (DELETE + reinsert),
  which hard FKs would block. Referential integrity is enforced at the application
  layer (nodes are created file-backed before edges may reference them).
- `areas` does not exist on the desktop backend (area is a plain TEXT column on
  nodes), so `kg_question.area_id` is a plain namespace string without FK.
- Idempotency + outbox stay self-contained in `kg_processed_commands` / `kg_outbox`
  so the RFC §3.1 transaction pipeline (kg write + idempotency + outbox in one
  transaction) holds without pretending to join Android's tables. Node creation
  additionally flows through sync_changes via node_lifecycle_service, so kg-created
  notes sync to mobile through the existing protocol; syncing the kg graph
  structure itself is a documented later phase (sync_changes CHECK extension).
"""
from __future__ import annotations

import json
import sqlite3
import threading
import time
import uuid
from contextlib import contextmanager

try:
    from .db import initialize as initialize_core_schema
    from .kg_engine import fingerprint
except ImportError:  # pragma: no cover - script execution
    from db import initialize as initialize_core_schema
    from kg_engine import fingerprint


class KgError(Exception):
    """Domain error mirroring RFC §3.2 KgError; `kind` is the wire `error` field."""

    def __init__(self, error_kind: str, http_status: int, **details):
        super().__init__(f"{error_kind}: {details}")
        self.kind = error_kind
        self.http_status = http_status
        self.details = details


# --- RFC §3.3 Schema v9 (pure additive; shared with Android Room v9) ---
# Cross-module REFERENCES to nodes are trimmed (see module docstring); the
# kg_edge.scope_question_id → kg_question reference stays (same module).
# The RFC's COALESCE expression unique indexes are rewritten as equivalent
# pairs of plain-column partial unique indexes (controlled deviation #4, see
# android Migration8To9 KDoc): expression indexes need SQLite 3.9+, while
# Robolectric's sqlite4java and older devices ship 3.8.x. NULL-bucket
# semantics are identical — the WHERE clauses keep NULLs out of the index.
SCHEMA_KG_DDL = """
CREATE TABLE IF NOT EXISTS kg_question (
  question_id TEXT PRIMARY KEY NOT NULL,
  root_node_id TEXT NOT NULL,
  area_id TEXT,
  problem_no INTEGER NOT NULL,
  title TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'CS_BASIC',
  jd_batch_id TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE',          -- ACTIVE | ARCHIVED
  revision INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_question_areano_null
  ON kg_question(problem_no) WHERE status != 'ARCHIVED' AND area_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_question_areano_area
  ON kg_question(area_id, problem_no) WHERE status != 'ARCHIVED' AND area_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS kg_edge (
  edge_id TEXT PRIMARY KEY NOT NULL,
  parent_node_id TEXT NOT NULL,   -- parent depends on child (child is prerequisite)
  child_node_id  TEXT NOT NULL,
  scope_type TEXT NOT NULL DEFAULT 'GLOBAL',      -- GLOBAL | PROBLEM_LOCAL
  scope_question_id TEXT REFERENCES kg_question(question_id),
  status TEXT NOT NULL DEFAULT 'ACTIVE',          -- ACTIVE | PENDING_CONFIRMATION | REJECTED
  created_by TEXT NOT NULL DEFAULT 'USER',        -- USER | AI | IMPORT
  revision INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_edge_live_global
  ON kg_edge(parent_node_id, child_node_id, scope_type)
  WHERE status != 'REJECTED' AND scope_question_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_edge_live_scoped
  ON kg_edge(parent_node_id, child_node_id, scope_type, scope_question_id)
  WHERE status != 'REJECTED' AND scope_question_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_kg_edge_parent ON kg_edge(parent_node_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_kg_edge_child  ON kg_edge(child_node_id)  WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_kg_edge_scopeq ON kg_edge(scope_question_id);

CREATE TABLE IF NOT EXISTS kg_proposal (
  proposal_id TEXT PRIMARY KEY NOT NULL,
  kind TEXT NOT NULL,                              -- PREREQUISITE_CHAIN | JD_DECOMPOSITION
  payload_json TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',          -- PENDING | CONFIRMED | REJECTED | EXPIRED
  model_ref TEXT, command_id TEXT,
  expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS kg_mastery (
  node_id TEXT PRIMARY KEY NOT NULL,
  state TEXT NOT NULL DEFAULT 'UNKNOWN',           -- UNKNOWN|LEARNING|FRAGILE|MASTERED
  score REAL NOT NULL DEFAULT 0.0,
  attempts INTEGER NOT NULL DEFAULT 0,
  fail_streak INTEGER NOT NULL DEFAULT 0,
  last_verdict TEXT, updated_at INTEGER NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS kg_mastery_event (
  event_id TEXT PRIMARY KEY NOT NULL,
  node_id TEXT NOT NULL, quiz_item_id TEXT NOT NULL,
  verdict TEXT NOT NULL, command_id TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

-- Self-contained idempotency + outbox (RFC §3.1 pipeline; desktop-local tables).
CREATE TABLE IF NOT EXISTS kg_processed_commands (
  command_id TEXT PRIMARY KEY NOT NULL,
  fingerprint TEXT NOT NULL,
  result_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS kg_outbox (
  outbox_id TEXT PRIMARY KEY NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  op TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
"""


class KgGraphStore:
    """Single-connection SQLite store. All writes serialize through
    transaction()/run_idempotent() so kg writes, idempotency registration and
    outbox appends commit atomically (RFC §3.1 invariant)."""

    def __init__(self, db_path: str = "cs_learning_os.db", now_ms=None, id_gen=None):
        self.db_path = db_path
        self._conn = sqlite3.connect(db_path, check_same_thread=False,
                                     isolation_level=None)  # explicit BEGIN/COMMIT
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA busy_timeout = 5000")
        self.lock = threading.RLock()
        self._now_ms = now_ms or (lambda: int(time.time() * 1000))
        self._id_gen = id_gen or (lambda: uuid.uuid4().hex)
        with self.lock:
            initialize_core_schema(self._conn)  # nodes/quizzes/... (idempotent)
            self._conn.executescript(SCHEMA_KG_DDL)

    @property
    def connection(self) -> sqlite3.Connection:
        """Underlying connection — helpers that need a Connection (e.g.
        node_lifecycle_service.upsert_node_file_in_conn) may use it *inside* a
        transaction() block; all statements then join the open transaction."""
        return self._conn

    # -- infrastructure -------------------------------------------------------
    def now(self) -> int:
        return int(self._now_ms())

    def new_id(self) -> str:
        return self._id_gen()

    @contextmanager
    def transaction(self):
        """Write transaction (BEGIN IMMEDIATE): kg write + idempotency + outbox."""
        with self.lock:
            cur = self._conn.cursor()
            cur.execute("BEGIN IMMEDIATE")
            try:
                yield cur
                cur.execute("COMMIT")
            except BaseException:
                cur.execute("ROLLBACK")
                raise

    def read(self, fn):
        """Read-only query fn(cursor) — mutually exclusive with writes, always
        reads the committed snapshot."""
        with self.lock:
            return fn(self._conn.cursor())

    def write(self, fn):
        """Non-idempotent write (e.g. proposal registration): still transactional."""
        with self.transaction() as cur:
            return fn(cur)

    def run_idempotent(self, command_id: str, fp_material, write_fn):
        """Idempotent command pipeline (RFC §3.1):

        - Same transaction lookup in kg_processed_commands:
          same commandId + same fingerprint → cached result (replayed=True);
          same commandId + different fingerprint → 409 CommandConflict;
        - otherwise run write_fn(cursor), register the command, commit.
        fingerprint = SHA-256 of the canonical JSON request material (caller-built).
        """
        fp = fingerprint(fp_material)
        with self.transaction() as cur:
            row = cur.execute(
                "SELECT fingerprint, result_json FROM kg_processed_commands"
                " WHERE command_id = ?", (command_id,)).fetchone()
            if row is not None:
                if row["fingerprint"] == fp:
                    return json.loads(row["result_json"]), True
                raise KgError("CommandConflict", 409, commandId=command_id)
            result = write_fn(cur)
            cur.execute(
                "INSERT INTO kg_processed_commands"
                " (command_id, fingerprint, result_json, created_at)"
                " VALUES (?, ?, ?, ?)",
                (command_id, fp, json.dumps(result, ensure_ascii=False), self.now()))
            return result, False

    def append_outbox(self, cur, entity_type: str, entity_id: str, op: str, payload):
        """kg_outbox append (same transaction as the business write)."""
        cur.execute(
            "INSERT INTO kg_outbox"
            " (outbox_id, entity_type, entity_id, op, payload_json, created_at)"
            " VALUES (?, ?, ?, ?, ?, ?)",
            (self.new_id(), entity_type, entity_id, op,
             json.dumps(payload, ensure_ascii=False), self.now()))

    def close(self):
        with self.lock:
            self._conn.close()
