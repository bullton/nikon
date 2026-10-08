"""
Tiny web UI: status, recent logs, configuration viewer, control buttons.

Uses Flask because it's a single-file, dependency-light HTTP framework
that's already on most Linux distros. We disable debug mode and
serve only on the configured (default 0.0.0.0:8080) interface.
"""

import json
import logging
import os
import time
from flask import Flask, jsonify, render_template, request, abort

from .config import Config
from .sync import Sync
from .state import State

log = logging.getLogger("camrelay.web")


def make_app(cfg: Config, sync: Sync, state: State) -> Flask:
    app = Flask(__name__,
                template_folder=os.path.join(os.path.dirname(__file__), "..", "web", "templates"),
                static_folder=os.path.join(os.path.dirname(__file__), "..", "web", "static"))
    app.config["cfg"] = cfg
    app.config["sync"] = sync
    app.config["state"] = state

    def check_auth():
        if not cfg.web.password:
            return
        from flask import request
        pw = request.headers.get("X-CamRelay-Password") or request.args.get("p") or \
             request.authorization.password if request.authorization else None
        if pw != cfg.web.password:
            return abort(401)

    @app.route("/")
    def index():
        check_auth()
        return render_template("index.html",
                               status=sync.status(),
                               recent=state.recent_logs(50))

    @app.route("/api/status")
    def api_status():
        check_auth()
        return jsonify({
            "status": sync.status(),
            "config": {
                "camera": dict(cfg.camera.__dict__),
                "nas": dict(cfg.nas.__dict__),
                "staging_dir": cfg.staging_dir,
            }
        })

    @app.route("/api/logs")
    def api_logs():
        check_auth()
        n = int(request.args.get("n", 100))
        return jsonify([
            {"ts": e.ts, "level": e.level, "msg": e.msg}
            for e in state.recent_logs(n)
        ])

    @app.route("/api/test-camera", methods=["POST"])
    def api_test_camera():
        check_auth()
        return jsonify({"camera": sync.camera.summary()})

    @app.route("/api/test-nas", methods=["POST"])
    def api_test_nas():
        check_auth()
        ok, msg = sync.uploader.test()
        return jsonify({"ok": ok, "msg": msg})

    return app
