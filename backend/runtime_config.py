from __future__ import annotations

import os
from pathlib import Path


def env_flag(name: str, default: bool = False) -> bool:
    value = os.environ.get(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on", "enabled"}


def app_profile() -> str:
    return os.environ.get("CS_LEARNING_PROFILE", "dev").strip().lower() or "dev"


def ai_enabled() -> bool:
    return env_flag("CS_LEARNING_AI_ENABLED", True)


def beta_mode() -> bool:
    return app_profile() in {"beta", "friend-beta"} or env_flag("CS_LEARNING_BETA", False)


def sync_bind_host() -> str:
    """Interface the API binds to. Defaults to loopback; set CS_LEARNING_HOST
    (or scripts/dev.ps1 -ApiHost) to a LAN/Tailscale address to serve sync."""
    return os.environ.get("CS_LEARNING_HOST", "127.0.0.1").strip() or "127.0.0.1"


def default_data_root(root: Path) -> Path:
    if os.environ.get("CS_LEARNING_DATA_ROOT"):
        return Path(os.environ["CS_LEARNING_DATA_ROOT"]).expanduser().resolve()
    return (Path.home() / "CSLearningOS").resolve()

