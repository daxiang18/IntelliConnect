#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${DOMAIN_CHECK_BASE_URL:-http://127.0.0.1:8080}"
EXPECT_HUB_ENABLED="${EXPECT_HUB_ENABLED:-}"
EXPECT_HUB_DEFAULT_ENTRY="${EXPECT_HUB_DEFAULT_ENTRY:-}"
EXPECT_HUB_MENU_GROUP="${EXPECT_HUB_MENU_GROUP:-}"
EXPECT_IOT_ENABLED="${EXPECT_IOT_ENABLED:-}"
EXPECT_IOT_DEFAULT_ENTRY="${EXPECT_IOT_DEFAULT_ENTRY:-}"
EXPECT_IOT_MENU_GROUP="${EXPECT_IOT_MENU_GROUP:-}"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required" >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required" >&2
  exit 1
fi

response="$(curl --silent --show-error --fail "${BASE_URL}/api/v2/domain-config")"

DOMAIN_RESPONSE="${response}" \
EXPECT_HUB_ENABLED="${EXPECT_HUB_ENABLED}" \
EXPECT_HUB_DEFAULT_ENTRY="${EXPECT_HUB_DEFAULT_ENTRY}" \
EXPECT_HUB_MENU_GROUP="${EXPECT_HUB_MENU_GROUP}" \
EXPECT_IOT_ENABLED="${EXPECT_IOT_ENABLED}" \
EXPECT_IOT_DEFAULT_ENTRY="${EXPECT_IOT_DEFAULT_ENTRY}" \
EXPECT_IOT_MENU_GROUP="${EXPECT_IOT_MENU_GROUP}" \
python3 - <<'PY'
import json
import os
import sys

payload = json.loads(os.environ["DOMAIN_RESPONSE"])
if not payload.get("success"):
    print(json.dumps(payload, ensure_ascii=False), file=sys.stderr)
    raise SystemExit(1)

data = payload.get("data") or {}
expected = {
    ("hub", "enabled"): os.environ.get("EXPECT_HUB_ENABLED", ""),
    ("hub", "defaultEntry"): os.environ.get("EXPECT_HUB_DEFAULT_ENTRY", ""),
    ("hub", "menuGroup"): os.environ.get("EXPECT_HUB_MENU_GROUP", ""),
    ("iot", "enabled"): os.environ.get("EXPECT_IOT_ENABLED", ""),
    ("iot", "defaultEntry"): os.environ.get("EXPECT_IOT_DEFAULT_ENTRY", ""),
    ("iot", "menuGroup"): os.environ.get("EXPECT_IOT_MENU_GROUP", ""),
}

for (section, key), expected_value in expected.items():
    if not expected_value:
        continue
    actual_value = data.get(section, {}).get(key)
    if isinstance(actual_value, bool):
        actual_value = "true" if actual_value else "false"
    else:
        actual_value = "" if actual_value is None else str(actual_value)
    if actual_value != expected_value:
        print(
            f"Expected {section}.{key}={expected_value}, got {actual_value}",
            file=sys.stderr,
        )
        raise SystemExit(1)

print(json.dumps(payload, ensure_ascii=False, indent=2))
PY
