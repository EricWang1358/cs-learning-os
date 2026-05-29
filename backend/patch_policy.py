from __future__ import annotations

from fastapi import HTTPException


SUPPORTED_PATCH_OPS = {"replace", "append_after", "append_end"}


def non_empty_line_count(text: str) -> int:
    return sum(1 for line in text.splitlines() if line.strip())


def validate_patch_op(index: int, action: str, find_text: str, replace_text: str) -> None:
    if action != "replace":
        return
    find_lines = non_empty_line_count(find_text)
    replace_lines = non_empty_line_count(replace_text)
    if replace_lines >= 4 and find_lines < 2:
        raise HTTPException(
            status_code=502,
            detail=(
                f"AI patch op #{index} is unsafe: replace find text is too small for "
                "a multi-line replacement. Match the full old block instead."
            ),
        )
    if replace_text.strip().startswith(find_text.strip()) and replace_lines > find_lines + 2:
        raise HTTPException(
            status_code=502,
            detail=(
                f"AI patch op #{index} is unsafe: replacement appears to append new content "
                "after the old text instead of replacing the full old block."
            ),
        )


def apply_patch_ops(original_body: str, patch_ops: list[dict]) -> str:
    body = original_body
    for index, op in enumerate(patch_ops, start=1):
        action = op.get("op")
        find_text = str(op.get("find") or "")
        replace_text = str(op.get("replace") or "")
        if action not in SUPPORTED_PATCH_OPS:
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
        validate_patch_op(index, action, find_text, replace_text)
        if action == "replace":
            body = body.replace(find_text, replace_text, 1)
        else:
            body = body.replace(find_text, f"{find_text.rstrip()}\n\n{replace_text.strip()}", 1)
    return body.strip()
