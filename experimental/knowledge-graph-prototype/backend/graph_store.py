"""SQLite persistence layer for the KnowledgeGraph module (stdlib sqlite3 only).

- Schema v9 DDL (RFC §3.3) 原样引用, 另加最小占位表 learning_nodes / areas /
  quiz_items / processed_commands / replication_outbox 以支撑外键与演示。
- 事务管线 (RFC §3.1 不变量): 所有写在同一事务内完成
  kg 表写入 + processed_commands 幂等登记 + replication_outbox 追加。
- 默认文件库 cs_learning_os.db; 测试用 ":memory:" / 临时文件。
"""
from __future__ import annotations

import json
import sqlite3
import threading
import time
import uuid
from contextlib import contextmanager

from graph_engine import fingerprint


class KgError(Exception):
    """领域错误, 与 RFC §3.2 KgError 一一对应; `kind` 即线上的 error 字段。
    details 中可再带 RFC 字段 (如 NotFound 的 kind/id) — 故首参名 error_kind。"""

    def __init__(self, error_kind: str, http_status: int, **details):
        super().__init__(f"{error_kind}: {details}")
        self.kind = error_kind
        self.http_status = http_status
        self.details = details


# --- RFC §3.3 Schema v9 (纯增量, 只 CREATE; Room 与 SQLite/FastAPI 共用此 DDL) --
SCHEMA_V9_DDL = """
CREATE TABLE IF NOT EXISTS kg_question (
  question_id TEXT PRIMARY KEY NOT NULL,
  root_node_id TEXT NOT NULL REFERENCES learning_nodes(id),
  area_id TEXT REFERENCES areas(id),
  problem_no INTEGER NOT NULL,
  title TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'CS_BASIC',
  jd_batch_id TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE',          -- ACTIVE | ARCHIVED
  revision INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_question_areano
  ON kg_question(COALESCE(area_id,''), problem_no) WHERE status != 'ARCHIVED';

CREATE TABLE IF NOT EXISTS kg_edge (
  edge_id TEXT PRIMARY KEY NOT NULL,
  parent_node_id TEXT NOT NULL REFERENCES learning_nodes(id),   -- parent 依赖 child
  child_node_id  TEXT NOT NULL REFERENCES learning_nodes(id),
  scope_type TEXT NOT NULL DEFAULT 'GLOBAL',      -- GLOBAL | PROBLEM_LOCAL
  scope_question_id TEXT REFERENCES kg_question(question_id),
  status TEXT NOT NULL DEFAULT 'ACTIVE',          -- ACTIVE | PENDING_CONFIRMATION | REJECTED
  created_by TEXT NOT NULL DEFAULT 'USER',        -- USER | AI | IMPORT
  revision INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_kg_edge_live
  ON kg_edge(parent_node_id, child_node_id, scope_type, COALESCE(scope_question_id,''))
  WHERE status != 'REJECTED';
CREATE INDEX IF NOT EXISTS idx_kg_edge_parent ON kg_edge(parent_node_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_kg_edge_child  ON kg_edge(child_node_id)  WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS kg_proposal (
  proposal_id TEXT PRIMARY KEY NOT NULL,
  kind TEXT NOT NULL,                              -- PREREQUISITE_CHAIN | JD_DECOMPOSITION
  payload_json TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',          -- PENDING | CONFIRMED | REJECTED | EXPIRED
  model_ref TEXT, command_id TEXT,
  expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS kg_mastery (
  node_id TEXT PRIMARY KEY NOT NULL REFERENCES learning_nodes(id),
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
"""

# --- 最小占位表: 支撑 kg 表外键、幂等命令管线与演示 (非 RFC 新增, 原样复用语义) --
PLACEHOLDER_DDL = """
CREATE TABLE IF NOT EXISTS learning_nodes (
  id TEXT PRIMARY KEY NOT NULL,
  title TEXT NOT NULL,
  markdown_body TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS areas (
  id TEXT PRIMARY KEY NOT NULL
);
CREATE TABLE IF NOT EXISTS quiz_items (
  id TEXT PRIMARY KEY NOT NULL,
  node_id TEXT REFERENCES learning_nodes(id),
  created_at INTEGER
);
CREATE TABLE IF NOT EXISTS processed_commands (
  command_id TEXT PRIMARY KEY NOT NULL,
  fingerprint TEXT NOT NULL,
  result_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS replication_outbox (
  outbox_id TEXT PRIMARY KEY NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  op TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
"""


class GraphStore:
    """单连接 SQLite 存储。所有写经 transaction()/run_idempotent() 串行化。"""

    def __init__(self, db_path: str = "cs_learning_os.db", now_ms=None, id_gen=None):
        self.db_path = db_path
        self._conn = sqlite3.connect(db_path, check_same_thread=False,
                                     isolation_level=None)  # 显式 BEGIN/COMMIT
        self._conn.row_factory = sqlite3.Row
        self.lock = threading.RLock()
        self._now_ms = now_ms or (lambda: int(time.time() * 1000))
        self._id_gen = id_gen or (lambda: uuid.uuid4().hex)
        with self.lock:
            self._conn.executescript(PLACEHOLDER_DDL)  # 被引用表先行
            self._conn.executescript(SCHEMA_V9_DDL)

    # -- 基础设施 ------------------------------------------------------------
    def now(self) -> int:
        return int(self._now_ms())

    def new_id(self) -> str:
        return self._id_gen()

    @contextmanager
    def transaction(self):
        """写事务 (BEGIN IMMEDIATE): kg 写 + 幂等登记 + outbox 追加同事务。"""
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
        """只读查询 fn(cursor) — 与写事务互斥, 始终读到已提交快照。"""
        with self.lock:
            return fn(self._conn.cursor())

    def write(self, fn):
        """非幂等写 (如 proposal 登记): 仍走事务 + outbox。"""
        with self.transaction() as cur:
            return fn(cur)

    def run_idempotent(self, command_id: str, fp_material, write_fn):
        """幂等命令管线 (RFC §3.1):

        - 同事务内查 processed_commands:
          同 commandId + 同 fingerprint → 返回缓存结果 (replayed=True);
          同 commandId + 异 fingerprint → 409 CommandConflict;
        - 否则执行 write_fn(cursor), 登记 processed_commands 并提交。
        fingerprint = 请求体 canonical JSON(排序键) 的 SHA-256 (调用方组料)。
        """
        fp = fingerprint(fp_material)
        with self.transaction() as cur:
            row = cur.execute(
                "SELECT fingerprint, result_json FROM processed_commands"
                " WHERE command_id = ?", (command_id,)).fetchone()
            if row is not None:
                if row["fingerprint"] == fp:
                    return json.loads(row["result_json"]), True
                raise KgError("CommandConflict", 409, commandId=command_id)
            result = write_fn(cur)
            cur.execute(
                "INSERT INTO processed_commands"
                " (command_id, fingerprint, result_json, created_at)"
                " VALUES (?, ?, ?, ?)",
                (command_id, fp, json.dumps(result, ensure_ascii=False), self.now()))
            return result, False

    def append_outbox(self, cur, entity_type: str, entity_id: str, op: str, payload):
        """replication_outbox 追加 (与业务写同事务, 可表达"删边"等软删)。"""
        cur.execute(
            "INSERT INTO replication_outbox"
            " (outbox_id, entity_type, entity_id, op, payload_json, created_at)"
            " VALUES (?, ?, ?, ?, ?, ?)",
            (self.new_id(), entity_type, entity_id, op,
             json.dumps(payload, ensure_ascii=False), self.now()))

    def close(self):
        with self.lock:
            self._conn.close()
