"""
CLI helpers for ad-hoc operations.

  python -m relay.cli doctor        # check config + camera + NAS
  python -m relay.cli list          # list files on camera
  python -m relay.cli pull NAME     # download a specific file
  python -m relay.cli shell         # drop into a Python REPL with cfg loaded
"""

import json
import os
import sys
import time

from .config import Config, DEFAULT_CONFIG_PATH
from .camera import Camera
from .nas import NasUploader


def doctor(cfg: Config) -> int:
    print("=== CamRelay doctor ===")
    print(f"config: {DEFAULT_CONFIG_PATH}")
    print(f"staging: {cfg.staging_dir}")
    print(f"state:   {cfg.state_db}")
    print()
    print("--- camera ---")
    cam = Camera(cfg.camera, cfg.staging_dir)
    print(f"  gphoto2: {cam._gphoto2}")
    summary = cam.summary()
    print(f"  detect:  {summary}")
    if "usb:" in (summary or "") or "ptpip:" in (summary or ""):
        print("  status:  ✓ camera found")
    else:
        print("  status:  ✗ no camera — connect it or fix network")
    print()
    print("--- NAS ---")
    up = NasUploader(cfg.nas)
    if cfg.nas.rclone_remote:
        print(f"  backend:  rclone -> {cfg.nas.rclone_remote}/{cfg.nas.rclone_subdir}")
    elif cfg.nas.direct_path:
        print(f"  backend:  direct copy -> {cfg.nas.direct_path}")
    else:
        print("  backend:  ✗ not configured")
    ok, msg = up.test()
    print(f"  test:     {'✓' if ok else '✗'} {msg}")
    print()
    return 0 if (summary and ("usb:" in summary or "ptpip:" in summary) and ok) else 1


def list_files(cfg: Config) -> int:
    cam = Camera(cfg.camera, cfg.staging_dir)
    files = cam.list_files()
    print(f"=== {len(files)} files on camera ===")
    for f in files[:200]:
        print(f"  {f.handle:50s} {f.size:>10d}  {f.name}")
    if len(files) > 200:
        print(f"  ... {len(files) - 200} more")
    return 0


def pull(cfg: Config, name: str) -> int:
    cam = Camera(cfg.camera, cfg.staging_dir)
    files = cam.list_files()
    target = next((f for f in files if f.name == name), None)
    if not target:
        print(f"not found on camera: {name}", file=sys.stderr); return 1
    local = cam.download(target)
    print(f"downloaded -> {local}")
    return 0


def main() -> int:
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", default=DEFAULT_CONFIG_PATH)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("doctor")
    sub.add_parser("list")
    p_pull = sub.add_parser("pull")
    p_pull.add_argument("name")
    sub.add_parser("shell")
    args = ap.parse_args()

    cfg = Config.load(args.config)
    if args.cmd == "doctor": return doctor(cfg)
    if args.cmd == "list":   return list_files(cfg)
    if args.cmd == "pull":   return pull(cfg, args.name)
    if args.cmd == "shell":
        import code
        ns = {"cfg": cfg, "Camera": Camera, "NasUploader": NasUploader}
        code.interact(local=ns)
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
