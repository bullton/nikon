"""
NAS uploader.

Two backends are supported:

1. rclone  (recommended)  — one tool, every protocol, robust
   rclone sync / copy, retries, bandwidth limiting, etc.
   Install: `apt install rclone` then `rclone config` to add your NAS.

2. direct_path  (fallback) — files are already mounted locally
   (e.g. /mnt/nas via /etc/fstab cifs mount). We just `cp` into it.

This avoids pulling in a dozen different Python protocol libraries
and gives the user a familiar tool (rclone) to manage destinations.
"""

import logging
import os
import shutil
import subprocess
import threading
import time
from typing import Optional

from .config import NasConfig

log = logging.getLogger("camrelay.nas")


class NasUploader:
    def __init__(self, cfg: NasConfig):
        self.cfg = cfg
        self._lock = threading.Lock()
        self._rclone = shutil.which("rclone")

    def test(self) -> tuple[bool, str]:
        """Return (ok, message)."""
        if self.cfg.rclone_remote:
            if not self._rclone:
                return False, "rclone binary not found"
            try:
                r = subprocess.run(
                    [self._rclone, "lsd", f"{self.cfg.rclone_remote}"],
                    capture_output=True, text=True, timeout=20
                )
                if r.returncode == 0:
                    return True, f"rclone ok: {self.cfg.rclone_remote}"
                return False, f"rclone error: {r.stderr.strip()[:200]}"
            except subprocess.TimeoutExpired:
                return False, "rclone timeout"
            except Exception as e:
                return False, f"rclone exception: {e}"
        if self.cfg.direct_path:
            if not os.path.isdir(self.cfg.direct_path):
                return False, f"direct_path not a directory: {self.cfg.direct_path}"
            test = os.path.join(self.cfg.direct_path, ".camrelay_test")
            try:
                with open(test, "w") as f:
                    f.write("ok")
                os.remove(test)
                return True, f"direct_path writable: {self.cfg.direct_path}"
            except Exception as e:
                return False, f"direct_path not writable: {e}"
        return False, "neither rclone_remote nor direct_path configured"

    def upload(self, local_path: str, remote_subdir: str = "") -> tuple[bool, str]:
        """Upload a local file. Returns (ok, message)."""
        if not os.path.exists(local_path):
            return False, f"local file missing: {local_path}"
        with self._lock:
            if self.cfg.rclone_remote:
                return self._upload_rclone(local_path, remote_subdir)
            if self.cfg.direct_path:
                return self._upload_direct(local_path, remote_subdir)
            return False, "no upload backend configured"

    def _upload_rclone(self, local_path: str, remote_subdir: str) -> tuple[bool, str]:
        sub = self.cfg.rclone_subdir.strip("/")
        if remote_subdir:
            sub = (sub + "/" + remote_subdir.strip("/")).strip("/")
        remote = f"{self.cfg.rclone_remote}/{sub}".rstrip("/")
        args = [
            self._rclone, "copyto",
            local_path,  # source (file)
            f"{remote}/{os.path.basename(local_path)}",  # dest (full path)
            "--create-dest-dirs",
            "--retries", "5",
            "--retries-sleep", "10s",
            "--low-level-retries", "10",
            "--contimeout", "30s",
            "--timeout", "300s",
        ]
        if self.cfg.bandwidth_limit:
            args += ["--bwlimit", self.cfg.bandwidth_limit]
        # Don't print progress
        args += ["--quiet"]
        log.debug("$ %s", " ".join(args))
        try:
            r = subprocess.run(args, capture_output=True, text=True, timeout=1800)
            if r.returncode != 0:
                return False, f"rclone copyto failed: {r.stderr.strip()[:300]}"
            return True, f"uploaded to {remote}"
        except subprocess.TimeoutExpired:
            return False, "rclone timeout"
        except Exception as e:
            return False, f"rclone exception: {e}"

    def _upload_direct(self, local_path: str, remote_subdir: str) -> tuple[bool, str]:
        sub = self.cfg.direct_path.rstrip("/")
        if self.cfg.rclone_subdir:
            sub += "/" + self.cfg.rclone_subdir.strip("/")
        if remote_subdir:
            sub += "/" + remote_subdir.strip("/")
        os.makedirs(sub, exist_ok=True)
        dest = os.path.join(sub, os.path.basename(local_path))
        try:
            # Try a fast copy first (might be on same filesystem).
            if os.stat(local_path).st_dev == os.stat(sub).st_dev:
                shutil.copy2(local_path, dest)
            else:
                shutil.copy2(local_path, dest)
            return True, f"copied to {dest}"
        except Exception as e:
            return False, f"direct copy failed: {e}"
