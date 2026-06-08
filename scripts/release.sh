#!/usr/bin/env bash
#
# 로컬 릴리스 — 이 서버에서 서명 AAB를 빌드해 Play 내부 트랙에 업로드한다.
# GitHub Actions 없이 warm Gradle 데몬 + 로컬 캐시로 빠르게 동작.
#
# 사용: ./scripts/release.sh [versionName]
# 사전: scripts/release.env (키스토어 자격), 서비스계정 JSON. docs/RELEASING.md 참고.
#
set -euo pipefail
cd "$(dirname "$0")/.."

ENV_FILE="scripts/release.env"
if [ ! -f "$ENV_FILE" ]; then
  echo "✗ $ENV_FILE 없음 — KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD 설정 필요(docs/RELEASING.md)"
  exit 1
fi
set -a; source "$ENV_FILE"; set +a

JSON_KEY="${PLAY_JSON_KEY:-scripts/play-service-account.json}"
if [ ! -f "$JSON_KEY" ]; then
  echo "✗ Play 서비스계정 JSON 없음: $JSON_KEY (PLAY_JSON_KEY로 경로 지정 가능)"
  exit 1
fi
if [ ! -x "scripts/.venv/bin/python" ]; then
  echo "✗ venv 없음 — python3 -m venv scripts/.venv && scripts/.venv/bin/pip install google-api-python-client google-auth"
  exit 1
fi

# Play는 단조 증가 versionCode를 요구 — 분 단위 타임스탬프(2020-01-01 기준)로 충돌 없이 증가.
# (git 커밋 수는 수동/재시도 업로드와 어긋나 충돌할 수 있어 시간 기반으로 도출.)
VERSION_CODE="${VERSION_CODE:-$(( ($(date +%s) - 1577836800) / 60 ))}"
VERSION_NAME="${1:-${VERSION_NAME:-1.0}}"
export KEYSTORE_PATH KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD VERSION_CODE VERSION_NAME

echo "▸ 서명 AAB 빌드 (versionCode=$VERSION_CODE, versionName=$VERSION_NAME)"
./gradlew :androidApp:bundleRelease

AAB="androidApp/build/outputs/bundle/release/androidApp-release.aab"
[ -f "$AAB" ] || { echo "✗ AAB 없음: $AAB"; exit 1; }

echo "▸ Play 내부 트랙 업로드"
scripts/.venv/bin/python scripts/play_upload.py \
  --aab "$AAB" \
  --package today.superb.jvl \
  --track internal \
  --json-key "$JSON_KEY" \
  --release-name "$VERSION_NAME"

echo "✓ 릴리스 완료 — Play Console 내부 테스트 트랙 확인"
