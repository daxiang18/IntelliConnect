#!/usr/bin/env bash
set -euo pipefail

FEISHU_ENABLED_RAW="${FEISHU_ENABLED:-false}"
FEISHU_APP_ID="${FEISHU_APP_ID:-}"
FEISHU_APP_SECRET="${FEISHU_APP_SECRET:-}"
FEISHU_API_BASE_URL="${FEISHU_API_BASE_URL:-https://open.feishu.cn/open-apis}"
FEISHU_SYNC_MODE_RAW="${FEISHU_SYNC_MODE:-doc}"
FEISHU_FOLDER_TOKEN="${FEISHU_FOLDER_TOKEN:-}"
FEISHU_WIKI_SPACE_ID="${FEISHU_WIKI_SPACE_ID:-}"
FEISHU_WIKI_PARENT_NODE_TOKEN="${FEISHU_WIKI_PARENT_NODE_TOKEN:-}"
FEISHU_TITLE_PREFIX="${FEISHU_TITLE_PREFIX:-IntelliConnect}"
FEISHU_WEB_BASE_URL="${FEISHU_WEB_BASE_URL:-}"

declare -a ERRORS=()
declare -a NOTES=()

is_blank() {
  local value="${1:-}"
  [[ -z "${value//[[:space:]]/}" ]]
}

normalize_bool() {
  local raw="${1:-false}"
  raw="${raw,,}"
  case "${raw}" in
    true | 1 | yes | y)
      printf 'true'
      ;;
    false | 0 | no | n | '')
      printf 'false'
      ;;
    *)
      ERRORS+=("FEISHU_ENABLED must be one of: true/false/1/0/yes/no")
      printf 'false'
      ;;
  esac
}

normalize_mode() {
  local raw="${1:-doc}"
  raw="${raw,,}"
  if is_blank "${raw}"; then
    raw="doc"
  fi
  case "${raw}" in
    doc | wiki)
      printf '%s' "${raw}"
      ;;
    *)
      ERRORS+=("FEISHU_SYNC_MODE must be 'doc' or 'wiki'")
      printf 'doc'
      ;;
  esac
}

require_non_blank() {
  local variable_name="$1"
  local description="$2"
  local value="${!variable_name:-}"
  if is_blank "${value}"; then
    ERRORS+=("${description} (${variable_name}) is required")
  fi
}

describe_value() {
  local value="${1:-}"
  if is_blank "${value}"; then
    printf 'not set'
  else
    printf 'configured (%d chars)' "${#value}"
  fi
}

enabled="$(normalize_bool "${FEISHU_ENABLED_RAW}")"
mode="$(normalize_mode "${FEISHU_SYNC_MODE_RAW}")"

if [[ "${enabled}" != "true" ]]; then
  ERRORS+=("FEISHU_ENABLED must be true before running Feishu live validation")
fi

require_non_blank FEISHU_APP_ID "Feishu app id"
require_non_blank FEISHU_APP_SECRET "Feishu app secret"
require_non_blank FEISHU_API_BASE_URL "Feishu api base url"

if [[ "${mode}" == "wiki" ]]; then
  require_non_blank FEISHU_WIKI_SPACE_ID "Feishu wiki space id"
  require_non_blank FEISHU_WIKI_PARENT_NODE_TOKEN "Feishu wiki parent node token"
fi

if is_blank "${FEISHU_FOLDER_TOKEN}"; then
  NOTES+=("FEISHU_FOLDER_TOKEN is not set; document placement will follow the app's default location.")
fi

if is_blank "${FEISHU_WEB_BASE_URL}"; then
  NOTES+=("FEISHU_WEB_BASE_URL is not set; externalReferencesJson will not include documentUrl/wikiUrl.")
fi

if ((${#ERRORS[@]} > 0)); then
  echo "Feishu config is not ready:" >&2
  for error in "${ERRORS[@]}"; do
    echo "  - ${error}" >&2
  done
  if ((${#NOTES[@]} > 0)); then
    echo >&2
    echo "Notes:" >&2
    for note in "${NOTES[@]}"; do
      echo "  - ${note}" >&2
    done
  fi
  exit 1
fi

cat <<EOF
Feishu config looks ready.
  enabled: ${enabled}
  mode: ${mode}
  apiBaseUrl: ${FEISHU_API_BASE_URL}
  appId: $(describe_value "${FEISHU_APP_ID}")
  appSecret: $(describe_value "${FEISHU_APP_SECRET}")
  folderToken: $(describe_value "${FEISHU_FOLDER_TOKEN}")
  wikiSpaceId: $(describe_value "${FEISHU_WIKI_SPACE_ID}")
  wikiParentNodeToken: $(describe_value "${FEISHU_WIKI_PARENT_NODE_TOKEN}")
  titlePrefix: ${FEISHU_TITLE_PREFIX}
  webBaseUrl: ${FEISHU_WEB_BASE_URL:-not set}
EOF

if ((${#NOTES[@]} > 0)); then
  echo
  echo "Notes:"
  for note in "${NOTES[@]}"; do
    echo "  - ${note}"
  done
fi

echo
echo "Suggested smoke validation command:"
cat <<EOF
  INPUT_SMOKE_SYNC_TARGETS=feishu \\
  INPUT_SMOKE_WAIT_FOR_SYNC=true \\
  INPUT_SMOKE_EXPECTED_SYNC_STATUS=synced \\
  bash ./scripts/smoke-input-flow.sh
EOF

echo
echo "Start the backend with the same FEISHU_* variables before running the smoke command."
