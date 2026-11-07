#!/usr/bin/env bash
# Usage: qa/mobsf/scan.sh /path/to/Breadcrumb.apk report.json
set -euo pipefail
apk=${1:?APK required}
report=${2:?Output JSON path required}
test -s "$apk"
scanner=opensecurity/mobile-security-framework-mobsf@sha256:35868c79cf378c2427fa98f7cd9ad5c7bcf314a342939d4381ce55668424d40b
container=$(docker run -d --network none "$scanner")
result=$(mktemp "${report}.XXXXXX")
cleanup() {
  docker rm -f "$container" >/dev/null
  rm -f "$result"
}
trap cleanup EXIT
docker cp "$apk" "$container:/tmp/Breadcrumb.apk"
docker exec -i "$container" python - < "$(dirname "$0")/scan.py" > "$result"
mv "$result" "$report"
