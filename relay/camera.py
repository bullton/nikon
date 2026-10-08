"""
Camera interface using gphoto2.

Why gphoto2 and not PTP/IP from scratch? Because gphoto2 has
20+ years of battle-tested reverse engineering for Nikon cameras.
The Z30 works out of the box with `gphoto2 --auto-detect`.

We shell out to the gphoto2 CLI for stability; you can swap in
the python-gphoto2 bindings if you prefer.
"""

import json
import logging
import os
import shutil
import subprocess
import threading
import time
from dataclasses import dataclass, asdict
from typing import List, Optional, Tuple

from .config import CameraConfig

log = logging.getLogger("camrelay.camera")


@dataclass
class CameraFile:
    handle: str            # gphoto2 handle (folder/name), stable across runs
    name: str
    folder: str
    size: int
    mtime: int
    is_jpeg: bool
    is_raw: bool
    is_video: bool

    @property
    def ext(self) -> str:
        return os.path.splitext(self.name)[1].lstrip(".").lower()

    def to_dict(self) -> dict:
        return asdict(self)

    @property
    def is_wanted(self) -> bool:
        return self.ext in ("jpg", "jpeg", "nef", "mp4", "mov", "avi", "mts")


class CameraError(RuntimeError):
    pass


class Camera:
    """Thread-safe wrapper around gphoto2 CLI."""

    def __init__(self, cfg: CameraConfig, staging_dir: str):
        self.cfg = cfg
        self.staging_dir = staging_dir
        os.makedirs(self.staging_dir, exist_ok=True)
        self._lock = threading.Lock()
        self._last_ok = 0.0
        self._gphoto2 = shutil.which("gphoto2")
        if not self._gphoto2:
            raise CameraError("gphoto2 binary not found. apt install gphoto2")

    # ---------- public ----------

    def detect(self) -> Optional[str]:
        """Return a description of the connected camera or None."""
        try:
            r = self._run(["--auto-detect"], timeout=10)
            for line in r.stdout.splitlines():
                line = line.strip()
                if line and "usb:" in line.lower() or "ptpip:" in line.lower():
                    return line
            return r.stdout.strip() or None
        except Exception:
            return None

    def list_files(self) -> List[CameraFile]:
        """List all files on the camera using gphoto2's filesystem walker."""
        with self._lock:
            args = ["--list-files"]
            if self.cfg.port:
                args += ["--port", self.cfg.port]
            # The output looks like:
            #   There is no file in folder '/'.
            #   There is 1 file in folder '/store_00010001'.
            #   #1    DSC_0001.JPG  rd  5512 KB image/jpeg
            try:
                r = self._run(args, timeout=60)
            except subprocess.TimeoutExpired:
                log.warning("gphoto2 --list-files timeout, retrying")
                return []
            except Exception as e:
                log.error("gphoto2 list failed: %s", e)
                return []

        files: List[CameraFile] = []
        current_folder = "/"
        for raw in r.stdout.splitlines():
            line = raw.strip()
            if not line:
                continue
            if "There is no file" in line:
                continue
            if "There is" in line and "file in folder" in line:
                # Extract folder between 'folder ' and "'"
                try:
                    folder = line.split("folder '", 1)[1].rstrip("'.")
                    current_folder = "/" + folder.lstrip("/")
                except Exception:
                    pass
                continue
            if line.startswith("#"):
                # Format: "#1  DSC_0001.JPG  rd  5512 KB image/jpeg"
                parts = line.split()
                if len(parts) < 3:
                    continue
                # name and flags
                name = parts[1]
                # size: "5512 KB" or "5512 KB" with extra spaces
                size = 0
                mtime = 0
                for tok in parts[2:]:
                    if tok.isdigit():
                        size = int(tok) * 1024
                        break
                # We don't get mtime from list-files; download info
                # is needed for that. Just use 0 here.
                ext = os.path.splitext(name)[1].lstrip(".").lower()
                is_jpeg = ext in ("jpg", "jpeg")
                is_raw = ext in ("nef", "nrw", "arw", "cr2", "cr3", "dng", "raf", "orf", "rw2", "pef")
                is_video = ext in ("mp4", "mov", "avi", "mts", "m4v")
                files.append(CameraFile(
                    handle=f"{current_folder}/{name}",
                    name=name, folder=current_folder, size=size, mtime=mtime,
                    is_jpeg=is_jpeg, is_raw=is_raw, is_video=is_video,
                ))
        return files

    def download(self, cam_file: CameraFile) -> str:
        """Download a camera file to the local staging dir. Returns local path."""
        with self._lock:
            local = os.path.join(self.staging_dir, cam_file.name)
            args = [
                "--get-file", f"{cam_file.folder}/{cam_file.name}",
                "--filename", local,
                "--force-overwrite",
            ]
            if self.cfg.port:
                args += ["--port", self.cfg.port]
            try:
                r = self._run(args, timeout=600)  # 10 min for big files
            except subprocess.TimeoutExpired:
                raise CameraError(f"download timeout: {cam_file.name}")
            if not os.path.exists(local) or os.path.getsize(local) == 0:
                raise CameraError(f"download failed (no file or empty): {cam_file.name}")
            actual_size = os.path.getsize(local)
            if cam_file.size and actual_size != cam_file.size:
                log.warning("size mismatch: %s expected %d got %d",
                            cam_file.name, cam_file.size, actual_size)
            return local

    def delete(self, cam_file: CameraFile) -> None:
        """Delete a file from the camera. CameraError on failure."""
        with self._lock:
            args = [
                "--delete-file", f"{cam_file.folder}/{cam_file.name}",
            ]
            if self.cfg.port:
                args += ["--port", self.cfg.port]
            self._run(args, timeout=30)

    def summary(self) -> str:
        return self.detect() or "no camera detected"

    # ---------- internal ----------

    def _run(self, args, timeout: int) -> subprocess.CompletedProcess:
        full = [self._gphoto2] + args
        log.debug("$ %s", " ".join(full))
        r = subprocess.run(
            full, capture_output=True, text=True, timeout=timeout
        )
        if r.returncode != 0:
            # gphoto2 sometimes returns 0 even on partial failure; log
            # only the tail of stderr for diagnosis.
            log.warning("gphoto2 exit=%d: %s", r.returncode,
                        (r.stderr or "").strip()[-400:])
        return r
