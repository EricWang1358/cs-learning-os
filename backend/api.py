from __future__ import annotations

import sqlite3
import re
import os
import json
import logging
import hashlib
import shutil
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

try:
    import tomllib
except ModuleNotFoundError:  # Python < 3.11
    try:
        import tomli as tomllib
    except ModuleNotFoundError:
        tomllib = None

from fastapi import BackgroundTasks, FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

try:
    from .db import connect, initialize
except ImportError:
    from db import connect, initialize


ROOT = Path(__file__).resolve().parents[1]
CONTENT_ROOT = Path(os.environ.get("CS_LEARNING_CONTENT", ROOT / "content-demo")).resolve()
DB_PATH = Path(os.environ.get("CS_LEARNING_DB", ROOT / "var" / "knowledge.db")).resolve()
logger = logging.getLogger("cs_learning.api")
DEFAULT_CODEX_CLI = Path(r"D:\Program Files\nodejs\node_global\codex.cmd")
DEFAULT_CODEX_HOME = Path.home() / ".codex"

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


class AiReviseRequest(BaseModel):
    target_type: str = Field(pattern="^(node|quiz)$")
    target_id: str = Field(min_length=1)
    question_ids: list[int] = []
    instruction: str = ""
    draft_body: str = ""


class AiJobCreate(BaseModel):
    target_type: str = Field(pattern="^(node|quiz)$")
    target_id: str = Field(min_length=1)
    question_ids: list[int] = []
    question: str = ""
    instruction: str = ""
    draft_body: str = ""


class AiJobApply(BaseModel):
    body: str = Field(min_length=1)


class AiJobReject(BaseModel):
    reason: str = ""


def apply_patch_ops(original_body: str, patch_ops: list[dict]) -> str:
    body = original_body
    for index, op in enumerate(patch_ops, start=1):
        action = op.get("op")
        find_text = str(op.get("find") or "")
        replace_text = str(op.get("replace") or "")
        if action not in {"replace", "append_after", "append_end"}:
            raise HTTPException(status_code=502, detail=f"AI patch op #{index} has unsupported op: {action}")
        if action == "append_end":
            body = f"{body.rstrip()}\n\n{replace_text.strip()}\n"
            continue
        if not find_text:
            raise HTTPException(status_code=502, detail=f"AI patch op #{index} is missing find text")
        occurrences = body.count(find_text)
        if occurrences != 1:
            raise HTTPException(
                status_code=502,
                detail=f"AI patch op #{index} expected one match, found {occurrences}",
            )
        if action == "replace":
            body = body.replace(find_text, replace_text, 1)
        else:
            body = body.replace(find_text, f"{find_text.rstrip()}\n\n{replace_text.strip()}", 1)
    return body.strip()


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


def summarize_ai_error(error: str | None) -> str:
    if not error:
        return ""

    text = error.strip()
    lower_text = text.lower()
    if "high demand" in lower_text:
        return (
            "Codex CLI failed because the model provider reported high demand. "
            "The job is safe to retry; it did not change your Markdown."
        )
    if "timed out" in lower_text:
        return "Codex CLI timed out before returning a draft. The job is safe to retry."
    if "openai_api_key" in lower_text or "api key" in lower_text:
        return "AI provider is not configured with the required API key."
    if "returned non-json" in lower_text:
        return "Codex CLI returned output that was not valid JSON, so the draft could not be parsed."

    for marker in ["\n--------\nuser", "\nuser\n", "Original Markdown body:"]:
        marker_index = text.find(marker)
        if marker_index != -1:
            text = text[:marker_index].strip()

    lines = []
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        if "WARN codex_core" in stripped or "ERROR: Reconnecting" in stripped:
            continue
        lines.append(stripped)
        if len(" ".join(lines)) > 700:
            break

    summary = " ".join(lines).strip()
    return summary[:900] + ("..." if len(summary) > 900 else "")


def classify_ai_error(error: str | None) -> str:
    text = (error or "").lower()
    if "high demand" in text:
        return "high_demand"
    if "timed out" in text:
        return "timeout"
    if "non-json" in text or "not valid json" in text:
        return "non_json"
    if "api key" in text or "unauthorized" in text or "not configured" in text:
        return "auth_or_config"
    if "cancel" in text:
        return "cancelled"
    return "unknown"


def row_to_ai_job(row: sqlite3.Row) -> dict:
    result = json.loads(row["result_json"] or "{}")
    error = row["error"] or ""
    return {
        "id": row["id"],
        "target_type": row["target_type"],
        "target_id": row["target_id"],
        "question_ids": json.loads(row["question_ids"] or "[]"),
        "provider": row["provider"],
        "model": row["model"],
        "status": row["status"],
        "stage": row["stage"],
        "instruction": row["instruction"],
        "error": error,
        "error_summary": summarize_ai_error(error),
        "error_code": row["error_code"],
        "retry_of": row["retry_of"],
        "attempt": row["attempt"],
        "base_body_hash": row["base_body_hash"],
        "created_at": row["created_at"],
        "updated_at": row["updated_at"],
        "completed_at": row["completed_at"],
        "started_at": row["started_at"],
        "revision": result.get("revision"),
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


def body_hash(body: str) -> str:
    return hashlib.sha256(body.strip().encode("utf-8")).hexdigest()


def build_fts_query(term: str) -> str:
    tokens = re.findall(r"[\w-]+", term, flags=re.UNICODE)
    return " ".join(token.replace('"', "") for token in tokens)


def update_node_body_in_conn(conn: sqlite3.Connection, row: sqlite3.Row, body: str) -> None:
    now = datetime.now(timezone.utc).isoformat()
    slug = row["slug"]
    tags = [
        item["tag_name"]
        for item in conn.execute(
            "SELECT tag_name FROM node_tags WHERE node_slug = ? ORDER BY tag_name",
            (slug,),
        ).fetchall()
    ]
    conn.execute("UPDATE nodes SET body = ?, updated_at = ? WHERE slug = ?", (body, now, slug))
    conn.execute("DELETE FROM node_fts WHERE slug = ?", (slug,))
    conn.execute(
        """
        INSERT INTO node_fts (slug, title, summary, body, tags)
        VALUES (?, ?, ?, ?, ?)
        """,
        (slug, row["title"], row["summary"], body, " ".join(tags)),
    )


def update_quiz_body_in_conn(conn: sqlite3.Connection, row: sqlite3.Row, body: str) -> None:
    now = datetime.now(timezone.utc).isoformat()
    quiz_id = row["id"]
    tags = [
        item["tag_name"]
        for item in conn.execute(
            "SELECT tag_name FROM quiz_tags WHERE quiz_id = ? ORDER BY tag_name",
            (quiz_id,),
        ).fetchall()
    ]
    conn.execute("UPDATE quizzes SET body = ?, updated_at = ? WHERE id = ?", (body, now, quiz_id))
    conn.execute("DELETE FROM quiz_fts WHERE id = ?", (quiz_id,))
    conn.execute(
        """
        INSERT INTO quiz_fts (id, title, summary, body, tags)
        VALUES (?, ?, ?, ?, ?)
        """,
        (quiz_id, row["title"], row["summary"], body, " ".join(tags)),
    )


def like_term(term: str) -> str:
    escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return f"%{escaped}%"


def openai_model_name() -> str:
    return os.environ.get("CS_LEARNING_OPENAI_MODEL", "gpt-5.4-mini")


def codex_model_name() -> str:
    return os.environ.get("CS_LEARNING_CODEX_MODEL", "gpt-5.4-mini")


def codex_source_home() -> Path:
    return Path(os.environ.get("CS_LEARNING_CODEX_SOURCE_HOME", DEFAULT_CODEX_HOME)).resolve()


def load_codex_source_config() -> dict:
    config_path = codex_source_home() / "config.toml"
    if not config_path.is_file():
        return {}
    if tomllib is None:
        logger.warning("Python tomllib is unavailable; set CS_LEARNING_CODEX_BASE_URL explicitly for dynamic Codex config.")
        return {}
    try:
        with config_path.open("rb") as config_file:
            return tomllib.load(config_file)
    except Exception as exc:
        logger.warning("Could not read Codex source config %s: %s", config_path, exc)
        return {}


def codex_model_provider_name() -> str:
    return os.environ.get("CS_LEARNING_CODEX_MODEL_PROVIDER") or load_codex_source_config().get("model_provider", "OpenAI")


def codex_base_url() -> str:
    configured = os.environ.get("CS_LEARNING_CODEX_BASE_URL")
    if configured:
        return configured.strip()
    config = load_codex_source_config()
    provider_name = codex_model_provider_name()
    provider = config.get("model_providers", {}).get(provider_name, {})
    return str(provider.get("base_url") or "https://api.openai.com/v1").strip()


def codex_auth_mode() -> str:
    configured = os.environ.get("CS_LEARNING_CODEX_AUTH_MODE")
    if configured:
        return configured.strip()
    config = load_codex_source_config()
    provider_name = codex_model_provider_name()
    provider = config.get("model_providers", {}).get(provider_name, {})
    return "openai" if provider.get("requires_openai_auth", True) else "none"


def codex_auth_file() -> Path:
    return Path(os.environ.get("CS_LEARNING_CODEX_AUTH_FILE", codex_source_home() / "auth.json")).resolve()


def codex_job_home() -> Path:
    return Path(os.environ.get("CS_LEARNING_CODEX_HOME", ROOT / "generated" / "codex-home")).resolve()


def shell_quote_toml(value: str) -> str:
    return json.dumps(value)


def ensure_codex_job_home() -> Path:
    home = codex_job_home()
    home.mkdir(parents=True, exist_ok=True)
    auth_file = codex_auth_file()
    if auth_file.is_file():
        shutil.copy2(auth_file, home / "auth.json")
    config_text = "\n".join(
        [
            f"model_provider = {shell_quote_toml(codex_model_provider_name())}",
            f"model = {shell_quote_toml(codex_model_name())}",
            'model_reasoning_effort = "none"',
            "disable_response_storage = true",
            "",
            f'[model_providers.{shell_quote_toml(codex_model_provider_name())}]',
            f"name = {shell_quote_toml(codex_model_provider_name())}",
            f"base_url = {shell_quote_toml(codex_base_url())}",
            'wire_api = "responses"',
            f"requires_openai_auth = {str(codex_auth_mode() == 'openai').lower()}",
            "",
            "[features]",
            "js_repl = false",
        ]
    )
    (home / "config.toml").write_text(config_text, encoding="utf-8")
    return home


def codex_fake_mode() -> str:
    return os.environ.get("CS_LEARNING_CODEX_FAKE", "").strip().lower()


def ai_provider_name() -> str:
    return os.environ.get("CS_LEARNING_AI_PROVIDER", "codex-cli").strip().lower()


def openai_is_configured() -> bool:
    return bool(os.environ.get("OPENAI_API_KEY"))


def codex_cli_path() -> str:
    configured = os.environ.get("CS_LEARNING_CODEX_CLI")
    if configured:
        return configured
    if DEFAULT_CODEX_CLI.is_file():
        return str(DEFAULT_CODEX_CLI)
    found = shutil.which("codex.cmd") or shutil.which("codex")
    return found or ""


def codex_is_configured() -> bool:
    return bool(codex_cli_path())


def extract_response_text(response: object) -> str:
    output_text = getattr(response, "output_text", "")
    if output_text:
        return output_text
    output = getattr(response, "output", [])
    chunks: list[str] = []
    for item in output or []:
        for content in getattr(item, "content", []) or []:
            text = getattr(content, "text", "")
            if text:
                chunks.append(text)
    return "\n".join(chunks)


def ai_revision_schema() -> dict:
    return {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "revised_body": {
                "type": "string",
                "description": "Full replacement Markdown body only. Leave empty only when patch_ops can build the final body.",
            },
            "patch_ops": {
                "type": "array",
                "description": "Preferred compact Markdown patch operations. Use exact find text from the original body.",
                "items": {
                    "type": "object",
                    "additionalProperties": False,
                    "properties": {
                        "op": {
                            "type": "string",
                            "enum": ["replace", "append_after", "append_end"],
                        },
                        "section": {
                            "type": "string",
                            "description": "Nearby Markdown heading or human-readable location.",
                        },
                        "find": {
                            "type": "string",
                            "description": "Exact existing Markdown to replace or append after. Empty only for append_end.",
                        },
                        "replace": {
                            "type": "string",
                            "description": "New Markdown fragment.",
                        },
                    },
                    "required": ["op", "section", "find", "replace"],
                },
            },
            "summary": {
                "type": "string",
                "description": "One short sentence explaining the main improvement.",
            },
            "rationale": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Why these changes answer the learner's confusion.",
            },
            "changed_sections": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Markdown section headings or areas that changed.",
            },
            "resolved_question_ids": {
                "type": "array",
                "items": {"type": "integer"},
                "description": "Reader question IDs that the revised body directly answers.",
            },
            "suggested_new_nodes": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Reusable prerequisite ideas that deserve separate future nodes.",
            },
        },
        "required": [
            "revised_body",
            "patch_ops",
            "summary",
            "rationale",
            "changed_sections",
            "resolved_question_ids",
            "suggested_new_nodes",
        ],
    }


def revision_response(
    result: dict,
    reader_questions: list[dict],
    model: str,
    provider: str,
    original_body: str = "",
) -> dict:
    patch_ops = result.get("patch_ops") or []
    revised_body = result.get("revised_body", "").strip()
    if not revised_body and patch_ops and original_body:
        revised_body = apply_patch_ops(original_body, patch_ops)
    if not revised_body:
        logger.error("AI revision returned an empty body: %s", result)
        raise HTTPException(
            status_code=502,
            detail=(
                "AI revision returned neither a full body nor applicable patch ops. "
                f"Raw result: {json.dumps(result, ensure_ascii=False)[:1000]}"
            ),
        )

    known_question_ids = {item["id"] for item in reader_questions}
    resolved_question_ids = [
        question_id
        for question_id in result.get("resolved_question_ids", [])
        if question_id in known_question_ids
    ]

    return {
        "revision": {
            "revised_body": revised_body,
            "patch_ops": patch_ops,
            "summary": result.get("summary", ""),
            "rationale": result.get("rationale", []),
            "changed_sections": result.get("changed_sections", []),
            "resolved_question_ids": resolved_question_ids,
            "suggested_new_nodes": result.get("suggested_new_nodes", []),
            "model": model,
            "provider": provider,
        }
    }


def load_ai_target(
    conn: sqlite3.Connection,
    target_type: str,
    target_id: str,
    question_ids: list[int],
) -> tuple[dict, list[dict]]:
    if target_type == "node":
        row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (target_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Node not found")
        tags = [
            item["tag_name"]
            for item in conn.execute(
                "SELECT tag_name FROM node_tags WHERE node_slug = ? ORDER BY tag_name",
                (target_id,),
            ).fetchall()
        ]
        links = [
            {"target": item["target_slug"], "kind": item["kind"]}
            for item in conn.execute(
                "SELECT target_slug, kind FROM links WHERE source_slug = ? ORDER BY kind, target_slug",
                (target_id,),
            ).fetchall()
        ]
        target = row_to_node(row) | {"body": row["body"], "tags": tags, "links": links}
    else:
        row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (target_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Quiz not found")
        tags = [
            item["tag_name"]
            for item in conn.execute(
                "SELECT tag_name FROM quiz_tags WHERE quiz_id = ? ORDER BY tag_name",
                (target_id,),
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
                (target_id,),
            ).fetchall()
        ]
        target = row_to_quiz(row) | {"body": row["body"], "tags": tags, "linked_nodes": linked_nodes}

    params: list[object] = [target_type, target_id]
    query = """
        SELECT *
        FROM reader_questions
        WHERE target_type = ?
          AND target_id = ?
    """
    if question_ids:
        placeholders = ",".join("?" for _ in question_ids)
        query += f" AND id IN ({placeholders})"
        params.extend(question_ids)
    else:
        query += " AND status IN ('open', 'queued', 'solving', 'draft_ready')"
    query += " ORDER BY created_at DESC, id DESC"
    rows = conn.execute(query, params).fetchall()
    return target, [row_to_reader_question(item) for item in rows]


def build_ai_revision_prompt(
    target: dict,
    target_type: str,
    reader_questions: list[dict],
    instruction: str,
    draft_body: str,
) -> str:
    content_standard = """
Standard A: bilingual practical exam note. Use a patient tutorial tone, aligned English and Chinese,
concrete command/code examples, plain explanation, common mistakes, quick recall, and deliberate links.
When a reader question is local to the current node, fold the answer into this body. If it reveals a
reusable prerequisite, mention it in suggested_new_nodes instead of bloating this body.

Standard Q: quiz-bank item. Keep prompt, answer, explanation, plain explanation, what this tests, and
linked review. Do not skip reasoning steps; show line-by-line state changes and arithmetic when relevant.
"""
    payload = {
        "target_type": target_type,
        "target": target,
        "reader_questions": reader_questions,
        "extra_instruction": instruction.strip(),
        "draft_body_override": draft_body.strip(),
        "content_standard": content_standard.strip(),
    }
    return json.dumps(payload, ensure_ascii=False, indent=2)


def build_ai_revision_instruction(context_json: str) -> str:
    context = json.loads(context_json)
    target = context["target"]
    reader_questions = context["reader_questions"]
    original_body = context["draft_body_override"] or target["body"]
    questions_text = "\n".join(
        f"- #{item['id']}: {item['question']}" for item in reader_questions
    ) or "- No saved reader questions; use the extra instruction."
    return f"""
You are revising a personal CS learning knowledge base from a local web app.

Return a JSON object matching the provided schema. Do not wrap it in Markdown.
Rules:
- Prefer patch_ops over rewriting the whole file. Use small exact-find patches when the answer can be localized.
- If patch_ops can build the final Markdown, revised_body may be an empty string.
- If no safe exact patch is possible, revised_body must be the complete replacement Markdown body only, without YAML frontmatter.
- revised_body and patch_ops must not both be empty. If no useful edit is needed, return the original body unchanged as revised_body.
- Preserve the useful structure of the original body.
- Improve clarity, fill missing reasoning steps, and keep explanations tutorial-like.
- If target_type is node, prefer Standard A.
- If target_type is quiz, prefer Standard Q.
- Do not invent external sources.
- resolved_question_ids may include only reader questions directly answered in revised_body.

Target:
- type: {context["target_type"]}
- id: {target.get("slug") or target.get("id")}
- title: {target.get("title")}
- summary: {target.get("summary")}
- tags: {", ".join(target.get("tags", []))}

Extra instruction:
{context["extra_instruction"] or "Improve clarity for the saved reader questions."}

Reader questions:
{questions_text}

Content standard:
{context["content_standard"]}

Original Markdown body:
```markdown
{original_body}
```
""".strip()


def run_codex_revision(context_json: str, reader_questions: list[dict]) -> dict:
    fake_mode = codex_fake_mode()
    if fake_mode:
        if fake_mode == "success":
            context = json.loads(context_json)
            target = context["target"]
            original_body = (context["draft_body_override"] or target["body"]).strip()
            questions = context["reader_questions"]
            return revision_response(
                {
                    "revised_body": "",
                    "patch_ops": [
                        {
                            "op": "append_end",
                            "section": "end",
                            "find": "",
                            "replace": "## AI Draft Smoke Note\nThis fake Codex draft answers the selected reader question for the CRUD demo flow.",
                        }
                    ],
                    "summary": "Fake Codex draft added a small clarification.",
                    "rationale": ["Fake response used for deterministic smoke coverage."],
                    "changed_sections": ["AI Draft Smoke Note"],
                    "resolved_question_ids": [item["id"] for item in questions],
                    "suggested_new_nodes": [],
                },
                reader_questions,
                "fake-codex",
                "codex-cli",
                original_body,
            )
        if fake_mode == "non_json":
            raise HTTPException(status_code=502, detail="Codex CLI returned non-JSON output")
        if fake_mode == "timeout":
            raise HTTPException(status_code=504, detail="Codex CLI revision timed out")
        raise HTTPException(status_code=502, detail=f"Fake Codex failure: {fake_mode}")

    executable = codex_cli_path()
    if not executable:
        raise HTTPException(
            status_code=503,
            detail="Codex CLI is not configured. Set CS_LEARNING_CODEX_CLI or install @openai/codex.",
        )

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".json", delete=False) as schema_file:
        json.dump(ai_revision_schema(), schema_file, ensure_ascii=False)
        schema_path = schema_file.name
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".json", delete=False) as output_file:
        output_path = output_file.name

    try:
        model = codex_model_name()
        codex_home = ensure_codex_job_home()
        command = [
            executable,
            "--ask-for-approval",
            "never",
            "-m",
            model,
            "exec",
            "--ephemeral",
            "--ignore-rules",
            "-C",
            str(ROOT),
            "--sandbox",
            "read-only",
            "--output-schema",
            schema_path,
            "--output-last-message",
            output_path,
            "-",
        ]
        logger.info("Starting Codex CLI revision with executable=%s model=%s", executable, model)
        env = os.environ.copy()
        env["CODEX_HOME"] = str(codex_home)
        completed = subprocess.run(
            command,
            cwd=ROOT,
            input=build_ai_revision_instruction(context_json),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=int(os.environ.get("CS_LEARNING_CODEX_TIMEOUT", "180")),
            check=False,
            env=env,
        )
    except subprocess.TimeoutExpired as exc:
        logger.exception("Codex CLI revision timed out")
        raise HTTPException(status_code=504, detail="Codex CLI revision timed out") from exc
    finally:
        try:
            Path(schema_path).unlink(missing_ok=True)
        except OSError:
            logger.warning("Could not delete temporary Codex schema file: %s", schema_path)

    output = ""
    output_file_path = Path(output_path)
    if output_file_path.is_file():
        output = output_file_path.read_text(encoding="utf-8").strip()
    if not output:
        output = (completed.stdout or "").strip()
    try:
        output_file_path.unlink(missing_ok=True)
    except OSError:
        logger.warning("Could not delete temporary Codex output file: %s", output_path)

    diagnostics = (completed.stderr or "").strip()
    if completed.returncode != 0:
        logger.error(
            "Codex CLI revision failed returncode=%s stderr=%s stdout=%s",
            completed.returncode,
            diagnostics[-2000:],
            output[-2000:],
        )
        raise HTTPException(
            status_code=502,
            detail=f"Codex CLI revision failed: {summarize_ai_error(diagnostics or output or str(completed.returncode))}",
        )

    try:
        result = json.loads(output)
    except json.JSONDecodeError as exc:
        logger.error("Codex CLI returned non-JSON output: %s", output[-2000:])
        raise HTTPException(status_code=502, detail="Codex CLI returned non-JSON output") from exc

    logger.info("Codex CLI revision finished")
    context = json.loads(context_json)
    original_body = (context["draft_body_override"] or context["target"]["body"]).strip()
    return revision_response(result, reader_questions, codex_model_name(), "codex-cli", original_body)


def run_openai_revision(context_json: str, reader_questions: list[dict]) -> dict:
    if not openai_is_configured():
        logger.warning("AI revision rejected because OPENAI_API_KEY is not configured")
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY is not configured for the local API process",
        )

    try:
        from openai import OpenAI
    except ImportError as exc:
        logger.exception("AI revision failed because the OpenAI package is missing")
        raise HTTPException(
            status_code=503,
            detail="OpenAI Python package is not installed. Run pip install -r backend/requirements.txt",
        ) from exc

    client = OpenAI()
    system_prompt = """
You revise a personal CS learning knowledge base. Return only valid JSON matching the schema.
Do not invent external sources. Preserve Markdown. Preserve the learner's useful structure.
Improve clarity, fill missing reasoning steps, and keep the body suitable for direct saving.
Prefer compact patch_ops with exact find text; use revised_body only when a patch would be unsafe.
If the target is a node, prefer Standard A. If the target is a quiz, prefer Standard Q.
Resolve only question IDs that are directly answered in the revised body.
"""

    try:
        response = client.responses.create(
            model=openai_model_name(),
            input=[
                {"role": "system", "content": system_prompt.strip()},
                {"role": "user", "content": context_json},
            ],
            text={
                "format": {
                    "type": "json_schema",
                    "name": "cs_learning_revision",
                    "schema": ai_revision_schema(),
                    "strict": True,
                }
            },
        )
        raw_text = extract_response_text(response)
        result = json.loads(raw_text)
    except Exception as exc:
        logger.exception("OpenAI API revision failed during model call or response parsing")
        raise HTTPException(status_code=502, detail=f"OpenAI API revision failed: {exc}") from exc

    context = json.loads(context_json)
    original_body = (context["draft_body_override"] or context["target"]["body"]).strip()
    return revision_response(result, reader_questions, openai_model_name(), "openai-api", original_body)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def update_ai_job(job_id: int, **fields: object) -> None:
    if not fields:
        return
    fields["updated_at"] = utc_now()
    assignments = ", ".join(f"{name} = ?" for name in fields)
    values = list(fields.values())
    values.append(job_id)
    with get_conn() as conn:
        conn.execute(f"UPDATE ai_jobs SET {assignments} WHERE id = ?", values)
        conn.commit()


def add_ai_job_event(job_id: int, stage: str, message: str, level: str = "info") -> None:
    with get_conn() as conn:
        conn.execute(
            """
            INSERT INTO ai_job_events (job_id, level, stage, message, created_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            (job_id, level, stage, message, utc_now()),
        )
        conn.commit()


def get_ai_job_or_404(conn: sqlite3.Connection, job_id: int) -> sqlite3.Row:
    row = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (job_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="AI job not found")
    return row


def ensure_job_can_write(job_id: int) -> sqlite3.Row:
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
    if row["status"] not in {"queued", "solving"}:
        raise HTTPException(status_code=409, detail=f"AI job is already {row['status']}")
    return row


def recover_stale_ai_jobs() -> None:
    cutoff_seconds = int(os.environ.get("CS_LEARNING_STALE_JOB_SECONDS", "900"))
    cutoff = datetime.now(timezone.utc).timestamp() - cutoff_seconds
    now = utc_now()
    with get_conn() as conn:
        rows = conn.execute(
            """
            SELECT id, updated_at
            FROM ai_jobs
            WHERE status IN ('queued', 'solving')
            """
        ).fetchall()
        stale_ids = []
        for row in rows:
            try:
                updated = datetime.fromisoformat(row["updated_at"]).timestamp()
            except ValueError:
                updated = 0
            if updated < cutoff:
                stale_ids.append(row["id"])
        for job_id in stale_ids:
            conn.execute(
                """
                UPDATE ai_jobs
                SET status = 'failed',
                    stage = 'stale_failed',
                    error = ?,
                    error_code = 'stale_worker',
                    updated_at = ?,
                    completed_at = ?
                WHERE id = ?
                """,
                ("AI job was left queued/solving after the worker stopped or the API restarted.", now, now, job_id),
            )
        conn.commit()
    for job_id in stale_ids:
        add_ai_job_event(job_id, "stale_failed", "Marked stale after local worker recovery.", "warning")


def run_ai_job(job_id: int) -> None:
    try:
        add_ai_job_event(job_id, "queued", "AI job picked up by local worker.")
        update_ai_job(job_id, status="solving", stage="context_built", started_at=utc_now())
        with get_conn() as conn:
            row = get_ai_job_or_404(conn, job_id)
            if row["status"] not in {"queued", "solving"}:
                add_ai_job_event(job_id, row["status"], "AI job stopped before context build.", "warning")
                return
            question_ids = json.loads(row["question_ids"] or "[]")
            target, reader_questions = load_ai_target(
                conn,
                row["target_type"],
                row["target_id"],
                question_ids,
            )
            if row["draft_body"].strip():
                target["body"] = row["draft_body"].strip()
            context_json = build_ai_revision_prompt(
                target,
                row["target_type"],
                reader_questions,
                row["instruction"],
                row["draft_body"],
            )
        add_ai_job_event(job_id, "context_built", f"Built prompt with {len(reader_questions)} reader question(s).")

        provider = ai_provider_name()
        update_ai_job(job_id, provider=provider, stage="codex_running" if provider == "codex-cli" else "model_running")
        add_ai_job_event(job_id, "codex_running" if provider == "codex-cli" else "model_running", f"Starting {provider}.")
        if provider == "codex-cli":
            response = run_codex_revision(context_json, reader_questions)
        elif provider == "openai-api":
            response = run_openai_revision(context_json, reader_questions)
        else:
            raise HTTPException(
                status_code=400,
                detail="CS_LEARNING_AI_PROVIDER must be codex-cli or openai-api",
            )

        ensure_job_can_write(job_id)
        revision = response["revision"]
        update_ai_job(
            job_id,
            status="draft_ready",
            stage="draft_ready",
            provider=revision["provider"],
            model=revision["model"],
            result_json=json.dumps(response, ensure_ascii=False),
            completed_at=utc_now(),
        )
        add_ai_job_event(job_id, "draft_ready", "AI draft is ready for human review.")
    except Exception as exc:
        detail = exc.detail if isinstance(exc, HTTPException) else str(exc)
        logger.exception("AI job %s failed", job_id)
        error_text = str(detail)
        error_code = classify_ai_error(error_text)
        try:
            ensure_job_can_write(job_id)
        except HTTPException:
            add_ai_job_event(job_id, "stopped", "AI worker stopped after job state changed.", "warning")
            return
        update_ai_job(job_id, status="failed", stage="failed", error=error_text, error_code=error_code, completed_at=utc_now())
        add_ai_job_event(job_id, "failed", summarize_ai_error(error_text), "error")


@app.get("/api/health")
def health() -> dict:
    recover_stale_ai_jobs()
    return {
        "ok": True,
        "ai": {
            "provider": ai_provider_name(),
            "configured": codex_is_configured() if ai_provider_name() == "codex-cli" else openai_is_configured(),
            "model": codex_model_name() if ai_provider_name() == "codex-cli" else openai_model_name(),
            "codex_cli": codex_cli_path(),
            "codex_model_provider": codex_model_provider_name(),
            "codex_base_url": codex_base_url() if ai_provider_name() == "codex-cli" else "",
            "codex_home": str(codex_job_home()) if ai_provider_name() == "codex-cli" else "",
        },
    }


@app.post("/api/ai/revise")
def revise_with_ai(payload: AiReviseRequest) -> dict:
    provider = ai_provider_name()
    logger.info(
        "AI revision requested provider=%s target_type=%s target_id=%s question_ids=%s",
        provider,
        payload.target_type,
        payload.target_id,
        payload.question_ids,
    )

    with get_conn() as conn:
        target, reader_questions = load_ai_target(
            conn,
            payload.target_type,
            payload.target_id,
            payload.question_ids,
        )
    logger.info("AI revision context loaded with %s open reader questions", len(reader_questions))

    if payload.draft_body.strip():
        target["body"] = payload.draft_body.strip()

    context_json = build_ai_revision_prompt(
        target,
        payload.target_type,
        reader_questions,
        payload.instruction,
        payload.draft_body,
    )
    if provider == "codex-cli":
        response = run_codex_revision(context_json, reader_questions)
    elif provider == "openai-api":
        response = run_openai_revision(context_json, reader_questions)
    else:
        raise HTTPException(
            status_code=400,
            detail="CS_LEARNING_AI_PROVIDER must be codex-cli or openai-api",
        )

    resolved_question_ids = response["revision"]["resolved_question_ids"]
    logger.info(
        "AI revision succeeded provider=%s target_type=%s target_id=%s resolved_question_ids=%s",
        provider,
        payload.target_type,
        payload.target_id,
        resolved_question_ids,
    )
    return response


@app.post("/api/ai/jobs")
def create_ai_job(payload: AiJobCreate, background_tasks: BackgroundTasks) -> dict:
    question_ids = list(dict.fromkeys(payload.question_ids))
    now = utc_now()

    with get_conn() as conn:
        if payload.target_type == "node":
            exists = conn.execute("SELECT 1 FROM nodes WHERE slug = ?", (payload.target_id,)).fetchone()
        else:
            exists = conn.execute("SELECT 1 FROM quizzes WHERE id = ?", (payload.target_id,)).fetchone()
        if not exists:
            raise HTTPException(status_code=404, detail="target not found")

        question = payload.question.strip()
        if question:
            cursor = conn.execute(
                """
                INSERT INTO reader_questions (target_type, target_id, question, status, created_at)
                VALUES (?, ?, ?, 'queued', ?)
                """,
                (payload.target_type, payload.target_id, question, now),
            )
            question_ids.append(cursor.lastrowid)

        base_body = payload.draft_body.strip()
        if not base_body:
            if payload.target_type == "node":
                body_row = conn.execute("SELECT body FROM nodes WHERE slug = ?", (payload.target_id,)).fetchone()
            else:
                body_row = conn.execute("SELECT body FROM quizzes WHERE id = ?", (payload.target_id,)).fetchone()
            base_body = body_row["body"] if body_row else ""

        cursor = conn.execute(
            """
            INSERT INTO ai_jobs (
                target_type, target_id, question_ids, provider, model, status, stage,
                instruction, draft_body, created_at, updated_at, base_body_hash
            )
            VALUES (?, ?, ?, ?, ?, 'queued', 'queued', ?, ?, ?, ?, ?)
            """,
            (
                payload.target_type,
                payload.target_id,
                json.dumps(question_ids),
                ai_provider_name(),
                codex_model_name() if ai_provider_name() == "codex-cli" else openai_model_name(),
                payload.instruction,
                payload.draft_body,
                now,
                now,
                body_hash(base_body),
            ),
        )
        conn.commit()
        row = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (cursor.lastrowid,)).fetchone()

    add_ai_job_event(row["id"], "queued", "AI job created. Reader questions remain open until draft is applied.")
    background_tasks.add_task(run_ai_job, row["id"])
    return {"job": row_to_ai_job(row)}


@app.get("/api/ai/jobs")
def list_ai_jobs(
    target_type: Optional[str] = None,
    target_id: Optional[str] = None,
    status: Optional[str] = None,
) -> dict:
    query = "SELECT * FROM ai_jobs WHERE 1 = 1"
    params: list[str] = []
    if target_type:
        if target_type not in {"node", "quiz"}:
            raise HTTPException(status_code=400, detail="target_type must be node or quiz")
        query += " AND target_type = ?"
        params.append(target_type)
    if target_id:
        query += " AND target_id = ?"
        params.append(target_id)
    if status == "active":
        query += " AND status IN ('queued', 'solving', 'draft_ready', 'failed')"
    elif status:
        query += " AND status = ?"
        params.append(status)
    query += " ORDER BY updated_at DESC, id DESC LIMIT 50"
    with get_conn() as conn:
        rows = conn.execute(query, params).fetchall()
    return {"jobs": [row_to_ai_job(row) for row in rows]}


@app.get("/api/ai/jobs/{job_id}")
def get_ai_job(job_id: int) -> dict:
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
    return {"job": row_to_ai_job(row)}


@app.get("/api/ai/jobs/{job_id}/events")
def list_ai_job_events(job_id: int) -> dict:
    with get_conn() as conn:
        get_ai_job_or_404(conn, job_id)
        rows = conn.execute(
            """
            SELECT id, job_id, level, stage, message, created_at
            FROM ai_job_events
            WHERE job_id = ?
            ORDER BY id
            """,
            (job_id,),
        ).fetchall()
    return {"events": [dict(row) for row in rows]}


@app.post("/api/ai/jobs/{job_id}/apply")
def apply_ai_job(job_id: int, payload: AiJobApply) -> dict:
    now = utc_now()
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
        if row["status"] != "draft_ready":
            raise HTTPException(status_code=400, detail="AI job is not draft_ready")
        if row["target_type"] == "node":
            target_row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (row["target_id"],)).fetchone()
        else:
            target_row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (row["target_id"],)).fetchone()
        if not target_row:
            raise HTTPException(status_code=404, detail="AI job target not found")
        if row["base_body_hash"] and body_hash(target_row["body"]) != row["base_body_hash"]:
            raise HTTPException(status_code=409, detail="Target Markdown changed after this draft was created")

        write_markdown_body(CONTENT_ROOT / target_row["path"], payload.body)
        if row["target_type"] == "node":
            update_node_body_in_conn(conn, target_row, payload.body.strip())
        else:
            update_quiz_body_in_conn(conn, target_row, payload.body.strip())
        question_ids = json.loads(row["question_ids"] or "[]")
        if question_ids:
            placeholders = ",".join("?" for _ in question_ids)
            conn.execute(
                f"""
                UPDATE reader_questions
                SET status = 'resolved',
                    resolved_at = ?,
                    resolution_note = ?
                WHERE id IN ({placeholders})
                """,
                [now, "Resolved by applied AI job", *question_ids],
            )
        conn.execute(
            """
            UPDATE ai_jobs
            SET status = 'applied',
                stage = 'applied',
                updated_at = ?,
                completed_at = ?
            WHERE id = ?
            """,
            (now, now, job_id),
        )
        conn.commit()
        updated = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (job_id,)).fetchone()
    add_ai_job_event(job_id, "applied", "Human applied the draft and resolved linked questions.")
    return {"job": row_to_ai_job(updated)}


@app.post("/api/ai/jobs/{job_id}/cancel")
def cancel_ai_job(job_id: int) -> dict:
    now = utc_now()
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
        if row["status"] in {"draft_ready", "failed", "cancelled", "applied", "rejected", "retried"}:
            raise HTTPException(status_code=400, detail=f"cannot cancel {row['status']} job")
        conn.execute(
            """
            UPDATE ai_jobs
            SET status = 'cancelled',
                stage = 'cancelled',
                updated_at = ?,
                completed_at = ?
            WHERE id = ?
            """,
            (now, now, job_id),
        )
        conn.commit()
        updated = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (job_id,)).fetchone()
    add_ai_job_event(job_id, "cancelled", "Human cancelled the job. Linked questions remain open.", "warning")
    return {"job": row_to_ai_job(updated)}


@app.post("/api/ai/jobs/{job_id}/reject")
def reject_ai_job(job_id: int, payload: AiJobReject) -> dict:
    now = utc_now()
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
        if row["status"] != "draft_ready":
            raise HTTPException(status_code=400, detail=f"cannot reject {row['status']} job")
        note = payload.reason.strip() or "Draft rejected by human review"
        conn.execute(
            """
            UPDATE ai_jobs
            SET status = 'rejected',
                stage = 'rejected',
                error = ?,
                updated_at = ?,
                completed_at = ?
            WHERE id = ?
            """,
            (note, now, now, job_id),
        )
        conn.commit()
        updated = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (job_id,)).fetchone()
    add_ai_job_event(job_id, "rejected", "Human rejected the draft. Linked questions remain open.", "warning")
    return {"job": row_to_ai_job(updated)}


@app.post("/api/ai/jobs/{job_id}/retry")
def retry_ai_job(job_id: int, background_tasks: BackgroundTasks) -> dict:
    now = utc_now()
    with get_conn() as conn:
        row = get_ai_job_or_404(conn, job_id)
        if row["status"] != "failed":
            raise HTTPException(status_code=400, detail=f"cannot retry {row['status']} job")

        conn.execute(
            """
            UPDATE ai_jobs
            SET status = 'retried',
                stage = 'retried',
                updated_at = ?,
                completed_at = ?
            WHERE id = ?
            """,
            (now, now, job_id),
        )

        cursor = conn.execute(
            """
            INSERT INTO ai_jobs (
                target_type, target_id, question_ids, provider, model, status, stage,
                instruction, draft_body, created_at, updated_at, retry_of, attempt, base_body_hash
            )
            VALUES (?, ?, ?, ?, ?, 'queued', 'queued', ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                row["target_type"],
                row["target_id"],
                row["question_ids"],
                ai_provider_name(),
                codex_model_name() if ai_provider_name() == "codex-cli" else openai_model_name(),
                row["instruction"],
                row["draft_body"],
                now,
                now,
                row["id"],
                int(row["attempt"] or 1) + 1,
                row["base_body_hash"],
            ),
        )
        conn.commit()
        new_row = conn.execute("SELECT * FROM ai_jobs WHERE id = ?", (cursor.lastrowid,)).fetchone()

    add_ai_job_event(new_row["id"], "queued", f"Retry created from job #{job_id}.")
    background_tasks.add_task(run_ai_job, new_row["id"])
    return {"job": row_to_ai_job(new_row)}


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

    with get_conn() as conn:
        row = conn.execute("SELECT * FROM nodes WHERE slug = ?", (slug,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Node not found")

        content_path = CONTENT_ROOT / row["path"]
        if not content_path.is_file():
            raise HTTPException(status_code=404, detail="Node source file not found")

        write_markdown_body(content_path, body)

        update_node_body_in_conn(conn, row, body)
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

    with get_conn() as conn:
        row = conn.execute("SELECT * FROM quizzes WHERE id = ?", (quiz_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Quiz not found")

        content_path = CONTENT_ROOT / row["path"]
        if not content_path.is_file():
            raise HTTPException(status_code=404, detail="Quiz source file not found")

        write_markdown_body(content_path, body)

        update_quiz_body_in_conn(conn, row, body)
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
    if status == "active":
        query += " AND status IN ('open', 'queued', 'solving', 'draft_ready', 'failed')"
    elif status:
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


@app.post("/api/reader-questions/{question_id}/dismiss")
def dismiss_reader_question(question_id: int, payload: ReaderQuestionResolve) -> dict:
    now = utc_now()
    with get_conn() as conn:
        row = conn.execute(
            "SELECT * FROM reader_questions WHERE id = ?", (question_id,)
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="reader question not found")

        conn.execute(
            """
            UPDATE reader_questions
            SET status = 'dismissed',
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


@app.delete("/api/reader-questions/{question_id}")
def delete_reader_question(question_id: int) -> dict:
    with get_conn() as conn:
        row = conn.execute(
            "SELECT * FROM reader_questions WHERE id = ?", (question_id,)
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="reader question not found")
        conn.execute("DELETE FROM reader_questions WHERE id = ?", (question_id,))
        conn.commit()
    return {"ok": True}
