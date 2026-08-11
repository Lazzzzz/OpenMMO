from __future__ import annotations

import gzip
import hmac
import json
import os
import re
import subprocess
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse


TOKEN = os.environ["OPENMMO_OPS_TOKEN"]
BACKUP_DIR = Path("/backups")
BACKUP_DIR.mkdir(parents=True, exist_ok=True)
SERVICES = {
    "login": os.getenv("OPENMMO_LOGIN_CONTAINER", "openmmo-login-server-1"),
    "game": os.getenv("OPENMMO_GAME_CONTAINER", "openmmo-game-server-1"),
    "feed": os.getenv("OPENMMO_FEED_CONTAINER", "openmmo-feed-1"),
}
DATABASES = {
    "login": (
        os.getenv("OPENMMO_LOGIN_DB_CONTAINER", "openmmo-login-db-1"),
        os.environ["LOGIN_DB_USER"],
        os.environ["LOGIN_DB_NAME"],
    ),
    "game": (
        os.getenv("OPENMMO_GAME_DB_CONTAINER", "openmmo-game-db-1"),
        os.environ["GAME_DB_USER"],
        os.environ["GAME_DB_NAME"],
    ),
}
SAFE_BACKUP = re.compile(r"^[a-z]+-\d{8}T\d{6}Z\.sql\.gz$")


def run(*args: str, timeout: int = 30) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(args, check=True, capture_output=True, timeout=timeout)


class Handler(BaseHTTPRequestHandler):
    server_version = "OpenMMOOps/1"

    def do_GET(self) -> None:
        if not self.authorized():
            return
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.json({"ok": True})
        elif parsed.path == "/services":
            self.services()
        elif parsed.path == "/logs":
            self.logs(parse_qs(parsed.query))
        elif parsed.path == "/backups":
            self.backups()
        elif parsed.path.startswith("/backups/"):
            self.download_backup(parsed.path.removeprefix("/backups/"))
        else:
            self.error(HTTPStatus.NOT_FOUND, "Not found")

    def do_POST(self) -> None:
        if not self.authorized():
            return
        parsed = urlparse(self.path)
        if parsed.path == "/restart":
            self.restart(parse_qs(parsed.query))
        elif parsed.path == "/maintenance":
            self.maintenance(parse_qs(parsed.query))
        elif parsed.path == "/backups":
            self.create_backup()
        else:
            self.error(HTTPStatus.NOT_FOUND, "Not found")

    def authorized(self) -> bool:
        supplied = self.headers.get("Authorization", "").removeprefix("Bearer ")
        if hmac.compare_digest(supplied, TOKEN):
            return True
        self.error(HTTPStatus.UNAUTHORIZED, "Unauthorized")
        return False

    def services(self) -> None:
        states = {}
        for key, container in SERVICES.items():
            try:
                result = run("docker", "inspect", "--format", "{{json .State}}", container)
                state = json.loads(result.stdout.decode())
                states[key] = {
                    "running": bool(state.get("Running")),
                    "started_at": state.get("StartedAt"),
                    "status": state.get("Status", "unknown"),
                }
            except (subprocess.SubprocessError, json.JSONDecodeError):
                states[key] = {"running": False, "started_at": None, "status": "missing"}
        self.json({"services": states})

    def logs(self, query: dict[str, list[str]]) -> None:
        service = query.get("service", [""])[0]
        if service not in SERVICES:
            self.error(HTTPStatus.UNPROCESSABLE_ENTITY, "Unknown service")
            return
        try:
            tail = max(10, min(500, int(query.get("tail", ["150"])[0])))
            result = run("docker", "logs", "--tail", str(tail), SERVICES[service])
            text = (result.stdout + result.stderr).decode(errors="replace")[-120_000:]
            self.json({"service": service, "lines": text})
        except (ValueError, subprocess.SubprocessError) as error:
            self.error(HTTPStatus.BAD_GATEWAY, str(error))

    def restart(self, query: dict[str, list[str]]) -> None:
        service = query.get("service", [""])[0]
        if service not in SERVICES:
            self.error(HTTPStatus.UNPROCESSABLE_ENTITY, "Unknown service")
            return
        try:
            run("docker", "restart", "--time", "20", SERVICES[service], timeout=45)
            self.json({"ok": True, "service": service})
        except subprocess.SubprocessError as error:
            self.error(HTTPStatus.BAD_GATEWAY, str(error))

    def maintenance(self, query: dict[str, list[str]]) -> None:
        enabled = query.get("enabled", [""])[0] == "1"
        command = "stop" if enabled else "start"
        order = ("login", "game") if enabled else ("game", "login")
        try:
            for service in order:
                run("docker", command, SERVICES[service], timeout=45)
            self.json({"ok": True, "maintenance": enabled})
        except subprocess.SubprocessError as error:
            self.error(HTTPStatus.BAD_GATEWAY, str(error))

    def create_backup(self) -> None:
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        created = []
        try:
            for key, (container, user, database) in DATABASES.items():
                result = run(
                    "docker", "exec", container, "pg_dump", "--clean", "--if-exists",
                    "--no-owner", "--no-privileges", "-U", user, database, timeout=120,
                )
                path = BACKUP_DIR / f"{key}-{stamp}.sql.gz"
                with gzip.open(path, "wb", compresslevel=6) as output:
                    output.write(result.stdout)
                created.append(path.name)
            self.prune_backups()
            self.json({"ok": True, "files": created}, HTTPStatus.CREATED)
        except (OSError, subprocess.SubprocessError) as error:
            self.error(HTTPStatus.BAD_GATEWAY, str(error))

    def prune_backups(self) -> None:
        keep = max(2, int(os.getenv("OPENMMO_BACKUP_RETENTION", "14")))
        for key in DATABASES:
            files = sorted(BACKUP_DIR.glob(f"{key}-*.sql.gz"), reverse=True)
            for path in files[keep:]:
                path.unlink(missing_ok=True)

    def backups(self) -> None:
        files = [
            {
                "name": path.name,
                "size": path.stat().st_size,
                "created_at": datetime.fromtimestamp(path.stat().st_mtime, timezone.utc).isoformat(),
            }
            for path in sorted(BACKUP_DIR.glob("*.sql.gz"), reverse=True)
            if SAFE_BACKUP.fullmatch(path.name)
        ]
        self.json({"backups": files})

    def download_backup(self, name: str) -> None:
        if not SAFE_BACKUP.fullmatch(name):
            self.error(HTTPStatus.NOT_FOUND, "Not found")
            return
        path = BACKUP_DIR / name
        if not path.is_file():
            self.error(HTTPStatus.NOT_FOUND, "Not found")
            return
        body = path.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "application/gzip")
        self.send_header("Content-Disposition", f'attachment; filename="{name}"')
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def json(self, payload: object, status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(payload, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def error(self, status: HTTPStatus, message: str) -> None:
        self.json({"error": message}, status)

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"ops-agent: {fmt % args}", flush=True)


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8081), Handler).serve_forever()
