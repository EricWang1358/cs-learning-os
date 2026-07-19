"""Personal sync API (Phase 1): health, pairing, device management.

Auth model: ``GET /health`` and ``POST /pair`` are unauthenticated;
everything else requires ``Authorization: Bearer <credential>`` with the
scope the endpoint needs. Pairing tokens can only be minted from loopback
requests, so the router is safe to expose on LAN/Tailscale interfaces.
"""

from __future__ import annotations

import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable
from urllib.parse import urlencode

from fastapi import APIRouter, Depends, HTTPException, Request

try:
    from .api_models import (
        SyncDeviceScopesUpdate,
        SyncManifestRequest,
        SyncPairRequest,
        SyncPullRequest,
        SyncPushAttemptsRequest,
        SyncPushCapturesRequest,
        SyncPushNodesRequest,
        SyncPushQuizzesRequest,
        SyncPushQuestionsRequest,
        SyncPushBiteCardsRequest,
    )
    from . import sync_auth, sync_package, sync_service
except ImportError:  # pragma: no cover - script execution
    from api_models import (
        SyncDeviceScopesUpdate,
        SyncManifestRequest,
        SyncPairRequest,
        SyncPullRequest,
        SyncPushAttemptsRequest,
        SyncPushCapturesRequest,
        SyncPushNodesRequest,
        SyncPushQuizzesRequest,
        SyncPushQuestionsRequest,
        SyncPushBiteCardsRequest,
    )
    import sync_auth
    import sync_service
    import sync_package

ConnectionFactory = Callable[[], sqlite3.Connection]

LOOPBACK_HOSTS = {"127.0.0.1", "::1", "localhost"}


def default_loopback_check(request: Request) -> bool:
    client = request.client
    return client is not None and client.host in LOOPBACK_HOSTS


def create_sync_router(
    get_conn: ConnectionFactory,
    is_loopback: Callable[[Request], bool] = default_loopback_check,
    export_root: Path | None = None,
    content_root: Path | None = None,
    advertised_base_url: str | None = None,
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

    def credential_from_request(request: Request) -> str:
        authorization = request.headers.get("authorization", "")
        if authorization.startswith("Bearer "):
            return authorization[len("Bearer "):].strip()
        return ""

    def require_credential(request: Request) -> dict:
        credential = credential_from_request(request)
        with get_conn() as conn:
            sync_auth.ensure_sync_auth_schema(conn)
            device = sync_auth.verify_device_credential(conn, credential)
        if device is None:
            raise HTTPException(status_code=401, detail="Invalid or missing sync credential")
        return device

    def require_device_management(request: Request) -> sqlite3.Row | None:
        if is_loopback(request):
            return None
        return require_scope(sync_auth.SCOPE_READ)(request)

    def require_desktop_management(request: Request) -> None:
        if not is_loopback(request):
            raise HTTPException(status_code=403, detail="Device permissions can only be managed from the desktop")

    @router.get("/health")
    def health() -> dict:
        with get_conn() as conn:
            sync_auth.ensure_sync_auth_schema(conn)
            return {
                "protocolVersion": sync_auth.SYNC_PROTOCOL_VERSION,
                "serverId": sync_auth.server_id(conn),
                "pairedDevices": sync_auth.paired_device_count(conn),
                "advertisedBaseUrl": advertised_base_url,
            }

    @router.post("/pairing-tokens")
    def create_pairing_token(request: Request) -> dict:
        if not is_loopback(request):
            raise HTTPException(status_code=403, detail="Pairing tokens can only be created from the desktop itself")
        with get_conn() as conn:
            sync_auth.ensure_sync_auth_schema(conn)
            token, expires_at = sync_auth.create_pairing_token(conn)
            server = sync_auth.server_id(conn)
        base_url = advertised_base_url or str(request.base_url).rstrip("/")
        pairing_payload = "csos-sync://pair?" + urlencode(
            {"endpoint": base_url, "token": token, "server": server}
        )
        return {
            "token": token,
            "expiresAt": expires_at,
            "endpoint": base_url,
            "pairingPayload": pairing_payload,
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
    def devices(device=Depends(require_device_management)) -> dict:
        with get_conn() as conn:
            sync_auth.ensure_sync_auth_schema(conn)
            return {"devices": sync_auth.list_devices(conn)}

    @router.get("/device")
    def current_device(device=Depends(require_credential)) -> dict:
        return {
            "id": device["id"],
            "name": device["name"],
            "scopes": device["scopes"],
            "revokedAt": device["revokedAt"],
        }

    @router.put("/devices/{device_id}/scopes")
    def update_device_scopes(
        device_id: str,
        payload: SyncDeviceScopesUpdate,
        desktop=Depends(require_desktop_management),
    ) -> dict:
        with get_conn() as conn:
            sync_auth.ensure_sync_auth_schema(conn)
            device = sync_auth.update_device_scopes(conn, device_id, payload.scopes)
        if device is None:
            raise HTTPException(status_code=404, detail="Device not found or already revoked")
        return {"device": device}

    @router.post("/push/attempts")
    def push_attempts(
        payload: SyncPushAttemptsRequest,
        device: sqlite3.Row = Depends(require_scope(sync_auth.SCOPE_PUSH)),
    ) -> dict:
        with get_conn() as conn:
            receipts = sync_service.push_attempts(conn, [item.model_dump() for item in payload.items])
            return {"receipts": receipts}

    @router.post("/push/captures")
    def push_captures(
        payload: SyncPushCapturesRequest,
        device: sqlite3.Row = Depends(require_scope(sync_auth.SCOPE_PUSH)),
    ) -> dict:
        with get_conn() as conn:
            receipts = sync_service.push_captures(conn, [item.model_dump() for item in payload.items])
            return {"receipts": receipts}

    @router.post("/push/reader-questions")
    def push_reader_questions(
        payload: SyncPushQuestionsRequest,
        device: sqlite3.Row = Depends(require_scope(sync_auth.SCOPE_PUSH)),
    ) -> dict:
        with get_conn() as conn:
            receipts = sync_service.push_reader_questions(conn, [item.model_dump() for item in payload.items])
            return {"receipts": receipts}

    @router.post("/push/nodes")
    def push_nodes(
        payload: SyncPushNodesRequest,
        device: sqlite3.Row = Depends(require_scope(sync_auth.SCOPE_PUSH)),
    ) -> dict:
        if content_root is None:
            raise HTTPException(status_code=400, detail="Node sync is not configured on this desktop")
        with get_conn() as conn:
            receipts = sync_service.push_nodes(
                conn,
                content_root,
                device["id"],
                [item.model_dump() for item in payload.items],
            )
            return {"receipts": receipts}

    @router.post("/push/bite-cards")
    def push_bite_cards(
        payload: SyncPushBiteCardsRequest,
        device: sqlite3.Row = Depends(require_scope(sync_auth.SCOPE_PUSH)),
    ) -> dict:
        with get_conn() as conn:
            receipts = sync_service.push_bite_cards(
                conn,
                device["id"],
                [item.model_dump() for item in payload.items],
            )
            return {"receipts": receipts}

    @router.post("/push/quizzes")
    def push_quizzes(
        payload: SyncPushQuizzesRequest,
        device: sqlite3.Row = Depends(require_scope(sync_auth.SCOPE_PUSH)),
    ) -> dict:
        if content_root is None:
            raise HTTPException(status_code=400, detail="Quiz sync is not configured on this desktop")
        with get_conn() as conn:
            receipts = sync_service.push_quizzes(
                conn,
                content_root,
                device["id"],
                [item.model_dump() for item in payload.items],
            )
            return {"receipts": receipts}

    @router.post("/export/package")
    def export_package(
        device: sqlite3.Row = Depends(require_scope(sync_auth.SCOPE_READ)),
    ) -> dict:
        if export_root is None:
            raise HTTPException(status_code=400, detail="Package export is not configured on this desktop")
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
        out_path = export_root / f"cs-learning-os-package-{timestamp}.zip"
        with get_conn() as conn:
            manifest = sync_package.build_sync_package(conn, out_path)
        return {
            "fileName": out_path.name,
            "path": str(out_path),
            "counts": manifest["counts"],
        }

    @router.post("/manifest")
    def manifest(
        payload: SyncManifestRequest,
        device: sqlite3.Row = Depends(require_scope(sync_auth.SCOPE_READ)),
    ) -> dict:
        with get_conn() as conn:
            sync_auth.ensure_sync_auth_schema(conn)
            current_server = sync_auth.server_id(conn)
            if payload.serverId and payload.serverId != current_server:
                return {
                    "reset": True,
                    "protocolVersion": sync_auth.SYNC_PROTOCOL_VERSION,
                    "serverId": current_server,
                    "cursor": 0,
                    "changes": [],
                    "hasMore": False,
                }
            scope = sync_service.SyncScope.from_payload(payload.scope)
            result = sync_service.manifest_changes(
                conn,
                payload.cursor,
                scope,
                now=sync_auth.iso(sync_auth.utc_now()),
            )
            return {
                "reset": False,
                "protocolVersion": sync_auth.SYNC_PROTOCOL_VERSION,
                "serverId": current_server,
                **result,
            }

    @router.post("/pull")
    def pull(
        payload: SyncPullRequest,
        device: sqlite3.Row = Depends(require_scope(sync_auth.SCOPE_READ)),
    ) -> dict:
        if payload.entityType not in sync_service.ENTITY_TYPES:
            raise HTTPException(status_code=400, detail=f"Unsupported entity type: {payload.entityType}")
        with get_conn() as conn:
            scope = sync_service.SyncScope.from_payload(payload.scope)
            records = sync_service.pull_records(
                conn,
                payload.entityType,
                payload.ids,
                scope,
                now=sync_auth.iso(sync_auth.utc_now()),
            )
            return {"records": records}

    @router.post("/devices/{device_id}/revoke")
    def revoke(
        device_id: str,
        device=Depends(require_device_management),
    ) -> dict:
        with get_conn() as conn:
            sync_auth.ensure_sync_auth_schema(conn)
            revoked = sync_auth.revoke_device(conn, device_id)
        if not revoked:
            raise HTTPException(status_code=404, detail="Device not found or already revoked")
        return {"revoked": device_id}

    return router
