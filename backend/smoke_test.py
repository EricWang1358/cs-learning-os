from __future__ import annotations

from fastapi.testclient import TestClient

from api import app


def main() -> int:
    client = TestClient(app)

    health = client.get("/api/health")
    assert health.status_code == 200, health.text
    assert health.json()["ok"] is True

    nodes = client.get("/api/nodes")
    assert nodes.status_code == 200, nodes.text
    assert len(nodes.json()["nodes"]) >= 2

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

    print("API smoke test passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
