#!/usr/bin/env python3
"""Create local fixture credentials without printing the bearer token or replacing existing files."""
from pathlib import Path
import hashlib
import os
import secrets

root = Path(__file__).resolve().parents[2]
directory = root / "infra/observability/.secrets"
directory.mkdir(mode=0o700, exist_ok=False)
os.chmod(directory, 0o700)
token = secrets.token_hex(32)
# Directory traversal is owner-only. Inside the read-only file bind mount Prometheus can read it.
with (directory / "metrics-token").open("x") as stream:
    stream.write(token)
os.chmod(directory / "metrics-token", 0o644)
with (directory / "application.env").open("x") as stream:
    stream.write("THISWAY_METRICS_TOKEN_SHA256=" + hashlib.sha256(token.encode("ascii")).hexdigest() + "\n")
os.chmod(directory / "application.env", 0o600)
print("Created ignored .secrets directory. Load application.env when starting the local backend.")
