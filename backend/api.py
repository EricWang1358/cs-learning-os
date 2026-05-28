from __future__ import annotations

import sqlite3
import re
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

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


def get_conn() -> sqlite3.Connection:
    conn = connect(DB_PATH)
    initialize(conn)
    return conn


def row_to_node(row: sqlite3.Row) -> dict:
    return {
        "slug": row["slug"],
        "title": row["title"],
        "area": row["area"],
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
    query += " ORDER BY area, title"

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

    node = row_to_node(row)
    node["body"] = row["body"]
    node["tags"] = tags
    node["links"] = links
    node["sources"] = sources
    return {"node": node}


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
                "SELECT * FROM nodes ORDER BY area, title LIMIT 50"
            ).fetchall()

    return {"nodes": [row_to_node(row) for row in rows]}


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

    quiz = row_to_quiz(row)
    quiz["body"] = row["body"]
    quiz["tags"] = tags
    quiz["linked_nodes"] = linked_nodes
    quiz["sources"] = sources
    return {"quiz": quiz}


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
