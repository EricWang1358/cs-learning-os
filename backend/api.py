from __future__ import annotations

import logging
import os
import sqlite3
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

try:
    from . import ai_job_service
    from . import ai_revision_service
    from .asset_router import create_asset_router
    from .ai_router import create_ai_router
    from .bite_router import create_bite_router
    from .codex_service import (
        codex_base_url,
        codex_cli_path,
        codex_configuration_probe,
        codex_is_configured,
        codex_job_home,
        codex_model_name,
        codex_model_provider_name,
        codex_preflight,
    )
    from .db import connect, initialize
    from .graph_router import create_graph_router
    from .kg_router import create_kg_router
    from .kg_store import KgError, KgGraphStore
    from .node_router import create_node_router
    from .productization_router import create_productization_router
    from .quiz_router import create_quiz_router
    from .reader_question_router import create_reader_question_router
    from .runtime_config import ai_enabled, app_profile, beta_mode, sync_advertised_base_url
    from .system_metrics_service import SystemMetricsService
    from .system_router import create_system_router
    from .sync_router import create_sync_router
except ImportError:
    import ai_job_service
    import ai_revision_service
    from asset_router import create_asset_router
    from ai_router import create_ai_router
    from bite_router import create_bite_router
    from codex_service import (
        codex_base_url,
        codex_cli_path,
        codex_configuration_probe,
        codex_is_configured,
        codex_job_home,
        codex_model_name,
        codex_model_provider_name,
        codex_preflight,
    )
    from db import connect, initialize
    from graph_router import create_graph_router
    from kg_router import create_kg_router
    from kg_store import KgError, KgGraphStore
    from node_router import create_node_router
    from productization_router import create_productization_router
    from quiz_router import create_quiz_router
    from reader_question_router import create_reader_question_router
    from runtime_config import ai_enabled, app_profile, beta_mode, sync_advertised_base_url
    from system_metrics_service import SystemMetricsService
    from system_router import create_system_router
    from sync_router import create_sync_router


ROOT = Path(__file__).resolve().parents[1]
CONTENT_ROOT = Path(os.environ.get("CS_LEARNING_CONTENT", ROOT / "content-demo")).resolve()
CONTENT_ASSETS_ROOT = (CONTENT_ROOT / "assets").resolve()
DB_PATH = Path(os.environ.get("CS_LEARNING_DB", ROOT / "var" / "knowledge.db")).resolve()
EXPORT_ROOT = ROOT / "generated" / "exports"
EXPORT_ROOT = Path(os.environ.get("CS_LEARNING_EXPORT_ROOT", EXPORT_ROOT)).resolve()
GENERATED_ROOT = Path(os.environ.get("CS_LEARNING_GENERATED_ROOT", ROOT / "generated")).resolve()

logger = logging.getLogger("cs_learning.api")
app = FastAPI(title="CS Learning OS API")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:5177",
        "http://127.0.0.1:5177",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

ai_provider_name = ai_revision_service.ai_provider_name
openai_is_configured = ai_revision_service.openai_is_configured
openai_model_name = ai_revision_service.openai_model_name
metrics_service = SystemMetricsService(ROOT, CONTENT_ROOT, DB_PATH, logger, GENERATED_ROOT)


def get_conn() -> sqlite3.Connection:
    conn = connect(DB_PATH)
    initialize(conn)
    return conn


def recover_stale_ai_jobs() -> None:
    ai_job_service.recover_stale_ai_jobs(
        get_conn,
        int(os.environ.get("CS_LEARNING_STALE_JOB_SECONDS", "900")),
    )


app.include_router(create_asset_router(CONTENT_ASSETS_ROOT))
app.include_router(create_productization_router(get_conn, CONTENT_ROOT, EXPORT_ROOT))
app.include_router(
    create_system_router(
        get_conn,
        metrics_service,
        recover_stale_ai_jobs,
        ai_provider_name,
        openai_is_configured,
        openai_model_name,
        codex_is_configured,
        codex_model_name,
        codex_model_provider_name,
        codex_base_url,
        codex_cli_path,
        codex_job_home,
        codex_configuration_probe,
        codex_preflight,
        ai_enabled,
        app_profile,
        beta_mode,
    )
)
app.include_router(
    create_ai_router(
        get_conn,
        CONTENT_ROOT,
        logger,
        ai_provider_name,
        openai_model_name,
        codex_model_name,
        ai_enabled,
    )
)
app.include_router(create_node_router(get_conn, CONTENT_ROOT))
app.include_router(create_quiz_router(get_conn, CONTENT_ROOT))
app.include_router(create_bite_router(get_conn))
app.include_router(create_graph_router(get_conn))
app.include_router(create_reader_question_router(get_conn))

# KnowledgeGraph deep module (RFC-knowledge-graph): problem-rooted knowledge DAG
# under /api/kg. Own single-connection store over the shared DB file; kg node
# creation writes real markdown under CONTENT_ROOT so ingest keeps the rows.
kg_store = KgGraphStore(str(DB_PATH))
app.state.kg_store = kg_store


@app.exception_handler(KgError)
async def kg_error_handler(_request, exc: KgError):
    from fastapi.responses import JSONResponse

    return JSONResponse(status_code=exc.http_status,
                        content={"error": exc.kind, **exc.details})


app.include_router(create_kg_router(kg_store, CONTENT_ROOT))
app.include_router(
    create_sync_router(
        get_conn,
        export_root=EXPORT_ROOT,
        content_root=CONTENT_ROOT,
        advertised_base_url=sync_advertised_base_url(),
    )
)


@app.on_event("startup")
def on_startup() -> None:
    metrics_service.start_background_refresh()
