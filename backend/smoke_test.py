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
    assert len(nodes.json()["nodes"]) >= 5

    search = client.get("/api/search", params={"q": "graph"})
    assert search.status_code == 200, search.text
    assert any(node["slug"] == "graph-traversal" for node in search.json()["nodes"])

    detail = client.get("/api/nodes/binary-search")
    assert detail.status_code == 200, detail.text
    payload = detail.json()["node"]
    assert payload["slug"] == "binary-search"
    assert "body" in payload
    assert "tags" in payload
    assert "sources" in payload

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
    assert any(link["slug"] == "gdb-disassemble" for link in quiz_payload["linked_nodes"])

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
