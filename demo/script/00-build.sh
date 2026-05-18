#!/usr/bin/env bash
# 00-build.sh — populate ./public with only the runtime files. No sudo.
# Run as: bash script/00-build.sh
#
# Re-run after any edit to a runtime file. nginx serves ./public, so anything
# not copied here is not deployed — keeps reference assets, dead files, and
# repo metadata off the public site.

set -euo pipefail

cd "$(dirname "$0")/.."

# Files actually loaded at runtime (verified by reading Sonar Tamagotchi.html
# and grepping the JSX). ghost.jsx is dead code — the "ghost" species is
# implemented inline in creature.jsx.
files=(
  "Sonar Tamagotchi.html"
  styles.css
  ios-frame.jsx
  tweaks-panel.jsx
  creature.jsx
  radar.jsx
  battle.jsx
  screens.jsx
  app.jsx
)

mkdir -p public
# Wipe stale entries so a renamed/removed file doesn't keep getting served.
find public -mindepth 1 -delete

for f in "${files[@]}"; do
  if [[ ! -f $f ]]; then
    echo "missing source: $f" >&2
    exit 1
  fi
  cp -p "$f" "public/$f"
done

echo "Built public/ with $(printf '%s\n' "${files[@]}" | wc -l) files:"
ls -la public/
