from __future__ import annotations

import hashlib
import json
import re
import sqlite3
from datetime import date, datetime, timezone
from typing import Optional


SECTION_HEADING_RE = re.compile(r"^##+\s+(.+?)\s*$", re.MULTILINE)
NUMBERED_ITEM_RE = re.compile(r"^\s*(\d+)[.)]\s+(.+?)(?=^\s*\d+[.)]\s+|\Z)", re.MULTILINE | re.DOTALL)
SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?])\s+")
MARKDOWN_PREFIX_RE = re.compile(r"^\s*(?:[-*]\s+|\d+[.)]\s+|>\s*)")
INLINE_CODE_RE = re.compile(r"`([^`]+)`")
CHOICE_LINE_RE = re.compile(r"^\s*(?:[-*]\s*)?(?:\[[ xX]\]\s*)?(?:[A-Ha-h][\).:-]\s+)?(.+?)\s*$")


def daily_bite(conn: sqlite3.Connection, day: Optional[str] = None) -> dict:
    selected_day = day or date.today().isoformat()
    card = _card_for_daily_seed(conn, selected_day)
    if card:
        return _payload_from_card(card)
    row = _row_for_daily_seed(conn, selected_day)
    return _payload_from_row(conn, row, selected_day)


def next_bite(conn: sqlite3.Connection, cursor: str = "") -> dict:
    if cursor.startswith("card:"):
        card = _card_after_cursor(conn, cursor.removeprefix("card:")) or _card_after_cursor(conn, "")
        if card:
            return _payload_from_card(card)
    row = _row_after_cursor(conn, cursor) or _row_after_cursor(conn, "")
    return _payload_from_row(conn, row, date.today().isoformat())


def list_bite_cards(conn: sqlite3.Connection, status: str = "active") -> list[dict]:
    rows = conn.execute(
        """
        SELECT *
        FROM bite_cards
        WHERE status = ?
        ORDER BY updated_at DESC, id DESC
        """,
        (status,),
    ).fetchall()
    return [_card_to_bite(row) for row in rows]


def get_bite_card(conn: sqlite3.Connection, card_id: int) -> dict:
    return _card_to_bite(_get_card_row(conn, card_id))


def create_bite_card(conn: sqlite3.Connection, payload) -> dict:
    now = _utc_now()
    cursor = conn.execute(
        """
        INSERT INTO bite_cards (
            source_type, source_id, title, area, difficulty, question_type, prompt,
            answer, options_json, hint, explanation_json, status, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', ?, ?)
        """,
        (
            payload.source_type,
            payload.source_id,
            payload.title.strip(),
            payload.area.strip(),
            payload.difficulty.strip(),
            _normalized_question_type(payload.question_type, payload.options),
            payload.prompt.strip(),
            payload.answer.strip(),
            json.dumps(_normalized_options(payload.options, payload.answer, payload.question_type)),
            payload.hint.strip(),
            json.dumps(_normalized_explanation(payload.explanation)),
            now,
            now,
        ),
    )
    conn.commit()
    return get_bite_card(conn, int(cursor.lastrowid))


def update_bite_card(conn: sqlite3.Connection, card_id: int, payload) -> dict:
    _get_card_row(conn, card_id)
    conn.execute(
        """
        UPDATE bite_cards
        SET title = ?,
            area = ?,
            difficulty = ?,
            question_type = ?,
            prompt = ?,
            answer = ?,
            options_json = ?,
            hint = ?,
            explanation_json = ?,
            status = ?,
            updated_at = ?
        WHERE id = ?
        """,
        (
            payload.title.strip(),
            payload.area.strip(),
            payload.difficulty.strip(),
            _normalized_question_type(payload.question_type, payload.options),
            payload.prompt.strip(),
            payload.answer.strip(),
            json.dumps(_normalized_options(payload.options, payload.answer, payload.question_type)),
            payload.hint.strip(),
            json.dumps(_normalized_explanation(payload.explanation)),
            payload.status,
            _utc_now(),
            card_id,
        ),
    )
    conn.commit()
    return get_bite_card(conn, card_id)


def archive_bite_card(conn: sqlite3.Connection, card_id: int) -> dict:
    _get_card_row(conn, card_id)
    conn.execute(
        "UPDATE bite_cards SET status = 'archive', updated_at = ? WHERE id = ?",
        (_utc_now(), card_id),
    )
    conn.commit()
    return get_bite_card(conn, card_id)


def _card_for_daily_seed(conn: sqlite3.Connection, selected_day: str) -> Optional[sqlite3.Row]:
    count = conn.execute(
        "SELECT COUNT(*) AS count FROM bite_cards WHERE status = 'active'"
    ).fetchone()["count"]
    if count <= 0:
        return None
    offset = _stable_index(f"bite-card:{selected_day}", count)
    return conn.execute(
        """
        SELECT *
        FROM bite_cards
        WHERE status = 'active'
        ORDER BY id
        LIMIT 1 OFFSET ?
        """,
        (offset,),
    ).fetchone()


def _card_after_cursor(conn: sqlite3.Connection, cursor: str) -> Optional[sqlite3.Row]:
    if not cursor:
        return conn.execute(
            """
            SELECT *
            FROM bite_cards
            WHERE status = 'active'
            ORDER BY id
            LIMIT 1
            """
        ).fetchone()
    try:
        card_id = int(cursor)
    except ValueError:
        return None
    return conn.execute(
        """
        SELECT *
        FROM bite_cards
        WHERE status = 'active'
          AND id > ?
        ORDER BY id
        LIMIT 1
        """,
        (card_id,),
    ).fetchone()


def _row_for_daily_seed(conn: sqlite3.Connection, selected_day: str) -> sqlite3.Row:
    count = conn.execute(
        "SELECT COUNT(*) AS count FROM quizzes WHERE visibility != 'trash'"
    ).fetchone()["count"]
    if count <= 0:
        raise ValueError("No quizzes are available for Daily Bite.")
    offset = _stable_index(selected_day, count)
    return conn.execute(
        """
        SELECT *
        FROM quizzes
        WHERE visibility != 'trash'
        ORDER BY display_order, id
        LIMIT 1 OFFSET ?
        """,
        (offset,),
    ).fetchone()


def _row_after_cursor(conn: sqlite3.Connection, cursor: str) -> Optional[sqlite3.Row]:
    if cursor.startswith("quiz:"):
        cursor = cursor.removeprefix("quiz:")
    if not cursor:
        return conn.execute(
            """
            SELECT *
            FROM quizzes
            WHERE visibility != 'trash'
            ORDER BY display_order, id
            LIMIT 1
            """
        ).fetchone()

    current = conn.execute(
        "SELECT display_order, id FROM quizzes WHERE id = ? AND visibility != 'trash'",
        (cursor,),
    ).fetchone()
    if not current:
        return None

    return conn.execute(
        """
        SELECT *
        FROM quizzes
        WHERE visibility != 'trash'
          AND (display_order > ? OR (display_order = ? AND id > ?))
        ORDER BY display_order, id
        LIMIT 1
        """,
        (current["display_order"], current["display_order"], current["id"]),
    ).fetchone()


def _payload_from_row(conn: sqlite3.Connection, row: sqlite3.Row, selected_day: str) -> dict:
    if row is None:
        raise ValueError("No quizzes are available for Daily Bite.")

    body = row["body"] or ""
    prompt_section = _section(body, "prompt") or row["summary"] or row["title"]
    answer_section = _section(body, "answer") or row["title"]
    explanation_section = _section(body, "explanation") or _section(body, "plain explanation") or row["summary"]
    hint_section = _section(body, "hint")
    choices_section = _section(body, "choices") or _section(body, "options")
    pair = _select_prompt_answer_pair(row["id"], selected_day, prompt_section, answer_section)
    answer = _one_line(pair["answer"] or answer_section or row["title"], fallback=row["title"])
    prompt = _fill_blank_prompt(pair["prompt"] or prompt_section or row["summary"], answer)
    explanation = _three_sentences(explanation_section, row["summary"], row["title"])
    linked_nodes = _linked_nodes(conn, row["id"])
    hint = _first_sentence(hint_section or row["summary"]) or _fallback_hint(row, linked_nodes)
    options = _choice_options(choices_section or pair["prompt"])
    question_type = "multiple_choice" if len(options) >= 2 else "blank"
    if question_type == "multiple_choice":
        prompt = _one_line(pair["prompt"] or prompt_section or row["summary"])

    bite = {
        "id": f"quiz:{row['id']}",
        "card_id": None,
        "source_type": "quiz",
        "source_id": row["id"],
        "title": row["title"],
        "area": row["area"],
        "difficulty": row["difficulty"],
        "question_type": question_type,
        "prompt": prompt,
        "answer": answer,
        "options": options,
        "hint": hint,
        "explanation": explanation,
        "summary": row["summary"],
        "linked_nodes": linked_nodes,
        "open_quiz_path": f"/quizzes/{row['id']}",
        "open_node_path": f"/nodes/{linked_nodes[0]['slug']}" if linked_nodes else "",
        "status": "generated",
        "created_at": "",
        "updated_at": row["updated_at"],
    }
    return {"bite": bite, "next_cursor": f"quiz:{row['id']}"}


def _payload_from_card(row: sqlite3.Row) -> dict:
    bite = _card_to_bite(row)
    return {"bite": bite, "next_cursor": bite["id"]}


def _card_to_bite(row: sqlite3.Row) -> dict:
    source_type = row["source_type"]
    source_id = row["source_id"]
    return {
        "id": f"card:{row['id']}",
        "card_id": row["id"],
        "source_type": source_type,
        "source_id": source_id,
        "title": row["title"],
        "area": row["area"],
        "difficulty": row["difficulty"],
        "question_type": row["question_type"] if "question_type" in row.keys() else "blank",
        "prompt": row["prompt"],
        "answer": row["answer"],
        "options": _decode_options(row["options_json"] if "options_json" in row.keys() else "[]"),
        "hint": row["hint"],
        "explanation": _decode_explanation(row["explanation_json"]),
        "summary": row["hint"] or row["prompt"],
        "linked_nodes": [],
        "open_quiz_path": f"/quizzes/{source_id}" if source_type == "quiz" else "",
        "open_node_path": f"/nodes/{source_id}" if source_type == "node" else "",
        "status": row["status"],
        "created_at": row["created_at"],
        "updated_at": row["updated_at"],
    }


def _get_card_row(conn: sqlite3.Connection, card_id: int) -> sqlite3.Row:
    row = conn.execute("SELECT * FROM bite_cards WHERE id = ?", (card_id,)).fetchone()
    if not row:
        raise ValueError("Daily Bite card not found.")
    return row


def _section(body: str, section_name: str) -> str:
    matches = list(SECTION_HEADING_RE.finditer(body))
    target = section_name.strip().lower()
    for index, match in enumerate(matches):
        heading = match.group(1).strip().lower()
        if heading != target:
            continue
        start = match.end()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(body)
        return body[start:end].strip()
    return ""


def _select_prompt_answer_pair(quiz_id: str, selected_day: str, prompt: str, answer: str) -> dict[str, str]:
    prompt_items = _numbered_items(prompt)
    answer_items = _numbered_items(answer)
    pairs = [
        {"prompt": prompt_items[key], "answer": answer_items[key]}
        for key in prompt_items.keys() & answer_items.keys()
    ]
    if not pairs:
        return {"prompt": _one_line(prompt), "answer": _one_line(answer)}
    return pairs[_stable_index(f"{selected_day}:{quiz_id}:pair", len(pairs))]


def _numbered_items(value: str) -> dict[str, str]:
    return {
        match.group(1): _clean_markdown_text(match.group(2))
        for match in NUMBERED_ITEM_RE.finditer(value.strip())
    }


def _fill_blank_prompt(prompt: str, answer: str) -> str:
    cleaned_prompt = _one_line(prompt)
    cleaned_answer = _one_line(answer)
    if "____" in cleaned_prompt or "___" in cleaned_prompt:
        return cleaned_prompt
    if "blank" in cleaned_prompt.lower():
        return cleaned_prompt

    for token in INLINE_CODE_RE.findall(cleaned_prompt):
        if token and token.lower() in cleaned_answer.lower():
            return cleaned_prompt.replace(f"`{token}`", "____", 1)

    return f"____ - {cleaned_prompt.rstrip('.?')}"


def _choice_options(value: str) -> list[str]:
    options: list[str] = []
    for raw_line in value.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        match = CHOICE_LINE_RE.match(line)
        if not match:
            continue
        option = _clean_markdown_text(match.group(1))
        if option:
            options.append(option)
    return _unique_options(options)[:8]


def _three_sentences(value: str, summary: str, title: str) -> list[str]:
    sentences = [_clean_markdown_text(item) for item in SENTENCE_SPLIT_RE.split(value or "") if _clean_markdown_text(item)]
    if not sentences:
        sentences = [_clean_markdown_text(summary)]
    while len(sentences) < 3:
        fallback = "Connect the answer back to the original quiz before moving on."
        if len(sentences) == 0:
            fallback = title
        elif len(sentences) == 1:
            fallback = "The full quiz keeps the longer Markdown explanation and examples."
        sentences.append(fallback)
    return sentences[:3]


def _linked_nodes(conn: sqlite3.Connection, quiz_id: str) -> list[dict]:
    return [
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


def _fallback_hint(row: sqlite3.Row, linked_nodes: list[dict]) -> str:
    if linked_nodes:
        return f"Think about {linked_nodes[0]['title']}."
    if row["area"]:
        return f"Anchor it in {row['area']}."
    return "Recall the smallest command, term, or invariant first."


def _one_line(value: str, fallback: str = "") -> str:
    for line in value.splitlines():
        cleaned = _clean_markdown_text(line)
        if cleaned:
            return cleaned
    return _clean_markdown_text(fallback)


def _first_sentence(value: str) -> str:
    cleaned = _clean_markdown_text(value)
    if not cleaned:
        return ""
    return SENTENCE_SPLIT_RE.split(cleaned)[0]


def _clean_markdown_text(value: str) -> str:
    text = MARKDOWN_PREFIX_RE.sub("", value.strip())
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def _normalized_explanation(value: list[str]) -> list[str]:
    items = [_clean_markdown_text(item) for item in value if _clean_markdown_text(item)]
    while len(items) < 3:
        items.append("Review the linked source for the full explanation.")
    return items[:3]


def _decode_explanation(value: str) -> list[str]:
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError:
        parsed = []
    if not isinstance(parsed, list):
        parsed = []
    return _normalized_explanation([str(item) for item in parsed])


def _normalized_question_type(question_type: str, options: list[str]) -> str:
    if question_type == "multiple_choice" and len(_unique_options(options)) >= 2:
        return "multiple_choice"
    return "blank"


def _normalized_options(options: list[str], answer: str, question_type: str) -> list[str]:
    if question_type != "multiple_choice":
        return []
    normalized = _unique_options([_clean_markdown_text(item) for item in options])
    cleaned_answer = _clean_markdown_text(answer)
    if cleaned_answer and cleaned_answer not in normalized:
        normalized.insert(0, cleaned_answer)
    return normalized[:8]


def _decode_options(value: str) -> list[str]:
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError:
        parsed = []
    if not isinstance(parsed, list):
        parsed = []
    return _unique_options([_clean_markdown_text(str(item)) for item in parsed])[:8]


def _unique_options(options: list[str]) -> list[str]:
    seen: set[str] = set()
    unique: list[str] = []
    for option in options:
        cleaned = option.strip()
        key = cleaned.lower()
        if not cleaned or key in seen:
            continue
        seen.add(key)
        unique.append(cleaned)
    return unique


def _stable_index(seed: str, count: int) -> int:
    digest = hashlib.sha256(seed.encode("utf-8")).hexdigest()
    return int(digest[:12], 16) % count


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()
