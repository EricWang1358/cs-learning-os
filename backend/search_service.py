from __future__ import annotations

import re
import sqlite3


NODE_SORT_OPTIONS = {"relevance", "last-edit", "last-read", "order", "alphabet"}
QUIZ_SORT_OPTIONS = {"relevance", "last-edit", "difficulty", "order", "alphabet"}


def safe_node_sort(value: str, default: str = "order") -> str:
    return value if value in NODE_SORT_OPTIONS else default


def safe_quiz_sort(value: str, default: str = "order") -> str:
    return value if value in QUIZ_SORT_OPTIONS else default


def node_sort_order(sort: str, include_rank: bool = False) -> str:
    if sort == "relevance" and include_rank:
        return "rank, n.title COLLATE NOCASE"
    if sort == "last-edit":
        return "n.updated_at DESC, n.title COLLATE NOCASE"
    if sort == "last-read":
        return "(COALESCE(a.last_read_at, '') = '') ASC, a.last_read_at DESC, n.title COLLATE NOCASE"
    if sort == "alphabet":
        return "n.title COLLATE NOCASE, n.area, n.track, n.display_order"
    return "n.area, n.track, n.display_order, n.title COLLATE NOCASE"


def quiz_difficulty_order(alias: str = "q") -> str:
    return (
        f"CASE LOWER({alias}.difficulty) "
        "WHEN 'easy' THEN 1 "
        "WHEN 'medium' THEN 2 "
        "WHEN 'hard' THEN 3 "
        "ELSE 4 END"
    )


def quiz_sort_order(sort: str, include_rank: bool = False) -> str:
    difficulty_order = quiz_difficulty_order("q")
    if sort == "relevance" and include_rank:
        return "rank, q.title COLLATE NOCASE"
    if sort == "last-edit":
        return "q.updated_at DESC, q.title COLLATE NOCASE"
    if sort == "difficulty":
        return f"{difficulty_order}, q.area, q.display_order, q.title COLLATE NOCASE"
    if sort == "alphabet":
        return "q.title COLLATE NOCASE, q.area, q.display_order"
    return f"q.area, q.display_order, {difficulty_order}, q.title COLLATE NOCASE"


def build_fts_query(term: str) -> str:
    tokens = re.findall(r"[\w-]+", term, flags=re.UNICODE)
    return " ".join(token.replace('"', "") for token in tokens)


def like_term(term: str) -> str:
    escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return f"%{escaped}%"


def list_nodes(
    conn: sqlite3.Connection,
    area: str | None = None,
    visibility: str | None = None,
    sort: str = "order",
) -> list[sqlite3.Row]:
    selected_sort = safe_node_sort(sort, "order")
    query = """
        SELECT n.*, COALESCE(a.last_read_at, '') AS last_read_at, COALESCE(a.read_count, 0) AS read_count
        FROM nodes n
        LEFT JOIN node_activity a ON a.node_slug = n.slug
        WHERE 1 = 1
    """
    params: list[str] = []
    if area:
        query += " AND n.area = ?"
        params.append(area)
    if visibility:
        query += " AND n.visibility = ?"
        params.append(visibility)
    query += f" ORDER BY {node_sort_order(selected_sort)}"
    return conn.execute(query, params).fetchall()


def search_nodes(conn: sqlite3.Connection, term: str, sort: str = "relevance") -> list[sqlite3.Row]:
    term = term.strip()
    selected_sort = safe_node_sort(sort, "relevance" if term else "order")
    if term:
        fts_query = build_fts_query(term)
        rows: list[sqlite3.Row] = []
        if fts_query:
            try:
                rows = conn.execute(
                    f"""
                    SELECT n.*, COALESCE(a.last_read_at, '') AS last_read_at, COALESCE(a.read_count, 0) AS read_count, bm25(node_fts) AS rank
                    FROM node_fts
                    JOIN nodes n ON n.slug = node_fts.slug
                    LEFT JOIN node_activity a ON a.node_slug = n.slug
                    WHERE node_fts MATCH ?
                      AND n.visibility != 'trash'
                    ORDER BY {node_sort_order(selected_sort, include_rank=True)}
                    LIMIT 50
                    """,
                    (fts_query,),
                ).fetchall()
            except sqlite3.OperationalError:
                rows = []
        if rows:
            return rows
        pattern = like_term(term)
        return conn.execute(
            f"""
            SELECT n.*, COALESCE(a.last_read_at, '') AS last_read_at, COALESCE(a.read_count, 0) AS read_count
            FROM nodes n
            LEFT JOIN node_activity a ON a.node_slug = n.slug
            WHERE n.visibility != 'trash'
              AND (
                n.title LIKE ? ESCAPE '\\'
                OR n.summary LIKE ? ESCAPE '\\'
                OR n.body LIKE ? ESCAPE '\\'
                OR EXISTS (
                  SELECT 1
                  FROM node_tags nt
                  WHERE nt.node_slug = n.slug
                    AND nt.tag_name LIKE ? ESCAPE '\\'
                )
              )
            ORDER BY {node_sort_order(selected_sort if selected_sort != 'relevance' else 'order')}
            LIMIT 50
            """,
            (pattern, pattern, pattern, pattern),
        ).fetchall()

    return conn.execute(
        f"""
        SELECT n.*, COALESCE(a.last_read_at, '') AS last_read_at, COALESCE(a.read_count, 0) AS read_count
        FROM nodes n
        LEFT JOIN node_activity a ON a.node_slug = n.slug
        WHERE n.visibility != 'trash'
        ORDER BY {node_sort_order(selected_sort)}
        LIMIT 50
        """
    ).fetchall()


def list_quizzes(
    conn: sqlite3.Connection,
    area: str | None = None,
    visibility: str | None = None,
    sort: str = "order",
) -> list[sqlite3.Row]:
    selected_sort = safe_quiz_sort(sort, "order")
    query = "SELECT q.* FROM quizzes q WHERE 1 = 1"
    params: list[str] = []
    if area:
        query += " AND q.area = ?"
        params.append(area)
    if visibility:
        query += " AND q.visibility = ?"
        params.append(visibility)
    query += f" ORDER BY {quiz_sort_order(selected_sort)}"
    return conn.execute(query, params).fetchall()


def search_quizzes(
    conn: sqlite3.Connection,
    term: str,
    sort: str = "relevance",
    area: str | None = None,
    visibility: str | None = None,
    difficulty: str | None = None,
) -> list[sqlite3.Row]:
    term = term.strip()
    selected_sort = safe_quiz_sort(sort, "relevance" if term else "order")
    filters = []
    params: list[str] = []
    if area:
        filters.append("q.area = ?")
        params.append(area)
    if visibility:
        filters.append("q.visibility = ?")
        params.append(visibility)
    if difficulty:
        filters.append("q.difficulty = ?")
        params.append(difficulty)
    filter_sql = f" AND {' AND '.join(filters)}" if filters else ""

    if term:
        fts_query = build_fts_query(term)
        rows: list[sqlite3.Row] = []
        if fts_query:
            try:
                rows = conn.execute(
                    f"""
                    SELECT q.*, bm25(quiz_fts) AS rank
                    FROM quiz_fts
                    JOIN quizzes q ON q.id = quiz_fts.id
                    WHERE quiz_fts MATCH ?
                    {filter_sql}
                    ORDER BY {quiz_sort_order(selected_sort, include_rank=True)}
                    LIMIT 50
                    """,
                    (fts_query, *params),
                ).fetchall()
            except sqlite3.OperationalError:
                rows = []
        if rows:
            return rows
        pattern = like_term(term)
        return conn.execute(
            f"""
            SELECT q.*
            FROM quizzes q
            WHERE (
                q.title LIKE ? ESCAPE '\\'
                OR q.summary LIKE ? ESCAPE '\\'
                OR q.body LIKE ? ESCAPE '\\'
                OR EXISTS (
                  SELECT 1
                  FROM quiz_tags qt
                  WHERE qt.quiz_id = q.id
                    AND qt.tag_name LIKE ? ESCAPE '\\'
                )
            )
            {filter_sql}
            ORDER BY {quiz_sort_order(selected_sort if selected_sort != 'relevance' else 'order')}
            LIMIT 50
            """,
            (pattern, pattern, pattern, pattern, *params),
        ).fetchall()

    return conn.execute(
        f"""
        SELECT q.*
        FROM quizzes q
        WHERE 1 = 1
        {filter_sql}
        ORDER BY {quiz_sort_order(selected_sort)}
        LIMIT 50
        """,
        params,
    ).fetchall()
