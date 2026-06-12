from __future__ import annotations

import json
import logging
import os

from fastapi import HTTPException

try:
    from .ai_job_service import summarize_ai_error
    from .codex_service import codex_model_name, run_codex_json
    from .patch_policy import apply_patch_ops
except ImportError:
    from ai_job_service import summarize_ai_error
    from codex_service import codex_model_name, run_codex_json
    from patch_policy import apply_patch_ops


logger = logging.getLogger("cs_learning.ai_revision")


def openai_model_name() -> str:
    return os.environ.get("CS_LEARNING_OPENAI_MODEL", "gpt-5.4-mini")


def codex_fake_mode() -> str:
    return os.environ.get("CS_LEARNING_CODEX_FAKE", "").strip().lower()


def ai_provider_name() -> str:
    return os.environ.get("CS_LEARNING_AI_PROVIDER", "codex-cli").strip().lower()


def openai_is_configured() -> bool:
    return bool(os.environ.get("OPENAI_API_KEY"))


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
Quality bar: match the depth of "Shark Tank Passcode: process_code and is_valid_code". Do not skip
prerequisite vocabulary, command effects, operand/register roles, state changes, branch decisions, or
arithmetic. For C/GDB/assembly, include tiny examples and define terms a first-pass learner would ask about.
When a reader question is local to the current node, fold the answer into this body. If it reveals a
reusable prerequisite, mention it in suggested_new_nodes instead of bloating this body.
Placement gate: cs-fundamentals is only for intro-level prerequisites or foundational bridges such as
intro C, GDB, x86-64, binary representation, memory, CSAPP/Bomb Lab basics. Do not place advanced,
project-specific, tool-only, or rare-trick material there by default.

Standard Q: quiz-bank item. Keep prompt, answer, explanation, plain explanation, what this tests, and
linked review. Do not skip reasoning steps; show line-by-line state changes and arithmetic when relevant.
Include "How To Think" when the solution depends on recognizing noise, calling convention, state tracing,
pointer/memory layout, or a non-obvious instruction pattern. Explain tempting wrong answers when useful.
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
- For replace patch_ops, find must contain the complete old block being replaced, not just a heading or first line.
- A replace patch_op must not keep a duplicate copy of the old block after the new block. If you add bilingual lines, replace the full original section with the bilingual section.
- If patch_ops can build the final Markdown, revised_body may be an empty string.
- If no safe exact patch is possible, revised_body must be the complete replacement Markdown body only, without YAML frontmatter.
- revised_body and patch_ops must not both be empty. If no useful edit is needed, return the original body unchanged as revised_body.
- Preserve the useful structure of the original body.
- Improve clarity, fill missing reasoning steps, and keep explanations tutorial-like.
- If target_type is node, prefer Standard A.
- If target_type is quiz, prefer Standard Q.
- Treat Shark Tank Passcode as the minimum quality bar for quiz and low-level systems explanations.
- Do not create shallow prerequisite nodes. If a concept deserves a node, make it tutorial-grade; otherwise fold it into the current body.
- Keep cs-fundamentals intro-level only. Suggest a different area/track for advanced, project-specific, tool-only, or rare material.
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

    try:
        result = run_codex_json(build_ai_revision_instruction(context_json), ai_revision_schema())
    except HTTPException as exc:
        if exc.status_code == 502 and isinstance(exc.detail, str):
            raise HTTPException(status_code=502, detail=summarize_ai_error(exc.detail)) from exc
        raise

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
Use Shark Tank Passcode as the quality bar: detailed reasoning, prerequisite vocabulary, examples, common mistakes, and linked review.
Prefer compact patch_ops with exact find text; use revised_body only when a patch would be unsafe.
For replace patch_ops, match and replace the full old block. Do not use a heading-only find string to insert a new section above the old section.
If the target is a node, prefer Standard A. If the target is a quiz, prefer Standard Q.
Keep cs-fundamentals intro-level only; do not dump advanced or project-specific topics there.
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


def run_revision(context_json: str, reader_questions: list[dict]) -> dict:
    provider = ai_provider_name()
    if provider == "codex-cli":
        return run_codex_revision(context_json, reader_questions)
    if provider == "openai-api":
        return run_openai_revision(context_json, reader_questions)
    raise HTTPException(
        status_code=400,
        detail="CS_LEARNING_AI_PROVIDER must be codex-cli or openai-api",
    )
