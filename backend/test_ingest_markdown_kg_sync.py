from pathlib import Path
from typing import Optional

from backend.ingest import ingest
from backend.kg_store import KgGraphStore


def write_node(root: Path, slug: str, title: str, prerequisites: Optional[list[str]] = None) -> None:
    values = ", ".join(prerequisites or [])
    frontmatter = [
        "---",
        f"slug: {slug}",
        f"title: {title}",
        "area: systems",
        "track: imported",
        "status: seed",
        "visibility: core",
        f"prerequisites: [{values}]",
        "---",
        "",
        f"# {title}",
        "",
        "A test node.",
        "",
    ]
    path = root / "nodes" / "systems" / f"{slug}.md"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(frontmatter), encoding="utf-8")


def test_ingest_materializes_markdown_prerequisites_for_kg_export(tmp_path: Path) -> None:
    content_root = tmp_path / "content"
    db_path = tmp_path / "knowledge.db"
    write_node(content_root, "wa8-root", "WA8 root", ["wa8-prerequisite"])
    write_node(content_root, "wa8-prerequisite", "WA8 prerequisite")

    ingest(content_root, db_path)

    store = KgGraphStore(str(db_path))
    payload = store.read(
        lambda cur: {
            "edge": cur.execute(
                """
                SELECT parent_node_id, child_node_id, scope_type, created_by, status
                FROM kg_edge
                WHERE parent_node_id = 'wa8-root'
                  AND child_node_id = 'wa8-prerequisite'
                """
            ).fetchone(),
        }
    )
    store.close()

    assert payload["edge"] is not None
    assert tuple(payload["edge"]) == (
        "wa8-root",
        "wa8-prerequisite",
        "GLOBAL",
        "IMPORT",
        "ACTIVE",
    )

    ingest(content_root, db_path)
    store = KgGraphStore(str(db_path))
    edge_count = store.read(
        lambda cur: cur.execute(
            """
            SELECT COUNT(*)
            FROM kg_edge
            WHERE parent_node_id = 'wa8-root'
              AND child_node_id = 'wa8-prerequisite'
              AND status = 'ACTIVE'
            """
        ).fetchone()[0]
    )
    store.close()
    assert edge_count == 1

    root_path = content_root / "nodes" / "systems" / "wa8-root.md"
    root_path.write_text(root_path.read_text(encoding="utf-8").replace(
        "prerequisites: [wa8-prerequisite]", "prerequisites: []"
    ), encoding="utf-8")
    ingest(content_root, db_path)
    store = KgGraphStore(str(db_path))
    removed_status = store.read(
        lambda cur: cur.execute(
            """
            SELECT status FROM kg_edge
            WHERE parent_node_id = 'wa8-root'
              AND child_node_id = 'wa8-prerequisite'
            """
        ).fetchone()[0]
    )
    store.close()
    assert removed_status == "REJECTED"

    root_path.write_text(root_path.read_text(encoding="utf-8").replace(
        "prerequisites: []", "prerequisites: [wa8-prerequisite]"
    ), encoding="utf-8")
    ingest(content_root, db_path)
    store = KgGraphStore(str(db_path))
    restored_status = store.read(
        lambda cur: cur.execute(
            """
            SELECT status FROM kg_edge
            WHERE parent_node_id = 'wa8-root'
              AND child_node_id = 'wa8-prerequisite'
            """
        ).fetchone()[0]
    )
    store.close()
    assert restored_status == "ACTIVE"
