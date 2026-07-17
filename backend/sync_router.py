"""Personal sync API (Phase 1): health, pairing, device management.

Auth model: ``GET /health`` and ``POST /pair`` are unauthenticated;
everything else requires ``Authorization: Bearer <credential>`` with the
scope the endpoint needs. Pairing tokens can only be minted from loopback
requests, so the router is safe to expose on LAN/Tailscale interfaces.
"""

from __future__ import annotations

import sqlite3
from typing import Callable

from fastapi import APIRouter, Depends, HTTPException, Request

try:
    from .api_models import SyncPairRequest
    from . import sync_auth
except ImportError:  # pragma: no cover - script execution
    from api_models import SyncPairRequest
    import sync_auth

ConnectionFactory = Callable[[], sqlite3.Connection]

LOOPBACK_HOSTS = {"127.0.0.1", "::1", "localhost"}


def default_loopback_check(request: Request) -> bool:
    client = request.client
    return client is not None and client.host in LOOPBACK_HOSTS


def create_sync_router(
    get_conn: ConnectionFactory,
    is_loopback: Callable[[Request], bool] = default_loopback_check,
) -> APIRouter:
    router = APIRouter(prefix="/api/sync/v1", tags=["sync"])

    def require_scope(scope: str):
        def dependency(request: Request) -> sqlite3.Row:
            authorization = request.headers.get("authorization", "")
            credential = ""
            if authorization.startswith("Bearer "):
                credential = authorization[len("Bearer "):].strip()
            with get_conn() as conn:
                sync_auth.ensure_sync_auth_schema(conn)
                device = sync_auth.verify_credential(conn, credential, scope)
            if device is None:
                raise HTTPException(status_code=401, detail="Invalid or missing sync credential")
            return device

        return dependency

    @router.get("/health")
    def health() -> dict:
        with get_conn() as conn:
            sync_auth.ensure_sync_auth_schema(conn)
            return {
                "protocolVersion": sync_auth.SYNC_PROTOCOL_VERSION,
                "serverId": sync_auth.server_id(conn),
                "pairedDevices": sync_auth.paired_device_count(conn),
            }

    @router.post("/pairing-tokens")
    def create_pairing_token(request: Request) -> dict:
        if not is_loopback(request):
            raise HTTPException(status_code=403, detail="Pairing tokens can only be created from the desktop itself")
        with get_conn() as conn:
            sync_auth.ensure_sync_auth_schema(conn)
            token, expires_at = sync_auth.create_pairing_token(conn)
            server = sync_auth.server_id(conn)
        base_url = str(request.base_url).rstrip("/")
        return {
            "token": token,
            "expiresAt": expires_at,
            "pairingPayload": f"csos-sync://pair?endpoint={base_url}&token={token}&server={server}",
        }

    @router.post("/pair")
    def pair(payload: SyncPairRequest) -> dict:
        with get_conn() as conn:
            sync_auth.ensure_sync_auth_schema(conn)
            try:
                device = sync_auth.redeem_pairing_token(conn, payload.token, payload.device_name)
            except sync_auth.PairingError as error:
                raise HTTPException(status_code=401, detail=str(error)) from error
            return {
                **device,
                "protocolVersion": sync_auth.SYNC_PROTOCOL_VERSION,
                "serverId": sync_auth.server_id(conn),
            }

    @router.get("/devices")
    def devices(device: sqlite3.Row = Depends(require_scope(sync_auth.SCOPE_READ))) -> dict:
        with get_conn() as conn:
            sync_auth.ensure_sync_auth_schema(conn)
            return {"devices": sync_auth.list_devices(conn)}

    @router.post("/devices/{device_id}/revoke")
    def revoke(
        device_id: str,
        device: sqlite3.Row = Depends(require_scope(sync_auth.SCOPE_READ)),
    ) -> dict:
        with get_conn() as conn:
            sync_auth.ensure_sync_auth_schema(conn)
            revoked = sync_auth.revoke_device(conn, device_id)
        if not revoked:
            raise HTTPException(status_code=404, detail="Device not found or already revoked")
        return {"revoked": device_id}

    return router
