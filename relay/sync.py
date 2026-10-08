"""
The orchestrator. Connects to the camera, polls, downloads, uploads.

Runs in a single thread. Reports progress via the shared [State] so
the web UI can render status.
"""

import logging
import os
import threading
import time
from typing import List, Optional

from .camera import Camera, CameraFile, CameraError
from .config import Config
from .nas import NasUploader
from .state import State

log = logging.getLogger("camrelay.sync")


class Sync:
    def __init__(self, cfg: Config, state: State):
        self.cfg = cfg
        self.state = state
        self.camera = Camera(cfg.camera, cfg.staging_dir)
        self.uploader = NasUploader(cfg.nas)
        self._thread: Optional[threading.Thread] = None
        self._stop = threading.Event()
        self._last_cycle: float = 0.0
        self._cycle_count: int = 0
        self._errors_streak: int = 0

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, name="camrelay-sync", daemon=True)
        self._thread.start()

    def stop(self, join_timeout: float = 5.0) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=join_timeout)

    def status(self) -> dict:
        return {
            "running": bool(self._thread and self._thread.is_alive()),
            "last_cycle": self._last_cycle,
            "cycle_count": self._cycle_count,
            "errors_streak": self._errors_streak,
            "camera": self.camera.summary(),
            "uploaded_total": self.state.uploaded_count(),
        }

    # ---------- main loop ----------

    def _run(self) -> None:
        log.info("sync started, staging=%s", self.cfg.staging_dir)
        while not self._stop.is_set():
            try:
                self._one_cycle()
                self._errors_streak = 0
            except Exception as e:
                self._errors_streak += 1
                log.exception("cycle error: %s", e)
                self.state.log("ERROR", f"cycle error: {e}")
            # Sleep with cancellation check every second.
            for _ in range(self.cfg.camera.poll_interval_sec):
                if self._stop.is_set():
                    break
                time.sleep(1)
        log.info("sync stopped")

    def _one_cycle(self) -> None:
        self._last_cycle = time.time()
        self._cycle_count += 1

        # 1. List files on camera
        try:
            files = self.camera.list_files()
        except CameraError as e:
            self.state.log("WARNING", f"camera list: {e}")
            return

        # 2. Filter: wanted extensions, not already uploaded
        exts = set(e.lower() for e in self.cfg.camera.include_ext)
        pending: List[CameraFile] = []
        for f in files:
            if exts and f.ext not in exts:
                continue
            if self.state.is_uploaded(f.handle):
                continue
            pending.append(f)

        if not pending:
            self.state.log("DEBUG", f"idle: {len(files)} on card, none new")
            return

        self.state.log("INFO", f"found {len(pending)} new file(s)")

        # 3. Process each
        for f in pending:
            if self._stop.is_set():
                return
            self._process_one(f)

    def _process_one(self, f: CameraFile) -> None:
        # 3a. Download from camera
        self.state.log("INFO", f"download {f.name} ({f.size/1024:.1f} KB)")
        try:
            local = self.camera.download(f)
        except CameraError as e:
            self.state.log("ERROR", f"download {f.name} failed: {e}")
            return

        # 3b. Determine remote subdir by date (e.g. 2025/2025-10-15)
        subdir = ""
        if f.mtime:
            subdir = time.strftime("%Y/%Y-%m-%d", time.gmtime(f.mtime))
        # If mtime is 0, leave subdir empty and rclone will dump to root.
        # Some cameras return a real mtime via gphoto2 --list-files; the
        # standard CLI doesn't, so we set 0. As a fallback, we could
        # call --get-file-info for each file but that doubles the
        # round-trips; we accept the flat layout for now.

        # 3c. Upload
        ok, msg = self.uploader.upload(local, subdir)
        if not ok:
            self.state.log("ERROR", f"upload {f.name} failed: {msg}")
            os.remove(local)
            return
        self.state.log("INFO", f"uploaded {f.name}: {msg}")

        # 3d. Mark and clean up
        self.state.mark_uploaded(f.handle)
        os.remove(local)

        # 3e. Optionally delete from camera
        if self.cfg.camera.delete_after_upload:
            try:
                self.camera.delete(f)
                self.state.log("INFO", f"deleted from camera: {f.name}")
            except CameraError as e:
                self.state.log("WARNING", f"delete {f.name} failed: {e}")
