"""Daily Bite card extraction and CRUD service."""

from __future__ import annotations

import json
import re
import sqlite3

try:
    from .sync_envelope import ENTITY_BITE_CARD, content_hash, record_change, utc_now
except ImportError:
    from sync_envelope import ENTITY_BITE_CARD, content_hash, record_change, utc_now


def list_sources(conn, area=""):
    """Return quizzes and nodes eligible for bite card extraction."""
    sources = []
    quiz_rows = conn.execute(
        "SELECT id, title, area, summary FROM quizzes"
        " WHERE deleted_at IS NULL AND visibility != 'trash'"
        + (" AND area = ?" if area else "")
        + " ORDER BY area, title",
        (area,) if area else (),
    ).fetchall()
    for row in quiz_rows:
        count = conn.execute(
            "SELECT COUNT(*) AS n FROM bite_cards WHERE source_id = ? AND status = 'active'",
            (row["id"],),
        ).fetchone()["n"]
        sources.append({
            "type": "quiz", "id": row["id"], "title": row["title"],
            "area": row["area"], "summary": row["summary"],
            "hasBiteCards": count > 0,
        })
    node_rows = conn.execute(
        "SELECT slug, title, area, summary FROM nodes"
        " WHERE visibility IN ('core','support')"
        + (" AND area = ?" if area else "")
        + " ORDER BY area, title",
        (area,) if area else (),
    ).fetchall()
    for row in node_rows:
        count = conn.execute(
            "SELECT COUNT(*) AS n FROM bite_cards WHERE source_id = ? AND status = 'active'",
            (row["slug"],),
        ).fetchone()["n"]
        sources.append({
            "type": "node", "id": row["slug"], "title": row["title"],
            "area": row["area"], "summary": row["summary"],
            "hasBiteCards": count > 0,
        })
    return sources


def list_bite_cards(conn, status="active"):
    rows = conn.execute(
        "SELECT * FROM bite_cards WHERE status = ? ORDER BY updated_at DESC, id DESC",
        (status,),
    ).fetchall()
    return [_card_dict(row) for row in rows]


def get_bite_card(conn, card_id):
    row = conn.execute(
        "SELECT * FROM bite_cards WHERE id = ?", (card_id,)
    ).fetchone()
    if not row:
        raise ValueError(f"bite card {card_id} not found")
    return _card_dict(row)


def archive_bite_card(conn, card_id):
    """Archive a card (called by DELETE /api/bites/{id})."""
    row = conn.execute("SELECT * FROM bite_cards WHERE id = ?", (card_id,)).fetchone()
    if not row:
        raise ValueError(f"bite card {card_id} not found")
    conn.execute("UPDATE bite_cards SET status='archive',updated_at=? WHERE id=?",
                 (utc_now(), card_id))
    record_change(
        conn,
        ENTITY_BITE_CARD,
        str(card_id),
        1,
        content_hash(row["prompt"] + row["answer"]),
        tombstone=True,
    )
    conn.commit()
    return _card_dict(conn.execute("SELECT * FROM bite_cards WHERE id = ?", (card_id,)).fetchone())


def _to_dict(data):
    """Accept either a dict or a Pydantic model."""
    if hasattr(data, "model_dump"):
        return data.model_dump()
    if hasattr(data, "dict"):
        return data.dict()
    return data


def create_bite_card(conn, data):
    data = _to_dict(data)
    now = utc_now()
    expl = json.dumps(data.get("explanation", []), ensure_ascii=False)
    opts = json.dumps(data.get("options", []), ensure_ascii=False)
    conn.execute(
        "INSERT INTO bite_cards (source_type,source_id,title,area,difficulty,"
        "prompt,answer,hint,explanation_json,status,question_type,options_json,"
        "created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,'active',?,?,?,?)",
        (data["source_type"], data["source_id"], data["title"],
         data.get("area", ""), data.get("difficulty", "medium"),
         data["prompt"], data["answer"], data.get("hint", ""), expl,
         data.get("question_type", "blank"), opts, now, now),
    )
    card_id = conn.execute("SELECT last_insert_rowid()").fetchone()[0]
    record_change(conn, ENTITY_BITE_CARD, str(card_id), 1,
                  content_hash(data["prompt"] + data["answer"]))
    conn.commit()
    return get_bite_card(conn, card_id)


def update_bite_card(conn, card_id, data):
    data = _to_dict(data)
    existing = conn.execute(
        "SELECT * FROM bite_cards WHERE id = ?", (card_id,)
    ).fetchone()
    if not existing:
        return None
    expl = json.dumps(data.get("explanation", []), ensure_ascii=False)
    opts = json.dumps(data.get("options", []), ensure_ascii=False)
    conn.execute(
        "UPDATE bite_cards SET title=?,area=?,difficulty=?,prompt=?,answer=?,"
        "hint=?,explanation_json=?,question_type=?,options_json=?,status=?,"
        "updated_at=? WHERE id=?",
        (data.get("title", existing["title"]),
         data.get("area", existing["area"]),
         data.get("difficulty", existing["difficulty"]),
         data.get("prompt", existing["prompt"]),
         data.get("answer", existing["answer"]),
         data.get("hint", existing["hint"]), expl,
         data.get("question_type", existing["question_type"]), opts,
         data.get("status", existing["status"]), utc_now(), card_id),
    )
    record_change(conn, ENTITY_BITE_CARD, str(card_id), 1,
                  content_hash(data.get("prompt", existing["prompt"])
                               + data.get("answer", existing["answer"])))
    conn.commit()
    return get_bite_card(conn, card_id)


def delete_bite_card(conn, card_id):
    existing = conn.execute(
        "SELECT * FROM bite_cards WHERE id = ?", (card_id,)
    ).fetchone()
    if not existing:
        return False
    conn.execute(
        "UPDATE bite_cards SET status='archive',updated_at=? WHERE id=?",
        (utc_now(), card_id),
    )
    record_change(
        conn,
        ENTITY_BITE_CARD,
        str(card_id),
        1,
        content_hash(existing["prompt"] + existing["answer"]),
        tombstone=True,
    )
    conn.commit()
    return True


def extract_from_source(conn, source_type, source_id):
    if source_type == "quiz":
        row = conn.execute(
            "SELECT id, title, area, body FROM quizzes WHERE id = ?",
            (source_id,),
        ).fetchone()
        if not row:
            return []
        return _extract_quiz(row["body"], source_id, row["area"])
    return []


def _extract_quiz(body, source_id, area):
    sections = re.split(r"^## ", body, flags=re.MULTILINE)
    prompts, answers, hints, explanations = [], [], [], []
    cur_section = None
    cur_content = []

    for section in sections:
        if not section.strip():
            continue
        parts = section.split("\n", 1)
        heading = parts[0].strip().lower()
        content = parts[1].strip() if len(parts) > 1 else ""

        if cur_section == "prompt":
            prompts.append(cur_content[0] if cur_content else "")
        elif cur_section == "answer":
            answers.append(cur_content[0] if cur_content else "")
        elif cur_section == "hint":
            hints.append(cur_content[0] if cur_content else "")
        elif cur_section == "explanation":
            explanations.append([l for l in cur_content if l.strip()])

        if heading.startswith("prompt"):
            cur_section = "prompt"
            cur_content = [content]
        elif heading.startswith("answer"):
            cur_section = "answer"
            cur_content = [content]
        elif heading.startswith("hint"):
            cur_section = "hint"
            cur_content = [content]
        elif heading.startswith("explanation"):
            cur_section = "explanation"
            cur_content = content.split("\n")
        else:
            cur_section = None
            cur_content = []

    if cur_section == "prompt":
        prompts.append(cur_content[0] if cur_content else "")
    elif cur_section == "answer":
        answers.append(cur_content[0] if cur_content else "")
    elif cur_section == "hint":
        hints.append(cur_content[0] if cur_content else "")
    elif cur_section == "explanation":
        explanations.append([l for l in cur_content if l.strip()])

    cards = []
    for i in range(min(len(prompts), len(answers))):
        p = _clean_text(prompts[i])
        a = _clean_text(answers[i])
        if not p or not a:
            continue
        h = hints[i] if i < len(hints) else ""
        e = explanations[i] if i < len(explanations) else []
        cards.append({
            "source_type": "quiz", "source_id": source_id,
            "title": f"{source_id} #{i+1}", "area": area,
            "difficulty": "medium", "question_type": "blank",
            "prompt": p, "answer": a, "hint": h,
            "explanation": e[:3], "options": [],
        })
    return cards


def _clean_text(text):
    text = text.strip()
    lines = text.split("\n")
    cleaned = []
    for line in lines:
        line = line.strip()
        if not line:
            continue
        if line.lower().startswith(("english:", "chinese:", "中文：")):
            cleaned.append(line.split(":", 1)[1].strip() if ":" in line else line)
        else:
            cleaned.append(line)
    return "\n".join(cleaned).strip()


def daily_bite(conn, day=None):
    """Return today's Daily Bite card (deterministic by date)."""
    from datetime import date
    selected = day or date.today().isoformat()
    rows = conn.execute(
        "SELECT * FROM bite_cards WHERE status = 'active'"
        " ORDER BY id LIMIT 1"
    ).fetchall()
    if not rows:
        raise ValueError("no active bite cards")
    # Deterministic pick: hash date to pick a card index
    import hashlib
    idx = int(hashlib.md5(selected.encode()).hexdigest(), 16) % len(rows)
    return _card_dict(rows[idx])


def next_bite(conn, cursor=""):
    """Return the next bite card after cursor."""
    rows = conn.execute(
        "SELECT * FROM bite_cards WHERE status = 'active'"
        " ORDER BY id"
    ).fetchall()
    if not rows:
        raise ValueError("no active bite cards")
    if not cursor:
        return _card_dict(rows[0])
    for i, row in enumerate(rows):
        if str(row["id"]) == cursor and i + 1 < len(rows):
            return _card_dict(rows[i + 1])
    return _card_dict(rows[0])


def _card_dict(row):
    return {
        "id": row["id"], "card_id": row["id"], "sourceType": row["source_type"],
        "sourceId": row["source_id"], "title": row["title"],
        "area": row["area"], "difficulty": row["difficulty"],
        "prompt": row["prompt"], "answer": row["answer"],
        "hint": row["hint"],
        "explanation": json.loads(row["explanation_json"] or "[]"),
        "questionType": row["question_type"],
        "options": json.loads(row["options_json"] or "[]"),
        "status": row["status"],
        "createdAt": row["created_at"], "updatedAt": row["updated_at"],
    }
