from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field


class ReaderQuestionCreate(BaseModel):
    target_type: str = Field(pattern="^(node|quiz)$")
    target_id: str = Field(min_length=1)
    question: str = Field(min_length=1)


class ReaderQuestionResolve(BaseModel):
    resolution_note: str = ""


class BodyUpdate(BaseModel):
    body: str
    base_body_hash: str = ""


class NodeCreate(BaseModel):
    title: str = Field(min_length=1)
    area: str = Field(default="questions", pattern="^[a-z0-9-]+$")
    track: str = Field(default="general", pattern="^[a-z0-9-]+$")
    summary: str = ""
    tags: list[str] = []
    visibility: str = Field(default="support", pattern="^(core|support|draft|archive)$")
    status: str = "draft"
    order: int = 1000


class NodeReadMark(BaseModel):
    read_at: Optional[str] = None
    min_interval_seconds: int = Field(default=60, ge=0, le=86400)


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


class QuizAttemptCreate(BaseModel):
    grade: str = Field(pattern="^(again|hard|good|easy)$")
    elapsed_ms: int = Field(default=0, ge=0)
    note: str = ""


class BiteCardCreate(BaseModel):
    source_type: str = Field(pattern="^(node|quiz)$")
    source_id: str = Field(min_length=1)
    title: str = Field(min_length=1)
    area: str = ""
    difficulty: str = ""
    question_type: str = Field(default="blank", pattern="^(blank|multiple_choice)$")
    prompt: str = Field(min_length=1)
    answer: str = Field(min_length=1)
    options: list[str] = []
    hint: str = ""
    explanation: list[str] = []


class BiteCardUpdate(BaseModel):
    title: str = Field(min_length=1)
    area: str = ""
    difficulty: str = ""
    question_type: str = Field(default="blank", pattern="^(blank|multiple_choice)$")
    prompt: str = Field(min_length=1)
    answer: str = Field(min_length=1)
    options: list[str] = []
    hint: str = ""
    explanation: list[str] = []
    status: str = Field(default="active", pattern="^(active|archive)$")
