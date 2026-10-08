"""
Entry point.

  python -m relay.main            # foreground
  python -m relay.main --check    # one-shot: print config and exit
  systemctl start camrelay        # via systemd
"""

import argparse
import logging
import os
import signal
import sys
import threading
import time

from .config import Config, DEFAULT_CONFIG_PATH
from .state import State
from .sync import Sync
from .web import make_app


def setup_logging(level: str = "INFO") -> None:
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s [%(name)s] %(levelname)s %(message)s",
        datefmt="%H:%M:%S",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default=DEFAULT_CONFIG_PATH,
                        help="Path to YAML config file")
    parser.add_argument("--check", action="store_true",
                        help="Validate config and exit")
    args = parser.parse_args()

    cfg = Config.load(args.config)
    setup_logging(cfg.log_level)
    log = logging.getLogger("camrelay")

    if args.check:
        print("Config OK at", args.config)
        print(cfg)
        return 0

    # State
    state = State(cfg.state_db)

    # Sync engine
    sync = Sync(cfg, state)
    sync.start()

    # Web UI
    try:
        app = make_app(cfg, sync, state)
    except Exception as e:
        log.exception("web ui failed to start: %s", e)
        app = None

    if app is not None:
        def serve():
            # Flask built-in server; OK for LAN usage. For real deploy
            # put gunicorn or nginx in front.
            app.run(host=cfg.web.host, port=cfg.web.port, debug=False, use_reloader=False)
        t = threading.Thread(target=serve, name="camrelay-web", daemon=True)
        t.start()
        log.info("web ui on http://%s:%d", cfg.web.host, cfg.web.port)

    # Block main thread; signal handler stops sync on SIGTERM/SIGINT.
    stop_event = threading.Event()

    def handle_signal(signum, frame):
        log.info("signal %d received, stopping", signum)
        stop_event.set()

    signal.signal(signal.SIGTERM, handle_signal)
    signal.signal(signal.SIGINT, handle_signal)

    while not stop_event.is_set():
        time.sleep(1)

    sync.stop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
