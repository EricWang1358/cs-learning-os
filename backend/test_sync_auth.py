"""Phase 1 acceptance tests for sync pairing and credential auth."""

from __future__ import annotations

from datetime import timedelta
from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from backend import sync_auth
from backend.db import connect, initialize
from backend.sync_router import create_sync_router


def build_client(db_path: Path, loopback: bool = True) -> TestClient:
    def get_conn():
        conn = connect(db_path)
        initialize(conn)
        return conn

    app = FastAPI()
    app.include_router(create_sync_router(get_conn, is_loopback=lambda request: loopback))
    return TestClient(app)


def pair_device(client: TestClient, device_name: str = "pixel-test") -> dict:
    token = client.post("/api/sync/v1/pairing-tokens").json()["token"]
    response = client.post("/api/sync/v1/pair", json={"token": token, "device_name": device_name})
    assert response.status_code == 200, response.text
    return response.json()


def test_health_is_unauthenticated_and_stable(tmp_path: Path) -> None:
    client = build_client(tmp_path / "knowledge.db")
    first = client.get("/api/sync/v1/health")
    assert first.status_code == 200
    body = first.json()
    assert body["protocolVersion"] == sync_auth.SYNC_PROTOCOL_VERSION
    assert body["pairedDevices"] == 0
    second = client.get("/api/sync/v1/health").json()
    assert body["serverId"] == second["serverId"]


def test_pairing_token_requires_loopback(tmp_path: Path) -> None:
    client = build_client(tmp_path / "knowledge.db", loopback=False)
    response = client.post("/api/sync/v1/pairing-tokens")
    assert response.status_code == 403


def test_full_pairing_flow_and_device_listing(tmp_path: Path) -> None:
    client = build_client(tmp_path / "knowledge.db")
    token_response = client.post("/api/sync/v1/pairing-tokens")
    assert token_response.status_code == 200
    payload = token_response.json()
    assert payload["token"]
    assert payload["pairingPayload"].startswith("csos-sync://pair?")

    device = pair_device(client)
    assert device["deviceId"]
    assert device["credential"].startswith("css_")
    assert device["scopes"] == list(sync_auth.DEFAULT_SCOPES)

    listed = client.get(
        "/api/sync/v1/devices",
        headers={"Authorization": f"Bearer {device['credential']}"},
    )
    assert listed.status_code == 200
    devices = listed.json()["devices"]
    assert len(devices) == 1
    assert devices[0]["id"] == device["deviceId"]
    assert devices[0]["name"] == "pixel-test"
    # The credential and its hash must never appear in API responses.
    assert "credential" not in devices[0]
    assert "credential_hash" not in devices[0]
    assert device["credential"] not in listed.text
    assert sync_auth.hash_secret(device["credential"]) not in listed.text

    health = client.get("/api/sync/v1/health").json()
    assert health["pairedDevices"] == 1


def test_pairing_token_is_single_use(tmp_path: Path) -> None:
    client = build_client(tmp_path / "knowledge.db")
    token = client.post("/api/sync/v1/pairing-tokens").json()["token"]
    first = client.post("/api/sync/v1/pair", json={"token": token, "device_name": "one"})
    assert first.status_code == 200
    second = client.post("/api/sync/v1/pair", json={"token": token, "device_name": "two"})
    assert second.status_code == 401


def test_expired_pairing_token_is_rejected(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    token = client.post("/api/sync/v1/pairing-tokens").json()["token"]
    with connect(db_path) as conn:
        initialize(conn)
        expired = sync_auth.iso(sync_auth.utc_now() - timedelta(minutes=1))
        conn.execute(
            "UPDATE sync_pairing_tokens SET expires_at = ? WHERE token_hash = ?",
            (expired, sync_auth.hash_secret(token)),
        )
        conn.commit()
    response = client.post("/api/sync/v1/pair", json={"token": token, "device_name": "late"})
    assert response.status_code == 401


def test_unknown_token_and_bad_credential_are_rejected(tmp_path: Path) -> None:
    client = build_client(tmp_path / "knowledge.db")
    response = client.post("/api/sync/v1/pair", json={"token": "nonsense", "device_name": "ghost"})
    assert response.status_code == 401
    missing = client.get("/api/sync/v1/devices")
    assert missing.status_code == 401
    wrong = client.get("/api/sync/v1/devices", headers={"Authorization": "Bearer css_wrong"})
    assert wrong.status_code == 401


def test_revoked_device_credential_stops_working(tmp_path: Path) -> None:
    client = build_client(tmp_path / "knowledge.db")
    device = pair_device(client)
    headers = {"Authorization": f"Bearer {device['credential']}"}

    revoke = client.post(f"/api/sync/v1/devices/{device['deviceId']}/revoke", headers=headers)
    assert revoke.status_code == 200

    after = client.get("/api/sync/v1/devices", headers=headers)
    assert after.status_code == 401
    health = client.get("/api/sync/v1/health").json()
    assert health["pairedDevices"] == 0


def test_server_reset_detection_identity(tmp_path: Path) -> None:
    # A fresh database means a fresh serverId; clients holding the old one
    # must re-baseline instead of trusting their cursor (Phase 2 contract).
    first = build_client(tmp_path / "a.db").get("/api/sync/v1/health").json()
    second = build_client(tmp_path / "b.db").get("/api/sync/v1/health").json()
    assert first["serverId"] != second["serverId"]
