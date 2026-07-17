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


class SyncPairRequest(BaseModel):
    token: str = Field(min_length=1)
    device_name: str = Field(default="android-device", max_length=120)


class SyncManifestRequest(BaseModel):
    cursor: int = Field(default=0, ge=0)
    serverId: str = ""
    scope: dict = Field(default_factory=dict)


class SyncPullRequest(BaseModel):
    entityType: str = Field(min_length=1)
    ids: list[str] = Field(default_factory=list, max_length=200)
    scope: dict = Field(default_factory=dict)


class SyncPushAttemptItem(BaseModel):
    clientAttemptId: str = Field(min_length=1, max_length=64)
    quizId: str = Field(min_length=1)
    grade: str = Field(pattern="^(again|hard|good|easy)$")
    answeredAt: str = ""
    elapsedMs: int = Field(default=0, ge=0)
    note: str = ""


class SyncPushAttemptsRequest(BaseModel):
    items: list[SyncPushAttemptItem] = Field(default_factory=list, max_length=500)


class SyncPushCaptureItem(BaseModel):
    id: str = Field(min_length=1, max_length=64)
    body: str = Field(min_length=1)
    type: str = "concept_seed"
    topicHint: str = ""
    sourceLabel: str = ""
    createdAt: str = ""


class SyncPushCapturesRequest(BaseModel):
    items: list[SyncPushCaptureItem] = Field(default_factory=list, max_length=500)


class SyncPushQuestionItem(BaseModel):
    clientId: str = Field(min_length=1, max_length=64)
    nodeId: str = Field(min_length=1)
    question: str = Field(min_length=1)
    createdAt: str = ""


class SyncPushQuestionsRequest(BaseModel):
    items: list[SyncPushQuestionItem] = Field(default_factory=list, max_length=500)


class SyncPushNodeItem(BaseModel):
    changeId: str = Field(min_length=1, max_length=64)
    id: str = Field(min_length=1, max_length=64)
    title: str = Field(min_length=1, max_length=300)
    area: str = Field(min_length=1, max_length=80)
    track: str = Field(min_length=1, max_length=80)
    summary: str = ""
    body: str = Field(min_length=1)
    visibility: str = Field(pattern="^(core|support|draft|archive)$")
    baseRevision: Optional[int] = Field(default=None, ge=0)
    revision: int = Field(ge=1)
    tombstone: bool = False


class SyncPushNodesRequest(BaseModel):
    items: list[SyncPushNodeItem] = Field(default_factory=list, max_length=200)


class SyncPushQuizItem(BaseModel):
    changeId: str = Field(min_length=1, max_length=64)
    id: str = Field(min_length=1, max_length=64)
    title: Optional[str] = Field(default=None, max_length=300)
    area: str = Field(min_length=1, max_length=80)
    difficulty: str = Field(default="", max_length=40)
    summary: Optional[str] = None
    body: str = Field(min_length=1)
    visibility: str = Field(pattern="^(practice|support|draft|archive)$")
    baseRevision: Optional[int] = Field(default=None, ge=0)
    revision: int = Field(ge=1)
    tombstone: bool = False


class SyncPushQuizzesRequest(BaseModel):
    items: list[SyncPushQuizItem] = Field(default_factory=list, max_length=200)


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
