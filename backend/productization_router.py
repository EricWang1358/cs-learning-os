from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from fastapi import APIRouter, Query

try:
    from . import maintenance_service
except ImportError:
    import maintenance_service


ConnectionFactory = Callable[[], sqlite3.Connection]


@dataclass(frozen=True)
class ProductizationRuntime:
    content_root: Path
    export_root: Path

    @property
    def package_manifest_path(self) -> Path:
        return self.export_root / "learning-package-manifest.json"

    @property
    def llmwiki_pack_path(self) -> Path:
        return self.export_root / "llmwiki-pack.json"


def create_productization_router(
    get_conn: ConnectionFactory,
    content_root: Path,
    export_root: Path,
) -> APIRouter:
    runtime = ProductizationRuntime(
        content_root=content_root.resolve(),
        export_root=export_root.resolve(),
    )
    router = APIRouter(prefix="/api", tags=["productization"])

    @router.get("/system/schema")
    def system_schema() -> dict:
        with get_conn() as conn:
            return {"schema": maintenance_service.schema_meta(conn)}

    @router.get("/system/repair")
    def system_repair_report() -> dict:
        with get_conn() as conn:
            return maintenance_service.repair_report(conn, runtime.content_root)

    @router.get("/package/export")
    def package_export_manifest(write: bool = Query(False)) -> dict:
        with get_conn() as conn:
            manifest = maintenance_service.content_manifest(conn, runtime.content_root)
        if write:
            maintenance_service.write_manifest(runtime.package_manifest_path, manifest)
            manifest["written_to"] = str(runtime.package_manifest_path)
        return {"manifest": manifest}

    @router.get("/llmwiki/export")
    def llmwiki_export(write: bool = Query(False)) -> dict:
        with get_conn() as conn:
            pack = maintenance_service.llmwiki_pack(conn, runtime.content_root)
        if write:
            maintenance_service.write_manifest(runtime.llmwiki_pack_path, pack)
            pack["written_to"] = str(runtime.llmwiki_pack_path)
        return {"pack": pack}

    return router
