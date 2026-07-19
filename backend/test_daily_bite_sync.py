from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from backend import bite_service
from backend.bite_router import create_bite_router
from backend.db import connect, initialize
from backend.sync_router import create_sync_router


SCOPE_ALGORITHMS = {"areas": ["algorithms"], "includeDueReviews": False, "pinnedNodeIds": []}


def build_client(db_path: Path) -> TestClient:
    def get_conn():
        conn = connect(db_path)
        initialize(conn)
        return conn

    app = FastAPI()
    app.include_router(create_bite_router(get_conn))
    app.include_router(create_sync_router(get_conn, is_loopback=lambda request: True))
    return TestClient(app)


def auth_headers(client: TestClient) -> dict:
    token = client.post("/api/sync/v1/pairing-tokens").json()["token"]
    credential = client.post(
        "/api/sync/v1/pair",
        json={"token": token, "device_name": "android-emulator"},
    ).json()["credential"]
    return {"Authorization": f"Bearer {credential}"}


def seed_daily_bite_quiz(db_path: Path) -> None:
    with connect(db_path) as conn:
        initialize(conn)
        conn.execute(
            """
            INSERT INTO quizzes (id, title, area, status, visibility, difficulty, summary, body, path, updated_at)
            VALUES (
                'quiz-bite-1',
                'Daily Bite Sync Quiz',
                'algorithms',
                'seed',
                'practice',
                'easy',
                'Daily Bite sync smoke',
                '## Prompt
A ____ caches recent virtual-to-physical translations.

## Answer
TLB

## Hint
It sits near address translation.

## Explanation
The translation lookaside buffer avoids repeated page-table walks.
It is checked before walking page tables.
It makes virtual memory fast enough for normal execution.',
                'quizzes/algorithms/quiz-bite-1.md',
                'now'
            )
            """
        )
        conn.commit()


def test_extract_save_daily_bite_then_sync_pull_returns_phone_ready_card(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    seed_daily_bite_quiz(db_path)
    client = build_client(db_path)
    headers = auth_headers(client)

    saved = client.post("/api/bite/extract-and-save?source_type=quiz&source_id=quiz-bite-1")
    assert saved.status_code == 200, saved.text
    assert saved.json()["count"] == 1
    card_id = str(saved.json()["cards"][0]["card_id"])

    manifest = client.post(
        "/api/sync/v1/manifest",
        json={"cursor": 0, "serverId": "", "scope": SCOPE_ALGORITHMS},
        headers=headers,
    )
    assert manifest.status_code == 200, manifest.text
    changes = manifest.json()["changes"]
    assert any(change["type"] == "bite_card" and change["id"] == card_id for change in changes)
    bite_change = next(change for change in changes if change["type"] == "bite_card" and change["id"] == card_id)
    assert bite_change["area"] == "algorithms"

    pulled = client.post(
        "/api/sync/v1/pull",
        json={"entityType": "bite_card", "ids": [card_id], "scope": SCOPE_ALGORITHMS},
        headers=headers,
    )
    assert pulled.status_code == 200, pulled.text
    records = pulled.json()["records"]
    assert len(records) == 1
    assert records[0]["type"] == "bite_card"
    assert records[0]["title"] == "quiz-bite-1 #1"
    assert records[0]["prompt"] == "A ____ caches recent virtual-to-physical translations."
    assert records[0]["answer"] == "TLB"
    assert records[0]["area"] == "algorithms"
    assert records[0]["status"] == "active"


def test_archived_daily_bite_manifest_change_is_tombstone(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    client = build_client(db_path)
    headers = auth_headers(client)
    with connect(db_path) as conn:
        initialize(conn)
        card = bite_service.create_bite_card(
            conn,
            {
                "source_type": "quiz",
                "source_id": "quiz-bite-archive",
                "title": "Archive me",
                "area": "algorithms",
                "difficulty": "easy",
                "prompt": "Archive ____",
                "answer": "card",
                "hint": "",
                "explanation": [],
                "options": [],
                "question_type": "blank",
            },
        )
        bite_service.archive_bite_card(conn, card["card_id"])

    body = client.post(
        "/api/sync/v1/manifest",
        json={"cursor": 0, "serverId": "", "scope": SCOPE_ALGORITHMS},
        headers=headers,
    ).json()
    changes = [change for change in body["changes"] if change["type"] == "bite_card"]
    assert changes[-1]["id"] == str(card["card_id"])
    assert changes[-1]["tombstone"] is True


def test_legacy_delete_bite_card_uses_valid_archive_status(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    with connect(db_path) as conn:
        initialize(conn)
        card = bite_service.create_bite_card(
            conn,
            {
                "source_type": "quiz",
                "source_id": "quiz-bite-delete",
                "title": "Delete me",
                "area": "algorithms",
                "difficulty": "easy",
                "prompt": "Delete ____",
                "answer": "card",
                "hint": "",
                "explanation": [],
                "options": [],
                "question_type": "blank",
            },
        )

        assert bite_service.delete_bite_card(conn, card["card_id"]) is True
        row = conn.execute("SELECT status FROM bite_cards WHERE id = ?", (card["card_id"],)).fetchone()

    assert row["status"] == "archive"


def test_initialize_migrates_legacy_sync_changes_check_for_bite_cards(tmp_path: Path) -> None:
    db_path = tmp_path / "knowledge.db"
    with connect(db_path) as conn:
        conn.executescript(
            """
            CREATE TABLE sync_changes (
                seq INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_type TEXT NOT NULL CHECK(entity_type IN ('node', 'quiz', 'capture_slip', 'reader_question', 'review_attempt')),
                entity_id TEXT NOT NULL,
                revision INTEGER,
                content_hash TEXT,
                tombstone INTEGER NOT NULL DEFAULT 0,
                changed_at TEXT NOT NULL
            );
            INSERT INTO sync_changes (entity_type, entity_id, revision, content_hash, tombstone, changed_at)
            VALUES ('node', 'existing-node', 7, 'hash-old', 0, '2026-07-19T00:00:00+00:00');
            """
        )
        conn.commit()

        initialize(conn)
        card = bite_service.create_bite_card(
            conn,
            {
                "source_type": "quiz",
                "source_id": "quiz-bite-migration",
                "title": "Migration card",
                "area": "algorithms",
                "difficulty": "easy",
                "prompt": "Migration ____",
                "answer": "card",
                "hint": "",
                "explanation": [],
                "options": [],
                "question_type": "blank",
            },
        )
        rows = conn.execute("SELECT seq, entity_type, entity_id FROM sync_changes ORDER BY seq").fetchall()

    assert rows[0]["seq"] == 1
    assert rows[0]["entity_type"] == "node"
    assert rows[-1]["entity_type"] == "bite_card"
    assert rows[-1]["entity_id"] == str(card["card_id"])
