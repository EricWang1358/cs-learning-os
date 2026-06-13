from __future__ import annotations

import os
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
os.environ["CS_LEARNING_CONTENT"] = str(ROOT / "content-demo")
os.environ["CS_LEARNING_DB"] = str(ROOT / "generated" / "smoke" / "knowledge.db")

from fastapi.testclient import TestClient

os.environ["CS_LEARNING_AI_PROVIDER"] = "openai-api"
os.environ["CS_LEARNING_AI_ENABLED"] = "true"
os.environ.pop("OPENAI_API_KEY", None)

from ingest import ingest
from api import app
from patch_policy import apply_patch_ops
import content_write_service


def wait_for_job(client: TestClient, job_id: int, terminal_statuses: set[str] | None = None) -> dict:
    terminal_statuses = terminal_statuses or {"draft_ready", "failed", "cancelled", "applied", "rejected"}
    last_payload: dict | None = None
    for _ in range(20):
        response = client.get(f"/api/ai/jobs/{job_id}")
        assert response.status_code == 200, response.text
        last_payload = response.json()["job"]
        if last_payload["status"] in terminal_statuses:
            return last_payload
    assert last_payload is not None
    return last_payload


def main() -> int:
    smoke_db_path = Path(os.environ["CS_LEARNING_DB"])
    if smoke_db_path.exists():
        smoke_db_path.unlink()
    ingest(Path(os.environ["CS_LEARNING_CONTENT"]), smoke_db_path)
    with sqlite3.connect(smoke_db_path) as conn:
        index_names = {
            row[0]
            for row in conn.execute(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'index'
                """
            )
        }
    assert {
        "idx_reader_questions_status_created",
        "idx_ai_jobs_status_updated",
        "idx_ai_job_events_job_id",
    }.issubset(index_names)
    client = TestClient(app, raise_server_exceptions=False)

    health = client.get("/api/health")
    assert health.status_code == 200, health.text
    assert health.json()["ok"] is True
    assert "ai" in health.json()

    metrics = client.get("/api/system/metrics")
    assert metrics.status_code == 200, metrics.text
    metrics_payload = metrics.json()
    assert metrics_payload["storage"]["project_related_bytes"] >= metrics_payload["storage"]["content_bytes"]
    assert metrics_payload["storage"]["github_repo_bytes"] >= 0
    assert metrics_payload["storage"]["github_repo_fallback_tracked_bytes"] >= 0
    assert "cache" in metrics_payload
    assert "refreshing" in metrics_payload["cache"]
    assert "schema" in metrics_payload
    assert metrics_payload["schema"]["schema_version"]["value"]
    assert "due_reviews" in metrics_payload["counts"]
    assert "collected_at" in metrics_payload
    partition_keys = {partition["key"] for partition in metrics_payload["storage"]["partitions"]}
    assert {"content", "sqlite-db", "generated"}.issubset(partition_keys)
    exclusive_keys = {partition["key"] for partition in metrics_payload["storage"]["exclusive_partitions"]}
    assert exclusive_keys
    if not metrics_payload["cache"]["refreshing"]:
        assert {"project-related", "github-upload", "git-tracked", "app-repo"}.issubset(partition_keys)
        assert any(key in exclusive_keys for key in {"repo-app", "repo-backend", "repo-content"})
    assert metrics_payload["storage"]["explained_project_bytes"] >= metrics_payload["storage"]["project_related_bytes"]
    assert metrics_payload["github"]["source"] in {"github-api", "git-tracked-fallback", "git-tracked-local", "pending-cache"}

    schema = client.get("/api/system/schema")
    assert schema.status_code == 200, schema.text
    assert schema.json()["schema"]["package_format_version"]["value"]

    repair = client.get("/api/system/repair")
    assert repair.status_code == 200, repair.text
    assert "issues" in repair.json()

    manifest = client.get("/api/package/export")
    assert manifest.status_code == 200, manifest.text
    manifest_payload = manifest.json()["manifest"]
    assert manifest_payload["package_format_version"] == "1"
    assert manifest_payload["counts"]["nodes"] >= 2
    assert any(item["path"].endswith(".md") for item in manifest_payload["files"])

    llmwiki = client.get("/api/llmwiki/export")
    assert llmwiki.status_code == 200, llmwiki.text
    llmwiki_payload = llmwiki.json()["pack"]
    assert llmwiki_payload["llmwiki_format_version"] == "1"
    assert llmwiki_payload["profile"] == "local-llmwiki-pack"
    assert llmwiki_payload["counts"]["items"] >= llmwiki_payload["counts"]["nodes"]
    assert llmwiki_payload["counts"]["markdown_files"] >= llmwiki_payload["counts"]["nodes"]
    assert llmwiki_payload["output"]["default_path"] == "generated/exports/llmwiki-pack.json"
    assert llmwiki_payload["usage"]["entrypoint"] == "System health > LLM Wiki pack > Export LLM Wiki pack"
    assert llmwiki_payload["memory_policy"]["includes_full_body"] is False
    assert llmwiki_payload["report"]["exported_items"] == llmwiki_payload["counts"]["items"]
    assert {"added", "updated", "skipped", "failed", "stale", "repaired"}.issubset(llmwiki_payload["report"])
    assert any(item["type"] == "node" and item["sha256"] for item in llmwiki_payload["items"])
    llmwiki_write = client.get("/api/llmwiki/export", params={"write": "true"})
    assert llmwiki_write.status_code == 200, llmwiki_write.text
    llmwiki_written_path = Path(llmwiki_write.json()["pack"]["written_to"])
    assert llmwiki_written_path.exists()
    assert llmwiki_written_path.name == "llmwiki-pack.json"

    preflight = client.get("/api/ai/preflight")
    assert preflight.status_code == 200, preflight.text
    assert "ok" in preflight.json()
    assert preflight.json()["enabled"] is True
    assert preflight.json()["ran_model"] is False

    os.environ["CS_LEARNING_AI_ENABLED"] = "false"
    disabled_preflight = client.get("/api/ai/preflight")
    assert disabled_preflight.status_code == 200, disabled_preflight.text
    assert disabled_preflight.json()["enabled"] is False
    blocked_job = client.post(
        "/api/ai/jobs",
        json={
            "target_type": "node",
            "target_id": "project-crud-app",
            "question_ids": [],
            "question": "Blocked smoke question.",
            "instruction": "This must not create a job.",
        },
    )
    assert blocked_job.status_code == 403, blocked_job.text
    os.environ["CS_LEARNING_AI_ENABLED"] = "true"

    nodes = client.get("/api/nodes")
    assert nodes.status_code == 200, nodes.text
    assert len(nodes.json()["nodes"]) >= 2

    areas = client.get("/api/areas")
    assert areas.status_code == 200, areas.text
    area_payload = areas.json()
    assert any(area["area"] == "cs-fundamentals" for area in area_payload["areas"])
    assert area_payload["system"]["all"] >= len(area_payload["areas"])

    graph_root = client.get("/api/graph")
    assert graph_root.status_code == 200, graph_root.text
    graph_root_payload = graph_root.json()
    assert graph_root_payload["center"]["type"] == "root"
    assert any(item["type"] == "area" for item in graph_root_payload["children"])

    graph_area = client.get("/api/graph/area/algorithms")
    assert graph_area.status_code == 200, graph_area.text
    assert graph_area.json()["center"]["type"] == "area"

    graph_track = client.get("/api/graph/track/algorithms/search-patterns")
    assert graph_track.status_code == 200, graph_track.text
    assert graph_track.json()["center"]["type"] == "track"
    assert graph_track.json()["pagination"]["page_size"] == 12

    graph_node = client.get("/api/graph/node/binary-search")
    assert graph_node.status_code == 200, graph_node.text
    graph_node_payload = graph_node.json()
    assert graph_node_payload["center"]["type"] == "node"
    assert any(item["type"] == "heading" for item in graph_node_payload["children"])
    assert any(action["kind"] == "focus_reading" for action in graph_node_payload["actions"])

    search = client.get("/api/search", params={"q": "binary"})
    assert search.status_code == 200, search.text
    assert any(node["slug"] == "binary-search" for node in search.json()["nodes"])

    alpha_nodes = client.get("/api/nodes", params={"sort": "alphabet"})
    assert alpha_nodes.status_code == 200, alpha_nodes.text
    alpha_titles = [node["title"] for node in alpha_nodes.json()["nodes"]]
    assert alpha_titles == sorted(alpha_titles, key=str.casefold)

    detail = client.get("/api/nodes/binary-search")
    assert detail.status_code == 200, detail.text
    payload = detail.json()["node"]
    assert payload["slug"] == "binary-search"
    assert "track" in payload
    assert "display_order" in payload
    assert "body" in payload
    assert "body_hash" in payload
    assert "tags" in payload
    assert "sources" in payload
    assert "open_question_count" in payload

    read_once = client.post(
        "/api/nodes/binary-search/read",
        json={"read_at": "2026-05-30T00:00:00+00:00", "min_interval_seconds": 60},
    )
    assert read_once.status_code == 200, read_once.text
    read_count_once = read_once.json()["node"]["read_count"]
    read_duplicate = client.post(
        "/api/nodes/binary-search/read",
        json={"read_at": "2026-05-30T00:00:30+00:00", "min_interval_seconds": 60},
    )
    assert read_duplicate.status_code == 200, read_duplicate.text
    assert read_duplicate.json()["node"]["read_count"] == read_count_once
    assert read_duplicate.json()["node"]["last_read_at"] == "2026-05-30T00:00:00+00:00"
    read_later = client.post(
        "/api/nodes/binary-search/read",
        json={"read_at": "2026-05-30T00:02:00+00:00", "min_interval_seconds": 60},
    )
    assert read_later.status_code == 200, read_later.text
    assert read_later.json()["node"]["read_count"] == read_count_once + 1
    read_backwards = client.post(
        "/api/nodes/binary-search/read",
        json={"read_at": "2026-05-29T00:00:00+00:00", "min_interval_seconds": 60},
    )
    assert read_backwards.status_code == 200, read_backwards.text
    assert read_backwards.json()["node"]["read_count"] == read_count_once + 1
    assert read_backwards.json()["node"]["last_read_at"] == "2026-05-30T00:02:00+00:00"

    last_read_nodes = client.get("/api/nodes", params={"sort": "last-read"})
    assert last_read_nodes.status_code == 200, last_read_nodes.text
    assert last_read_nodes.json()["nodes"][0]["slug"] == "binary-search"

    preserved_last_read_at = read_backwards.json()["node"]["last_read_at"]
    preserved_read_count = read_backwards.json()["node"]["read_count"]
    ingest(Path(os.environ["CS_LEARNING_CONTENT"]), Path(os.environ["CS_LEARNING_DB"]))
    detail_after_reingest = client.get("/api/nodes/binary-search")
    assert detail_after_reingest.status_code == 200, detail_after_reingest.text
    node_after_reingest = detail_after_reingest.json()["node"]
    assert node_after_reingest["last_read_at"] == preserved_last_read_at
    assert node_after_reingest["read_count"] == preserved_read_count
    last_read_after_reingest = client.get("/api/nodes", params={"sort": "last-read"})
    assert last_read_after_reingest.status_code == 200, last_read_after_reingest.text
    assert last_read_after_reingest.json()["nodes"][0]["slug"] == "binary-search"

    last_edit_search = client.get("/api/search", params={"q": "binary", "sort": "last-edit"})
    assert last_edit_search.status_code == 200, last_edit_search.text
    assert any(node["slug"] == "binary-search" for node in last_edit_search.json()["nodes"])

    reader_question = client.post(
        "/api/reader-questions",
        json={
            "target_type": "node",
            "target_id": "binary-search",
            "question": "What should be clarified here?",
        },
    )
    assert reader_question.status_code == 200, reader_question.text
    question_payload = reader_question.json()["question"]
    assert question_payload["status"] == "open"

    questions = client.get(
        "/api/reader-questions",
        params={"target_type": "node", "target_id": "binary-search"},
    )
    assert questions.status_code == 200, questions.text
    assert any(item["id"] == question_payload["id"] for item in questions.json()["questions"])

    resolve_question = client.post(
        f"/api/reader-questions/{question_payload['id']}/resolve",
        json={"resolution_note": "Smoke test cleanup"},
    )
    assert resolve_question.status_code == 200, resolve_question.text
    assert resolve_question.json()["question"]["status"] == "resolved"

    dismissed_question = client.post(
        "/api/reader-questions",
        json={
            "target_type": "node",
            "target_id": "binary-search",
            "question": "Dismiss me",
        },
    )
    assert dismissed_question.status_code == 200, dismissed_question.text
    dismissed_id = dismissed_question.json()["question"]["id"]
    dismiss = client.post(
        f"/api/reader-questions/{dismissed_id}/dismiss",
        json={"resolution_note": "Smoke test dismiss"},
    )
    assert dismiss.status_code == 200, dismiss.text
    assert dismiss.json()["question"]["status"] == "dismissed"

    deleted_question = client.post(
        "/api/reader-questions",
        json={
            "target_type": "node",
            "target_id": "binary-search",
            "question": "Delete me",
        },
    )
    assert deleted_question.status_code == 200, deleted_question.text
    deleted_id = deleted_question.json()["question"]["id"]
    delete = client.delete(f"/api/reader-questions/{deleted_id}")
    assert delete.status_code == 200, delete.text

    original_body = payload["body"]
    edited_body = original_body + "\n\n## Smoke Test Edit\nThis temporary edit verifies browser save support."
    update = client.put("/api/nodes/binary-search/body", json={"body": edited_body})
    assert update.status_code == 200, update.text
    assert "Smoke Test Edit" in update.json()["node"]["body"]
    restore = client.put("/api/nodes/binary-search/body", json={"body": original_body})
    assert restore.status_code == 200, restore.text
    assert restore.json()["node"]["body"] == original_body

    binary_path = Path(os.environ["CS_LEARNING_CONTENT"]) / payload["path"]
    original_file_text = binary_path.read_text(encoding="utf-8")
    stale_update = client.put(
        "/api/nodes/binary-search/body",
        json={
            "body": original_body + "\n\n## Stale Edit\nThis must be rejected.",
            "base_body_hash": "not-the-current-body-hash",
        },
    )
    assert stale_update.status_code == 409, stale_update.text
    assert binary_path.read_text(encoding="utf-8") == original_file_text

    original_update_node_body = content_write_service.update_node_body_in_conn

    def failing_update_node_body(*args, **kwargs):
        raise RuntimeError("simulated DB failure after file write")

    content_write_service.update_node_body_in_conn = failing_update_node_body
    try:
        failed_update = client.put(
            "/api/nodes/binary-search/body",
            json={"body": original_body + "\n\n## Should Roll Back\nThis must not remain."},
        )
        assert failed_update.status_code == 500, failed_update.text
    finally:
        content_write_service.update_node_body_in_conn = original_update_node_body
    assert binary_path.read_text(encoding="utf-8") == original_file_text

    created = client.post(
        "/api/nodes",
        json={
            "title": "Smoke Web Node",
            "area": "questions",
            "track": "general",
            "summary": "Temporary node created by backend smoke.",
            "tags": ["smoke", "web-create"],
            "visibility": "draft",
        },
    )
    assert created.status_code == 200, created.text
    created_node = created.json()["node"]
    created_slug = created_node["slug"]
    assert created_node["visibility"] == "draft"
    created_path = Path(os.environ["CS_LEARNING_CONTENT"]) / created_node["path"]
    assert created_path.exists()

    trashed = client.post(f"/api/nodes/{created_slug}/trash")
    assert trashed.status_code == 200, trashed.text
    assert trashed.json()["node"]["visibility"] == "trash"

    restored_node = client.post(f"/api/nodes/{created_slug}/restore")
    assert restored_node.status_code == 200, restored_node.text
    assert restored_node.json()["node"]["visibility"] == "draft"
    assert "previous_visibility" not in created_path.read_text(encoding="utf-8")

    trashed_again = client.post(f"/api/nodes/{created_slug}/trash")
    assert trashed_again.status_code == 200, trashed_again.text
    deleted_node = client.delete(f"/api/nodes/{created_slug}")
    assert deleted_node.status_code == 200, deleted_node.text
    assert not created_path.exists()

    tracks = client.get("/api/areas/cs-fundamentals/tracks")
    assert tracks.status_code == 200, tracks.text
    track_names = {track["track"] for track in tracks.json()["tracks"]}
    assert "x86-64-assembly" in track_names
    assert track_names

    quizzes = client.get("/api/quizzes")
    assert quizzes.status_code == 200, quizzes.text
    quiz_items = quizzes.json()["quizzes"]
    assert quiz_items
    selected_quiz_id = quiz_items[0]["id"]

    quiz_detail = client.get(f"/api/quizzes/{selected_quiz_id}")
    assert quiz_detail.status_code == 200, quiz_detail.text
    quiz_payload = quiz_detail.json()["quiz"]
    assert quiz_payload["id"] == selected_quiz_id
    assert "display_order" in quiz_payload
    assert "body" in quiz_payload
    assert "body_hash" in quiz_payload
    assert "linked_nodes" in quiz_payload
    assert isinstance(quiz_payload["linked_nodes"], list)

    fresh_reviews = client.get("/api/review/due")
    assert fresh_reviews.status_code == 200, fresh_reviews.text
    fresh_reviews_payload = fresh_reviews.json()
    assert "reviews" in fresh_reviews_payload
    assert fresh_reviews_payload["reviews"]
    fresh_review_item = fresh_reviews_payload["reviews"][0]
    assert fresh_review_item["target_type"] == "quiz"
    assert fresh_review_item["target_id"]
    assert fresh_review_item["title"]
    assert "summary" in fresh_review_item
    assert "interval_days" in fresh_review_item

    original_quiz_body = quiz_payload["body"]
    edited_quiz_body = original_quiz_body + "\n\n## Quiz Smoke Edit\nThis temporary edit verifies quiz save support."
    update_quiz = client.put(
        f"/api/quizzes/{selected_quiz_id}/body",
        json={"body": edited_quiz_body, "base_body_hash": quiz_payload["body_hash"]},
    )
    assert update_quiz.status_code == 200, update_quiz.text
    assert "Quiz Smoke Edit" in update_quiz.json()["quiz"]["body"]
    restore_quiz = client.put(f"/api/quizzes/{selected_quiz_id}/body", json={"body": original_quiz_body})
    assert restore_quiz.status_code == 200, restore_quiz.text
    assert restore_quiz.json()["quiz"]["body"] == original_quiz_body

    quiz_search_term = quiz_payload["title"].split()[0]
    quiz_search = client.get("/api/quiz-search", params={"q": quiz_search_term})
    assert quiz_search.status_code == 200, quiz_search.text
    assert any(
        quiz["id"] == selected_quiz_id
        for quiz in quiz_search.json()["quizzes"]
    )
    quiz_alpha = client.get("/api/quiz-search", params={"q": quiz_search_term, "sort": "alphabet"})
    assert quiz_alpha.status_code == 200, quiz_alpha.text
    assert any(
        quiz["id"] == selected_quiz_id
        for quiz in quiz_alpha.json()["quizzes"]
    )

    quiz_attempt = client.post(
        f"/api/quizzes/{selected_quiz_id}/attempts",
        json={"grade": "good", "elapsed_ms": 1200, "note": "Smoke review attempt"},
    )
    assert quiz_attempt.status_code == 200, quiz_attempt.text
    good_review = quiz_attempt.json()["review"]
    assert good_review["target_id"] == selected_quiz_id
    assert good_review["interval_days"] >= 2.5
    assert good_review["ease_factor"] == 2.5
    assert good_review["reps"] == 1

    repeat_attempt = client.post(
        f"/api/quizzes/{selected_quiz_id}/attempts",
        json={"grade": "again", "elapsed_ms": 800},
    )
    assert repeat_attempt.status_code == 200, repeat_attempt.text
    repeat_review = repeat_attempt.json()["review"]
    assert repeat_review["interval_days"] <= 0.02
    assert repeat_review["ease_factor"] < good_review["ease_factor"]
    assert repeat_review["reps"] == 0
    assert repeat_review["lapses"] == 1

    invalid_attempt = client.post(
        f"/api/quizzes/{selected_quiz_id}/attempts",
        json={"grade": "perfect", "elapsed_ms": 1},
    )
    assert invalid_attempt.status_code == 422, invalid_attempt.text
    due_reviews = client.get("/api/review/due")
    assert due_reviews.status_code == 200, due_reviews.text
    due_reviews_payload = due_reviews.json()
    assert "reviews" in due_reviews_payload

    ai_revision = client.post(
        "/api/ai/revise",
        json={
            "target_type": "node",
            "target_id": "binary-search",
            "question_ids": [],
            "instruction": "Clarify the loop invariant.",
        },
    )
    assert ai_revision.status_code in {200, 503}, ai_revision.text

    ai_job = client.post(
        "/api/ai/jobs",
        json={
            "target_type": "node",
            "target_id": "binary-search",
            "question": "Queued smoke test question",
            "instruction": "Clarify the midpoint.",
        },
    )
    assert ai_job.status_code == 200, ai_job.text
    job_payload = ai_job.json()["job"]
    assert job_payload["status"] in {"queued", "solving", "failed"}
    job_detail = client.get(f"/api/ai/jobs/{job_payload['id']}")
    assert job_detail.status_code == 200, job_detail.text
    job_detail_payload = job_detail.json()["job"]
    assert "error_summary" in job_detail_payload
    if job_detail_payload["status"] == "failed":
        retry_job = client.post(f"/api/ai/jobs/{job_payload['id']}/retry")
        assert retry_job.status_code == 200, retry_job.text
        retry_payload = retry_job.json()["job"]
        assert retry_payload["status"] in {"queued", "solving", "failed"}
        assert retry_payload["question_ids"] == job_payload["question_ids"]
    for question_id in job_payload["question_ids"]:
        cleanup = client.delete(f"/api/reader-questions/{question_id}")
        assert cleanup.status_code == 200, cleanup.text

    os.environ["CS_LEARNING_AI_PROVIDER"] = "codex-cli"
    os.environ["CS_LEARNING_CODEX_FAKE"] = "success"

    crud_detail = client.get("/api/nodes/project-crud-app")
    assert crud_detail.status_code == 200, crud_detail.text
    crud_original_body = crud_detail.json()["node"]["body"]

    crud_question = client.post(
        "/api/reader-questions",
        json={
            "target_type": "node",
            "target_id": "project-crud-app",
            "question": "Explain the CRUD loop in one concrete sentence.",
        },
    )
    assert crud_question.status_code == 200, crud_question.text
    crud_question_payload = crud_question.json()["question"]

    crud_job = client.post(
        "/api/ai/jobs",
        json={
            "target_type": "node",
            "target_id": "project-crud-app",
            "question_ids": [crud_question_payload["id"]],
            "instruction": "Use a minimal deterministic fake draft.",
        },
    )
    assert crud_job.status_code == 200, crud_job.text
    crud_job_payload = wait_for_job(client, crud_job.json()["job"]["id"])
    assert crud_job_payload["status"] == "draft_ready", crud_job_payload

    question_still_open = client.get(
        "/api/reader-questions",
        params={"target_type": "node", "target_id": "project-crud-app", "status": "open"},
    )
    assert question_still_open.status_code == 200, question_still_open.text
    assert any(item["id"] == crud_question_payload["id"] for item in question_still_open.json()["questions"])

    events = client.get(f"/api/ai/jobs/{crud_job_payload['id']}/events")
    assert events.status_code == 200, events.text
    assert any(item["stage"] == "draft_ready" for item in events.json()["events"])

    revised_body = crud_job_payload["revision"]["revised_body"]
    assert crud_job_payload["revision"]["patch_ops"][0]["op"] == "append_end"
    assert "AI Draft Smoke Note" in revised_body
    apply_job = client.post(f"/api/ai/jobs/{crud_job_payload['id']}/apply", json={"body": revised_body})
    assert apply_job.status_code == 200, apply_job.text
    assert apply_job.json()["job"]["status"] == "applied"

    resolved_question = client.get(
        "/api/reader-questions",
        params={"target_type": "node", "target_id": "project-crud-app", "status": "resolved"},
    )
    assert resolved_question.status_code == 200, resolved_question.text
    assert any(item["id"] == crud_question_payload["id"] for item in resolved_question.json()["questions"])

    restore_crud = client.put("/api/nodes/project-crud-app/body", json={"body": crud_original_body})
    assert restore_crud.status_code == 200, restore_crud.text

    reject_question = client.post(
        "/api/reader-questions",
        json={
            "target_type": "node",
            "target_id": "project-crud-app",
            "question": "Reject path smoke question.",
        },
    )
    assert reject_question.status_code == 200, reject_question.text
    reject_question_id = reject_question.json()["question"]["id"]
    reject_job = client.post(
        "/api/ai/jobs",
        json={
            "target_type": "node",
            "target_id": "project-crud-app",
            "question_ids": [reject_question_id],
            "instruction": "Fake reject path.",
        },
    )
    assert reject_job.status_code == 200, reject_job.text
    reject_job_payload = wait_for_job(client, reject_job.json()["job"]["id"])
    assert reject_job_payload["status"] == "draft_ready", reject_job_payload
    reject_job_id = reject_job_payload["id"]
    reject = client.post(f"/api/ai/jobs/{reject_job_id}/reject", json={"reason": "Smoke reject"})
    assert reject.status_code == 200, reject.text
    assert reject.json()["job"]["status"] == "rejected"
    reject_cleanup = client.delete(f"/api/reader-questions/{reject_question_id}")
    assert reject_cleanup.status_code == 200, reject_cleanup.text

    os.environ["CS_LEARNING_CODEX_FAKE"] = "non_json"
    failed_question = client.post(
        "/api/reader-questions",
        json={
            "target_type": "node",
            "target_id": "project-crud-app",
            "question": "Failure path smoke question.",
        },
    )
    assert failed_question.status_code == 200, failed_question.text
    failed_question_id = failed_question.json()["question"]["id"]
    failed_job = client.post(
        "/api/ai/jobs",
        json={
            "target_type": "node",
            "target_id": "project-crud-app",
            "question_ids": [failed_question_id],
            "instruction": "Fake failure path.",
        },
    )
    assert failed_job.status_code == 200, failed_job.text
    failed_job_payload = wait_for_job(client, failed_job.json()["job"]["id"])
    assert failed_job_payload["status"] == "failed"
    assert failed_job_payload["error_code"] == "non_json"
    failed_cleanup = client.delete(f"/api/reader-questions/{failed_question_id}")
    assert failed_cleanup.status_code == 200, failed_cleanup.text

    unsafe_patch_failed = False
    try:
        apply_patch_ops(
            "## 基础说明\n- 原始中文\n\n## Answer\n",
            [
                {
                    "op": "replace",
                    "section": "## 基础说明",
                    "find": "## 基础说明",
                    "replace": "## 基础说明\n- 原始中文\n  English: original Chinese.\n- 原始中文",
                }
            ],
        )
    except Exception:
        unsafe_patch_failed = True
    assert unsafe_patch_failed

    os.environ.pop("CS_LEARNING_CODEX_FAKE", None)
    os.environ["CS_LEARNING_AI_PROVIDER"] = "openai-api"

    print("API smoke test passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
