"""
Persistent state — what files we've already handled.

Stored as a flat JSON file with `{<handle>: <uploaded_at_iso>}`. We
also keep a small ring buffer of recent log events for the web UI.
"""

import json
import os
import threading
import time
from collections import deque
from dataclasses import dataclass
from typing import Deque, Dict


@dataclass
class LogEvent:
    ts: float
    level: str
    msg: str


class State:
    def __init__(self, path: str, max_logs: int = 500):
        self.path = path
        self._lock = threading.Lock()
        self._uploads: Dict[str, float] = {}
        self._logs: Deque[LogEvent] = deque(maxlen=max_logs)
        self._load()

    def _load(self) -> None:
        if not os.path.exists(self.path):
            return
        try:
            with open(self.path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self._uploads = {h: float(t) for h, t in data.get("uploads", {}).items()}
        except Exception:
            # Corrupt state; start clean.
            self._uploads = {}

    def _save(self) -> None:
        os.makedirs(os.path.dirname(self.path), exist_ok=True)
        tmp = self.path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump({"uploads": self._uploads}, f)
        os.replace(tmp, self.path)

    # ---------- uploads ----------

    def is_uploaded(self, handle: str) -> bool:
        with self._lock:
            return handle in self._uploads

    def mark_uploaded(self, handle: str) -> None:
        with self._lock:
            self._uploads[handle] = time.time()
            self._save()

    def unmark(self, handle: str) -> None:
        with self._lock:
            self._uploads.pop(handle, None)
            self._save()

    def uploaded_count(self) -> int:
        with self._lock:
            return len(self._uploads)

    # ---------- logs ----------

    def log(self, level: str, msg: str) -> None:
        ev = LogEvent(ts=time.time(), level=level, msg=msg)
        with self._lock:
            self._logs.append(ev)

    def recent_logs(self, n: int = 100) -> list[LogEvent]:
        with self._lock:
            return list(self._logs)[-n:]
