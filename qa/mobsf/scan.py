"""Runs inside the pinned MobSF container. Only the JSON report goes to stdout."""
import hashlib
import json
from pathlib import Path
import sys
import time
import requests

base = "http://127.0.0.1:8000"
session = requests.Session()
for attempt in range(120):
    try:
        if session.get(base, timeout=3).status_code < 500:
            break
    except (requests.ConnectionError, requests.Timeout):
        pass
    time.sleep(1)
else:
    sys.exit("MobSF did not become ready")

secret = (Path.home() / ".MobSF" / "secret").read_bytes().strip()
session.headers["Authorization"] = hashlib.sha256(secret).hexdigest()


def post(endpoint, **kwargs):
    response = session.post(base + "/api/v1/" + endpoint, timeout=1200, **kwargs)
    response.raise_for_status()
    body = response.json()
    if "error" in body:
        raise RuntimeError(body["error"])
    return body


with open("/tmp/Breadcrumb.apk", "rb") as apk:
    upload = post("upload", files={"file": ("Breadcrumb.apk", apk, "application/octet-stream")})
payload = {"hash": upload["hash"]}
post("scan", data=payload)
report = post("report_json", data=payload)
if report.get("package_name") != "dev.breadcrumb.app":
    sys.exit("Unexpected app package in scan result")
json.dump(report, sys.stdout, indent=2)
