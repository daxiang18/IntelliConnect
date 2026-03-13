#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${INPUT_SMOKE_BASE_URL:-http://127.0.0.1:18080}"
TOKEN="${INPUT_SMOKE_TOKEN:-}"
USERNAME="${INPUT_SMOKE_USERNAME:-admin}"
PASSWORD="${INPUT_SMOKE_PASSWORD:-123456}"
SOURCE_TYPE="${INPUT_SMOKE_SOURCE_TYPE:-smoke}"
SOURCE_ACCOUNT_ID="${INPUT_SMOKE_SOURCE_ACCOUNT_ID:-smoke-cli}"
SESSION_ID="${INPUT_SMOKE_SESSION_ID:-smoke-session}"
SENDER_ID="${INPUT_SMOKE_SENDER_ID:-smoke-cli}"
CONTENT_TYPE="${INPUT_SMOKE_CONTENT_TYPE:-url}"
RAW_CONTENT="${INPUT_SMOKE_RAW_CONTENT:-https://example.com/article}"
NORMALIZED_CONTENT="${INPUT_SMOKE_NORMALIZED_CONTENT:-}"
SYNC_TARGETS="${INPUT_SMOKE_SYNC_TARGETS:-}"
DEDUPE_KEY="${INPUT_SMOKE_DEDUPE_KEY:-smoke-$(date +%s)}"
POLL_COUNT="${INPUT_SMOKE_POLL_COUNT:-10}"
POLL_INTERVAL="${INPUT_SMOKE_POLL_INTERVAL:-2}"
WAIT_FOR_SYNC="${INPUT_SMOKE_WAIT_FOR_SYNC:-false}"
EXPECTED_SYNC_STATUS="${INPUT_SMOKE_EXPECTED_SYNC_STATUS:-synced}"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required" >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required" >&2
  exit 1
fi

login_if_needed() {
  if [[ -n "$TOKEN" ]]; then
    return
  fi

  if [[ -z "$USERNAME" || -z "$PASSWORD" ]]; then
    echo "Set INPUT_SMOKE_TOKEN or override INPUT_SMOKE_USERNAME and INPUT_SMOKE_PASSWORD" >&2
    exit 1
  fi

  local login_payload
  login_payload="$(python3 - <<'PY'
import json, os
print(json.dumps({
    "username": os.environ["USERNAME"],
    "password": os.environ["PASSWORD"],
}, ensure_ascii=False))
PY
)"

  local login_response
  login_response="$(curl --silent --show-error --fail \
    -H 'Content-Type: application/json' \
    -X POST "${BASE_URL}/api/v2/login" \
    -d "${login_payload}")"

  TOKEN="$(LOGIN_RESPONSE="${login_response}" python3 - <<'PY'
import json, os, sys
payload = json.loads(os.environ["LOGIN_RESPONSE"])
if not payload.get("success"):
    print(json.dumps(payload, ensure_ascii=False), file=sys.stderr)
    raise SystemExit(1)
data = payload.get("data")
if not data:
    print(json.dumps(payload, ensure_ascii=False), file=sys.stderr)
    raise SystemExit(1)
print(data)
PY
)"
}

create_message() {
  local create_payload
  create_payload="$(python3 - <<'PY'
import json, os
payload = {
    "sourceType": os.environ["SOURCE_TYPE"],
    "sourceAccountId": os.environ["SOURCE_ACCOUNT_ID"],
    "sessionId": os.environ["SESSION_ID"],
    "senderId": os.environ["SENDER_ID"],
    "contentType": os.environ["CONTENT_TYPE"],
    "rawContent": os.environ["RAW_CONTENT"],
    "dedupeKey": os.environ["DEDUPE_KEY"],
}
normalized = os.environ.get("NORMALIZED_CONTENT", "")
if normalized:
    payload["normalizedContent"] = normalized
sync_targets = os.environ.get("SYNC_TARGETS", "")
if sync_targets:
    payload["syncTargets"] = sync_targets
print(json.dumps(payload, ensure_ascii=False))
PY
)"

  curl --silent --show-error --fail \
    -H "Authorization: ${TOKEN}" \
    -H 'Content-Type: application/json' \
    -X POST "${BASE_URL}/api/v2/input/messages" \
    -d "${create_payload}"
}

extract_message_id() {
  CREATE_RESPONSE="$1" python3 - <<'PY'
import json, os, sys
payload = json.loads(os.environ["CREATE_RESPONSE"])
if not payload.get("success"):
    print(json.dumps(payload, ensure_ascii=False), file=sys.stderr)
    raise SystemExit(1)
data = payload.get("data") or {}
message_id = data.get("id")
if message_id is None:
    print(json.dumps(payload, ensure_ascii=False), file=sys.stderr)
    raise SystemExit(1)
print(message_id)
PY
}

process_message() {
  local message_id="$1"
  curl --silent --show-error --fail \
    -H "Authorization: ${TOKEN}" \
    -X POST "${BASE_URL}/api/v2/input/messages/${message_id}/process"
}

poll_message() {
  local attempt=1
  while (( attempt <= POLL_COUNT )); do
    local response
    response="$(curl --silent --show-error --fail \
      -H "Authorization: ${TOKEN}" \
      "${BASE_URL}/api/v2/input/messages/by-dedupe?dedupeKey=${DEDUPE_KEY}")"

    local parsed
    parsed="$(DETAIL_RESPONSE="${response}" python3 - <<'PY'
import json, os
payload = json.loads(os.environ["DETAIL_RESPONSE"])
data = payload.get("data") or {}
print(f"{data.get('status', '')}\t{data.get('syncStatus', '')}")
PY
)"

    local status="${parsed%%$'\t'*}"
    local sync_status="${parsed#*$'\t'}"

    if [[ "${status}" == "failed" ]]; then
      echo "${response}" | python3 -m json.tool
      return 1
    fi

    if [[ "${status}" == "ingested" && "${WAIT_FOR_SYNC}" != "true" ]]; then
      echo "${response}" | python3 -m json.tool
      return 0
    fi

    if [[ "${status}" == "ingested" && "${WAIT_FOR_SYNC}" == "true" ]]; then
      if [[ "${sync_status}" == "${EXPECTED_SYNC_STATUS}" ]]; then
        echo "${response}" | python3 -m json.tool
        return 0
      fi
      if [[ "${sync_status}" == "failed" ]]; then
        echo "${response}" | python3 -m json.tool
        return 1
      fi
    fi

    sleep "${POLL_INTERVAL}"
    attempt=$((attempt + 1))
  done

  if [[ "${WAIT_FOR_SYNC}" == "true" ]]; then
    echo "Timed out waiting for syncStatus=${EXPECTED_SYNC_STATUS} for dedupeKey=${DEDUPE_KEY}" >&2
  else
    echo "Timed out waiting for ingested status for dedupeKey=${DEDUPE_KEY}" >&2
  fi
  return 1
}

export USERNAME PASSWORD SOURCE_TYPE SOURCE_ACCOUNT_ID SESSION_ID SENDER_ID CONTENT_TYPE RAW_CONTENT
export NORMALIZED_CONTENT SYNC_TARGETS DEDUPE_KEY

login_if_needed
create_response="$(create_message)"
message_id="$(extract_message_id "${create_response}")"
process_message "${message_id}" >/dev/null
poll_message
