"""
Configuration loading.

YAML file with sensible defaults; missing fields are auto-filled.
"""

import os
import yaml
from dataclasses import dataclass, field, asdict
from typing import Optional, List


@dataclass
class CameraConfig:
    # gphoto2 port. "" = auto-detect, "usb:" or "ptpip:192.168.1.1" for explicit.
    port: str = ""
    # When True, try ptpip on host first, then USB, then auto.
    prefer_ptpip_host: str = "192.168.1.1"
    # When True, allow USB (gphoto2 will require permission popup if connected).
    allow_usb: bool = True
    # How many seconds between polls. 5 is fine; Z30 stays awake.
    poll_interval_sec: int = 5
    # If True, delete the file from the camera after successful upload.
    delete_after_upload: bool = False
    # Only transfer these filetypes (empty = all).
    include_ext: List[str] = field(default_factory=lambda: ["jpg", "jpeg", "nef", "mp4", "mov"])


@dataclass
class NasConfig:
    # rclone remote name (e.g. "nas:Photos/2025") OR a path
    # (e.g. "/mnt/nas/Photos"). See rclone setup below.
    rclone_remote: str = ""
    rclone_subdir: str = "/CamRelay"
    # Optional fall-back: direct mount path (e.g. via /etc/fstab cifs mount).
    direct_path: str = ""
    # rclone bandwidth limit, e.g. "10M". Empty = unlimited.
    bandwidth_limit: str = ""


@dataclass
class WebConfig:
    host: str = "0.0.0.0"
    port: int = 8080
    # If non-empty, require this password (HTTP basic) to view.
    password: str = ""


@dataclass
class Config:
    camera: CameraConfig = field(default_factory=CameraConfig)
    nas: NasConfig = field(default_factory=NasConfig)
    web: WebConfig = field(default_factory=WebConfig)
    # Where downloaded files are staged before upload.
    staging_dir: str = "/var/lib/camrelay/staging"
    # Where the persistent state DB lives.
    state_db: str = "/var/lib/camrelay/state.json"
    # Log level: DEBUG / INFO / WARNING.
    log_level: str = "INFO"

    @classmethod
    def load(cls, path: str) -> "Config":
        if not os.path.exists(path):
            # Write a default config so the user has a template to edit.
            os.makedirs(os.path.dirname(path), exist_ok=True)
            cfg = cls()
            cfg.save(path)
            return cfg
        with open(path, "r", encoding="utf-8") as f:
            raw = yaml.safe_load(f) or {}
        return cls.from_dict(raw)

    @classmethod
    def from_dict(cls, d: dict) -> "Config":
        cam = CameraConfig(**(d.get("camera") or {}))
        nas = NasConfig(**(d.get("nas") or {}))
        web = WebConfig(**(d.get("web") or {}))
        return cls(
            camera=cam, nas=nas, web=web,
            staging_dir=d.get("staging_dir", "/var/lib/camrelay/staging"),
            state_db=d.get("state_db", "/var/lib/camrelay/state.json"),
            log_level=d.get("log_level", "INFO"),
        )

    def save(self, path: str) -> None:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            yaml.safe_dump(asdict(self), f, default_flow_style=False, sort_keys=False)


# Sane defaults for very common setups
DEFAULT_CONFIG_PATH = os.environ.get(
    "CAMRELAY_CONFIG", "/etc/camrelay/config.yaml"
)
