"""Pairing and credential management for the personal sync protocol (Phase 1).

Model: a one-time pairing token (created only from loopback requests) is
exchanged for a device credential. Credentials are 256-bit bearer secrets;
only their SHA-256 hash is stored server-side. Every sync endpoint beyond
``/health`` and ``/pair`` requires a valid credential, so exposing the sync
router on a non-loopback interface is safe by construction.
"""

from __future__ import annotations

import hashlib
import secrets
import sqlite3
import uuid
from datetime import datetime, timedelta, timezone

SYNC_PROTOCOL_VERSION = 1
SCOPE_READ = "sync:read"
SCOPE_PUSH = "sync:push"
DEFAULT_SCOPES = (SCOPE_READ, SCOPE_PUSH)
PAIRING_TOKEN_TTL_MINUTES = 10

SCHEMA = """
CREATE TABLE IF NOT EXISTS sync_devices (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    credential_hash TEXT NOT NULL UNIQUE,
    scopes TEXT NOT NULL,
    created_at TEXT NOT NULL,
    last_seen_at TEXT NOT NULL DEFAULT '',
    revoked_at TEXT
);

CREATE TABLE IF NOT EXISTS sync_pairing_tokens (
    token_hash TEXT PRIMARY KEY,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    used_at TEXT
);
"""


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def iso(value: datetime) -> str:
    return value.isoformat()


def parse(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def hash_secret(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def ensure_sync_auth_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(SCHEMA)


def server_id(conn: sqlite3.Connection) -> str:
    """Stable installation identity; generated once and kept in schema_meta."""
    row = conn.execute("SELECT value FROM schema_meta WHERE key = 'sync_server_id'").fetchone()
    if row:
        return row["value"]
    value = uuid.uuid4().hex
    conn.execute(
        """
        INSERT INTO schema_meta (key, value, updated_at)
        VALUES ('sync_server_id', ?, ?)
        ON CONFLICT(key) DO UPDATE SET value = excluded.value
        """,
        (value, iso(utc_now())),
    )
    conn.commit()
    return value


def create_pairing_token(conn: sqlite3.Connection) -> tuple[str, str]:
    """Return a new one-time (token, expires_at). Only the hash is stored."""
    token = secrets.token_urlsafe(24)
    now = utc_now()
    expires = now + timedelta(minutes=PAIRING_TOKEN_TTL_MINUTES)
    conn.execute(
        "INSERT INTO sync_pairing_tokens (token_hash, created_at, expires_at) VALUES (?, ?, ?)",
        (hash_secret(token), iso(now), iso(expires)),
    )
    conn.commit()
    return token, iso(expires)


class PairingError(Exception):
    """Raised when a pairing token is unknown, expired, or already used."""


def redeem_pairing_token(conn: sqlite3.Connection, token: str, device_name: str) -> dict:
    row = conn.execute(
        "SELECT * FROM sync_pairing_tokens WHERE token_hash = ?",
        (hash_secret(token.strip()),),
    ).fetchone()
    if not row or row["used_at"] or parse(row["expires_at"]) <= utc_now():
        raise PairingError("pairing token is invalid, expired, or already used")

    device_id = secrets.token_hex(8)
    credential = f"css_{secrets.token_urlsafe(32)}"
    now = iso(utc_now())
    conn.execute("UPDATE sync_pairing_tokens SET used_at = ? WHERE token_hash = ?", (now, row["token_hash"]))
    conn.execute(
        """
        INSERT INTO sync_devices (id, name, credential_hash, scopes, created_at)
        VALUES (?, ?, ?, ?, ?)
        """,
        (
            device_id,
            device_name.strip() or "android-device",
            hash_secret(credential),
            " ".join(DEFAULT_SCOPES),
            now,
        ),
    )
    conn.commit()
    return {
        "deviceId": device_id,
        "credential": credential,
        "scopes": list(DEFAULT_SCOPES),
    }


def verify_credential(
    conn: sqlite3.Connection,
    credential: str,
    required_scope: str,
) -> sqlite3.Row | None:
    if not credential:
        return None
    row = conn.execute(
        "SELECT * FROM sync_devices WHERE credential_hash = ? AND revoked_at IS NULL",
        (hash_secret(credential),),
    ).fetchone()
    if not row:
        return None
    if required_scope not in row["scopes"].split():
        return None
    conn.execute(
        "UPDATE sync_devices SET last_seen_at = ? WHERE id = ?",
        (iso(utc_now()), row["id"]),
    )
    conn.commit()
    return row


def list_devices(conn: sqlite3.Connection) -> list[dict]:
    rows = conn.execute(
        """
        SELECT id, name, scopes, created_at, last_seen_at, revoked_at
        FROM sync_devices
        ORDER BY created_at DESC
        """
    ).fetchall()
    return [
        {
            "id": row["id"],
            "name": row["name"],
            "scopes": row["scopes"].split(),
            "createdAt": row["created_at"],
            "lastSeenAt": row["last_seen_at"],
            "revokedAt": row["revoked_at"],
        }
        for row in rows
    ]


def revoke_device(conn: sqlite3.Connection, device_id: str) -> bool:
    cursor = conn.execute(
        "UPDATE sync_devices SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL",
        (iso(utc_now()), device_id),
    )
    conn.commit()
    return cursor.rowcount > 0


def paired_device_count(conn: sqlite3.Connection) -> int:
    row = conn.execute(
        "SELECT COUNT(*) AS c FROM sync_devices WHERE revoked_at IS NULL"
    ).fetchone()
    return int(row["c"])
