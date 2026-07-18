"""Regression tests for keeping KnowledgeGraph edges aligned with ingest."""

from backend.db import connect, initialize
from backend.ingest import prune_kg_references
from backend.kg_store import SCHEMA_KG_DDL


def test_ingest_rejects_active_edges_to_removed_nodes(tmp_path):
    db_path = tmp_path / "knowledge.db"
    conn = connect(db_path)
    initialize(conn)
    conn.executescript(SCHEMA_KG_DDL)
    conn.executemany(
        "INSERT INTO nodes (slug, title, area, track, status, visibility, summary, body, path, updated_at) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        [
            ("root", "Root", "test", "test", "seed", "core", "", "# Root", "nodes/test/root.md", "now"),
            ("child", "Child", "test", "test", "seed", "core", "", "# Child", "nodes/test/child.md", "now"),
        ],
    )
    conn.execute(
        "INSERT INTO kg_question (question_id, root_node_id, area_id, problem_no, title, category, created_at) "
        "VALUES (?, ?, ?, ?, ?, ?, ?)",
        ("q1", "root", "test", 1, "Root question", "CS_BASIC", 1),
    )
    conn.executemany(
        "INSERT INTO kg_edge (edge_id, parent_node_id, child_node_id, scope_type, status, created_at) "
        "VALUES (?, ?, ?, 'GLOBAL', 'ACTIVE', ?)",
        [("valid", "root", "child", 1), ("orphan", "missing-bundle", "root", 1)],
    )
    conn.commit()

    prune_kg_references(conn)

    rows = [tuple(row) for row in conn.execute(
        "SELECT edge_id, status FROM kg_edge ORDER BY edge_id"
    ).fetchall()]
    assert rows == [("orphan", "REJECTED"), ("valid", "ACTIVE")]
    assert conn.execute("SELECT status FROM kg_question WHERE question_id = 'q1'").fetchone()[0] == "ACTIVE"
    conn.close()
