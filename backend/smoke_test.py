from __future__ import annotations

import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
os.environ["CS_LEARNING_CONTENT"] = str(ROOT / "content-demo")
os.environ["CS_LEARNING_DB"] = str(ROOT / "generated" / "smoke" / "knowledge.db")

from fastapi.testclient import TestClient

os.environ["CS_LEARNING_AI_PROVIDER"] = "openai-api"
os.environ.pop("OPENAI_API_KEY", None)

from ingest import ingest
from api import app
from patch_policy import apply_patch_ops


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
    ingest(Path(os.environ["CS_LEARNING_CONTENT"]), Path(os.environ["CS_LEARNING_DB"]))
    client = TestClient(app)

    health = client.get("/api/health")
    assert health.status_code == 200, health.text
    assert health.json()["ok"] is True
    assert "ai" in health.json()

    preflight = client.get("/api/ai/preflight")
    assert preflight.status_code == 200, preflight.text
    assert "ok" in preflight.json()
    assert preflight.json()["ran_model"] is False

    nodes = client.get("/api/nodes")
    assert nodes.status_code == 200, nodes.text
    assert len(nodes.json()["nodes"]) >= 2

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

    detail = client.get("/api/nodes/binary-search")
    assert detail.status_code == 200, detail.text
    payload = detail.json()["node"]
    assert payload["slug"] == "binary-search"
    assert "track" in payload
    assert "display_order" in payload
    assert "body" in payload
    assert "tags" in payload
    assert "sources" in payload
    assert "open_question_count" in payload

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

    tracks = client.get("/api/areas/cs-fundamentals/tracks")
    assert tracks.status_code == 200, tracks.text
    track_names = {track["track"] for track in tracks.json()["tracks"]}
    assert "x86-64-assembly" in track_names
    assert track_names

    quizzes = client.get("/api/quizzes")
    assert quizzes.status_code == 200, quizzes.text
    assert any(
        quiz["id"] == "x86-rax-trace-leaq-jump"
        for quiz in quizzes.json()["quizzes"]
    )

    quiz_detail = client.get("/api/quizzes/x86-rax-trace-leaq-jump")
    assert quiz_detail.status_code == 200, quiz_detail.text
    quiz_payload = quiz_detail.json()["quiz"]
    assert quiz_payload["id"] == "x86-rax-trace-leaq-jump"
    assert "body" in quiz_payload
    assert "linked_nodes" in quiz_payload
    linked_slugs = {link["slug"] for link in quiz_payload["linked_nodes"]}
    assert "x86-64-addressing-and-leaq" in linked_slugs

    quiz_search = client.get("/api/quiz-search", params={"q": "%rax"})
    assert quiz_search.status_code == 200, quiz_search.text
    assert any(
        quiz["id"] == "x86-rax-trace-leaq-jump"
        for quiz in quiz_search.json()["quizzes"]
    )

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
