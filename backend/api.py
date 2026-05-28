from __future__ import annotations

import sqlite3
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

try:
    from .db import connect, initialize
except ImportError:
    from db import connect, initialize


ROOT = Path(__file__).resolve().parents[1]
DB_PATH = ROOT / "var" / "knowledge.db"

app = FastAPI(title="CS Learning OS API")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class ReaderQuestionCreate(BaseModel):
    target_type: str = Field(pattern="^(node|quiz)$")
    target_id: str = Field(min_length=1)
    question: str = Field(min_length=1)


class ReaderQuestionResolve(BaseModel):
    resolution_note: str = ""


class BodyUpdate(BaseModel):
    body: str


def get_conn() -> sqlite3.Connection:
    conn = connect(DB_PATH)
    initialize(conn)
    return conn


def row_to_node(row: sqlite3.Row) -> dict:
    return {
        "slug": row["slug"],
        "title": row["title"],
        "area": row["area"],
        "track": row["track"],
        "display_order": row["display_order"],
        "status": row["status"],
        "visibility": row["visibility"],
        "summary": row["summary"],
        "path": row["path"],
        "updated_at": row["updated_at"],
    }


def row_to_quiz(row: sqlite3.Row) -> dict:
    return {
        "id": row["id"],
        "title": row["title"],
        "area": row["area"],
        "status": row["status"],
        "visibility": row["visibility"],
        "difficulty": row["difficulty"],
        "summary": row["summary"],
        "path": row["path"],
        "weight": row["weight"],
        "updated_at": row["updated_at"],
    }


def row_to_reader_question(row: sqlite3.Row) -> dict:
    return {
        "id": row["id"],
        "target_type": row["target_type"],
        "target_id": row["target_id"],
        "question": row["question"],
        "status": row["status"],
        "created_at": row["created_at"],
        "resolved_at": row["resolved_at"],
        "resolution_note": row["resolution_note"],
    }


def slug_title(value: str) -> str:
    return " ".join(part.capitalize() for part in value.replace("_", "-").split("-"))


def split_markdown_frontmatter(text: str) -> tuple[str, str]:
    match = re.match(r"^(---\s*\n.*?\n---\s*\n)(.*)$", text, flags=re.DOTALL)
    if not match:
        return "", text
    return match.group(1), match.group(2)


def write_markdown_body(path: Path, body: str) -> None:
    text = path.read_text(encoding="utf-8")
    frontmatter, _ = split_markdown_frontmatter(text)
    normalized_body = body.strip() + "\n"
    path.write_text(frontmatter + normalized_body, encoding="utf-8")


def build_fts_query(term: str) -> str:
    tokens = re.findall(r"[\w-]+", term, flags=re.UNICODE)
    return " ".join(token.replace('"', "") for token in tokens)


def like_term(term: str) -> str:
    escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return f"%{escaped}%"


@app.get("/api/health")
def health() -> dict:
    return {"ok": True}


@app.get("/api/nodes")
def list_nodes(
    area: Optional[str] = None,
    visibility: Optional[str] = None,
) -> dict:
    query = "SELECT * FROM nodes WHERE 1 = 1"
    params: list[str] = []
    if area:
        query += " AND area = ?"
        params.append(area)
    if visibility:
        query += " AND visibility = ?"
        params.append(visibility)
    query += " ORDER BY area, track, display_order, title"

    with get_conn() as conn:
        rows = conn.execute(query, params).fetchall()
    return {"nodes": [row_to_node(row) for row in rows]}


@app.get("/api/nodes/{slug}")
def get_node(slug: str) -> dict:
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Node not found")
        tags = [
            item["tag_name"]
            for item in conn.execute(
                "SELECT tag_name FROM node_tags WHERE node_slug = ? ORDER BY tag_name",
                (slug,),
            ).fetchall()
        ]
        links = [
            {"target": item["target_slug"], "kind": item["kind"]}
            for item in conn.execute(
                "SELECT target_slug, kind FROM links WHERE source_slug = ? ORDER BY kind, target_slug",
                (slug,),
            ).fetchall()
        ]
        sources = [
            {
                "source": item["source"],
                "source_type": item["source_type"],
                "note": item["note"],
            }
            for item in conn.execute(
                "SELECT source, source_type, note FROM sources WHERE node_slug = ? ORDER BY id",
                (slug,),
            ).fetchall()
        ]
        open_question_count = conn.execute(
            """
            SELECT COUNT(*) AS count
            FROM reader_questions
            WHERE target_type = 'node'
              AND target_id = ?
              AND status = 'open'
            """,
            (slug,),
        ).fetchone()["count"]

    node = row_to_node(row)
    node["body"] = row["body"]
    node["tags"] = tags
    node["links"] = links
    node["sources"] = sources
    node["open_question_count"] = open_question_count
    return {"node": node}


@app.put("/api/nodes/{slug}/body")
def update_node_body(slug: str, payload: BodyUpdate) -> dict:
    body = payload.body.strip()
    if not body:
        raise HTTPException(status_code=400, detail="body cannot be empty")

    now = datetime.now(timezone.utc).isoformat()
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Node not found")

        content_path = ROOT / "content" / row["path"]
        if not content_path.is_file():
            raise HTTPException(status_code=404, detail="Node source file not found")

        write_markdown_body(content_path, body)

        tags = [
            item["tag_name"]
            for item in conn.execute(
                "SELECT tag_name FROM node_tags WHERE node_slug = ? ORDER BY tag_name",
                (slug,),
            ).fetchall()
        ]
        conn.execute(
            "UPDATE nodes SET body = ?, updated_at = ? WHERE slug = ?",
            (body, now, slug),
        )
        conn.execute("DELETE FROM node_fts WHERE slug = ?", (slug,))
        conn.execute(
            """
            INSERT INTO node_fts (slug, title, summary, body, tags)
            VALUES (?, ?, ?, ?, ?)
            """,
            (slug, row["title"], row["summary"], body, " ".join(tags)),
        )
        conn.commit()

    return get_node(slug)


@app.get("/api/search")
def search(q: str = Query(default="", min_length=0)) -> dict:
    term = q.strip()
    with get_conn() as conn:
        if term:
            fts_query = build_fts_query(term)
            rows = []
            if fts_query:
                try:
                    rows = conn.execute(
                        """
                        SELECT n.*, bm25(node_fts) AS rank
                        FROM node_fts
                        JOIN nodes n ON n.slug = node_fts.slug
                        WHERE node_fts MATCH ?
                        ORDER BY rank, n.title
                        LIMIT 50
                        """,
                        (fts_query,),
                    ).fetchall()
                except sqlite3.OperationalError:
                    rows = []
            if not rows:
                pattern = like_term(term)
                rows = conn.execute(
                    """
                    SELECT DISTINCT n.*
                    FROM nodes n
                    LEFT JOIN node_tags nt ON nt.node_slug = n.slug
                    WHERE n.title LIKE ? ESCAPE '\\'
                       OR n.summary LIKE ? ESCAPE '\\'
                       OR n.body LIKE ? ESCAPE '\\'
                       OR nt.tag_name LIKE ? ESCAPE '\\'
                    ORDER BY n.area, n.title
                    LIMIT 50
                    """,
                    (pattern, pattern, pattern, pattern),
                ).fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM nodes ORDER BY area, track, display_order, title LIMIT 50"
            ).fetchall()

    return {"nodes": [row_to_node(row) for row in rows]}


@app.get("/api/areas/{area}/tracks")
def list_area_tracks(area: str) -> dict:
    with get_conn() as conn:
        rows = conn.execute(
            """
            SELECT
                track,
                COUNT(*) AS node_count,
                MIN(display_order) AS first_order
            FROM nodes
            WHERE area = ?
              AND visibility != 'archive'
            GROUP BY track
            ORDER BY first_order, track
            """,
            (area,),
        ).fetchall()

    return {
        "area": area,
        "tracks": [
            {
                "track": row["track"],
                "label": slug_title(row["track"]),
                "node_count": row["node_count"],
                "first_order": row["first_order"],
            }
            for row in rows
        ],
    }


@app.get("/api/quizzes")
def list_quizzes(
    area: Optional[str] = None,
    visibility: Optional[str] = None,
) -> dict:
    query = "SELECT * FROM quizzes WHERE 1 = 1"
    params: list[str] = []
    if area:
        query += " AND area = ?"
        params.append(area)
    if visibility:
        query += " AND visibility = ?"
        params.append(visibility)
    query += " ORDER BY area, difficulty, title"

    with get_conn() as conn:
        rows = conn.execute(query, params).fetchall()
    return {"quizzes": [row_to_quiz(row) for row in rows]}


@app.get("/api/quizzes/{quiz_id}")
def get_quiz(quiz_id: str) -> dict:
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (quiz_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Quiz not found")
        tags = [
            item["tag_name"]
            for item in conn.execute(
                "SELECT tag_name FROM quiz_tags WHERE quiz_id = ? ORDER BY tag_name",
                (quiz_id,),
            ).fetchall()
        ]
        linked_nodes = [
            {"slug": item["node_slug"], "kind": item["kind"], "title": item["title"]}
            for item in conn.execute(
                """
                SELECT ql.node_slug, ql.kind, COALESCE(n.title, ql.node_slug) AS title
                FROM quiz_links ql
                LEFT JOIN nodes n ON n.slug = ql.node_slug
                WHERE ql.quiz_id = ?
                ORDER BY ql.kind, ql.node_slug
                """,
                (quiz_id,),
            ).fetchall()
        ]
        sources = [
            {
                "source": item["source"],
                "source_type": item["source_type"],
                "note": item["note"],
            }
            for item in conn.execute(
                "SELECT source, source_type, note FROM quiz_sources WHERE quiz_id = ? ORDER BY id",
                (quiz_id,),
            ).fetchall()
        ]
        open_question_count = conn.execute(
            """
            SELECT COUNT(*) AS count
            FROM reader_questions
            WHERE target_type = 'quiz'
              AND target_id = ?
              AND status = 'open'
            """,
            (quiz_id,),
        ).fetchone()["count"]

    quiz = row_to_quiz(row)
    quiz["body"] = row["body"]
    quiz["tags"] = tags
    quiz["linked_nodes"] = linked_nodes
    quiz["sources"] = sources
    quiz["open_question_count"] = open_question_count
    return {"quiz": quiz}


@app.put("/api/quizzes/{quiz_id}/body")
def update_quiz_body(quiz_id: str, payload: BodyUpdate) -> dict:
    body = payload.body.strip()
    if not body:
        raise HTTPException(status_code=400, detail="body cannot be empty")

    now = datetime.now(timezone.utc).isoformat()
    with get_conn() as conn:
        row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (quiz_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Quiz not found")

        content_path = ROOT / "content" / row["path"]
        if not content_path.is_file():
            raise HTTPException(status_code=404, detail="Quiz source file not found")

        write_markdown_body(content_path, body)

        tags = [
            item["tag_name"]
            for item in conn.execute(
                "SELECT tag_name FROM quiz_tags WHERE quiz_id = ? ORDER BY tag_name",
                (quiz_id,),
            ).fetchall()
        ]
        conn.execute(
            "UPDATE quizzes SET body = ?, updated_at = ? WHERE id = ?",
            (body, now, quiz_id),
        )
        conn.execute("DELETE FROM quiz_fts WHERE id = ?", (quiz_id,))
        conn.execute(
            """
            INSERT INTO quiz_fts (id, title, summary, body, tags)
            VALUES (?, ?, ?, ?, ?)
            """,
            (quiz_id, row["title"], row["summary"], body, " ".join(tags)),
        )
        conn.commit()

    return get_quiz(quiz_id)


@app.get("/api/quiz-search")
def search_quizzes(q: str = Query(default="", min_length=0)) -> dict:
    term = q.strip()
    with get_conn() as conn:
        if term:
            fts_query = build_fts_query(term)
            rows = []
            if fts_query:
                try:
                    rows = conn.execute(
                        """
                        SELECT q.*, bm25(quiz_fts) AS rank
                        FROM quiz_fts
                        JOIN quizzes q ON q.id = quiz_fts.id
                        WHERE quiz_fts MATCH ?
                        ORDER BY rank, q.title
                        LIMIT 50
                        """,
                        (fts_query,),
                    ).fetchall()
                except sqlite3.OperationalError:
                    rows = []
            if not rows:
                pattern = like_term(term)
                rows = conn.execute(
                    """
                    SELECT DISTINCT q.*
                    FROM quizzes q
                    LEFT JOIN quiz_tags qt ON qt.quiz_id = q.id
                    WHERE q.title LIKE ? ESCAPE '\\'
                       OR q.summary LIKE ? ESCAPE '\\'
                       OR q.body LIKE ? ESCAPE '\\'
                       OR qt.tag_name LIKE ? ESCAPE '\\'
                    ORDER BY q.area, q.title
                    LIMIT 50
                    """,
                    (pattern, pattern, pattern, pattern),
                ).fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM quizzes ORDER BY area, difficulty, title LIMIT 50"
            ).fetchall()

    return {"quizzes": [row_to_quiz(row) for row in rows]}


@app.get("/api/reader-questions")
def list_reader_questions(
    target_type: Optional[str] = None,
    target_id: Optional[str] = None,
    status: str = "open",
) -> dict:
    query = "SELECT * FROM reader_questions WHERE 1 = 1"
    params: list[str] = []
    if target_type:
        if target_type not in {"node", "quiz"}:
            raise HTTPException(status_code=400, detail="target_type must be node or quiz")
        query += " AND target_type = ?"
        params.append(target_type)
    if target_id:
        query += " AND target_id = ?"
        params.append(target_id)
    if status:
        query += " AND status = ?"
        params.append(status)
    query += " ORDER BY created_at DESC, id DESC"

    with get_conn() as conn:
        rows = conn.execute(query, params).fetchall()
    return {"questions": [row_to_reader_question(row) for row in rows]}


@app.post("/api/reader-questions")
def create_reader_question(payload: ReaderQuestionCreate) -> dict:
    question = payload.question.strip()
    if not question:
        raise HTTPException(status_code=400, detail="question cannot be empty")

    now = datetime.now(timezone.utc).isoformat()
    with get_conn() as conn:
        if payload.target_type == "node":
            exists = conn.execute(
                "SELECT 1 FROM nodes WHERE slug = ?", (payload.target_id,)
            ).fetchone()
        else:
            exists = conn.execute(
                "SELECT 1 FROM quizzes WHERE id = ?", (payload.target_id,)
            ).fetchone()
        if not exists:
            raise HTTPException(status_code=404, detail="target not found")

        cursor = conn.execute(
            """
            INSERT INTO reader_questions (target_type, target_id, question, status, created_at)
            VALUES (?, ?, ?, 'open', ?)
            """,
            (payload.target_type, payload.target_id, question, now),
        )
        conn.commit()
        row = conn.execute(
            "SELECT * FROM reader_questions WHERE id = ?", (cursor.lastrowid,)
        ).fetchone()

    return {"question": row_to_reader_question(row)}


@app.post("/api/reader-questions/{question_id}/resolve")
def resolve_reader_question(question_id: int, payload: ReaderQuestionResolve) -> dict:
    now = datetime.now(timezone.utc).isoformat()
    with get_conn() as conn:
        row = conn.execute(
            "SELECT * FROM reader_questions WHERE id = ?", (question_id,)
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="reader question not found")

        conn.execute(
            """
            UPDATE reader_questions
            SET status = 'resolved',
                resolved_at = ?,
                resolution_note = ?
            WHERE id = ?
            """,
            (now, payload.resolution_note, question_id),
        )
        conn.commit()
        updated = conn.execute(
            "SELECT * FROM reader_questions WHERE id = ?", (question_id,)
        ).fetchone()

    return {"question": row_to_reader_question(updated)}
