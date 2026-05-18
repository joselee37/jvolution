#!/usr/bin/env bash
# 03-verify.sh — smoke-test the deployed site. No sudo needed.
# Run as: bash script/03-verify.sh

set -uo pipefail

SITE=jvolution.superb.today
FAIL=0

check() {
  local label=$1 url=$2 expect=$3
  local code
  code=$(curl -sk -o /dev/null -w '%{http_code}' -m 8 "$url")
  if [[ "$code" == "$expect" ]]; then
    printf '  OK   %-12s %-60s → %s\n' "$label" "$url" "$code"
  else
    printf '  FAIL %-12s %-60s → %s (expected %s)\n' "$label" "$url" "$code" "$expect"
    FAIL=1
  fi
}

check_ctype() {
  local label=$1 url=$2 expect=$3
  local got
  got=$(curl -skI -m 8 "$url" | awk -F': ' 'tolower($1)=="content-type"{sub(/\r/,"",$2); print $2; exit}')
  # Compare on the leading mime token only (strip parameters like charset).
  local got_main=${got%%;*}
  got_main=${got_main%% *}
  if [[ "$got_main" == "$expect" ]]; then
    printf '  OK   %-12s %-60s → %s\n' "$label" "$url" "$got"
  else
    printf '  FAIL %-12s %-60s → %s (expected %s)\n' "$label" "$url" "$got" "$expect"
    FAIL=1
  fi
}

echo "Checking ${SITE}…"

# Runtime assets — must serve.
check "http→https" "http://${SITE}/"                     301
check "index"      "https://${SITE}/"                    200
check "html"       "https://${SITE}/Sonar%20Tamagotchi.html"  200
check "css"        "https://${SITE}/styles.css"          200
check "app"        "https://${SITE}/app.jsx"             200
check "creature"   "https://${SITE}/creature.jsx"        200
check "radar"      "https://${SITE}/radar.jsx"           200
check "battle"     "https://${SITE}/battle.jsx"          200
check "screens"    "https://${SITE}/screens.jsx"         200
check "ios"        "https://${SITE}/ios-frame.jsx"       200
check "tweaks"     "https://${SITE}/tweaks-panel.jsx"    200

# Content types — wrong types make the browser download instead of render.
check_ctype "ct-index"   "https://${SITE}/"               text/html
check_ctype "ct-css"     "https://${SITE}/styles.css"     text/css
check_ctype "ct-jsx"     "https://${SITE}/app.jsx"        text/babel

# Cache-Control on the index — bust the bad cached response.
got_cc=$(curl -skI -m 8 "https://${SITE}/" | awk -F': ' 'tolower($1)=="cache-control"{sub(/\r/,"",$2); print $2; exit}')
if [[ "$got_cc" == *"no-cache"* ]]; then
  printf '  OK   %-12s %-60s → %s\n' "ct-cache" "https://${SITE}/" "$got_cc"
else
  printf '  FAIL %-12s %-60s → %s (expected no-cache)\n' "ct-cache" "https://${SITE}/" "$got_cc"
  FAIL=1
fi

# Confirm non-runtime assets are NOT exposed.
check "no-ghost"   "https://${SITE}/ghost.jsx"           404
check "no-motive"  "https://${SITE}/모티브/"              404
check "no-claude"  "https://${SITE}/CLAUDE.md"           404
check "no-script"  "https://${SITE}/script/00-build.sh"  404
check "no-git"     "https://${SITE}/.git/HEAD"           404

echo
if [[ $FAIL -eq 0 ]]; then
  echo "All checks passed."
else
  echo "Some checks failed — http→301 holds only after 02-deploy-ssl.sh runs."
  exit 1
fi
