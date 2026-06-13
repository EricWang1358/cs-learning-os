from __future__ import annotations

from pathlib import Path

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse


def create_asset_router(content_assets_root: Path) -> APIRouter:
    assets_root = content_assets_root.resolve()
    router = APIRouter(tags=["assets"])

    @router.get("/content-assets/{asset_path:path}")
    def get_content_asset(asset_path: str):
        if not asset_path or "\x00" in asset_path:
            raise HTTPException(status_code=404, detail="Asset not found")

        asset = (assets_root / asset_path).resolve()
        try:
            asset.relative_to(assets_root)
        except ValueError:
            raise HTTPException(status_code=404, detail="Asset not found") from None

        if not asset.is_file():
            raise HTTPException(status_code=404, detail="Asset not found")

        return FileResponse(asset)

    return router
