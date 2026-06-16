#!/usr/bin/env bash
#
# UI e2e smoke harness — boots the dedicated jvolution emulator, installs the
# debug build, drives the app through each feature screen via the in-app
# terminal, and captures screenshots to docs/screenshots/e2e/.
#
# This is a *smoke* harness: it verifies each screen renders (not a pixel-diff
# baseline — the runtime creature/genome are random). Run from the repo root:
#
#   scripts/e2e-ui.sh
#
# Requires: the `emu` helper (dedicated AVD policy), adb, a configured Android SDK.
# Domain logic is covered deterministically by :shared:jvmTest e2e tests; this
# layer covers the visual/integration surface (GENOME, ASSAY, tree, radar, CRT).
set -uo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5570}"
export ANDROID_SERIAL="$SERIAL"
PKG="today.superb.jvl"
OUT="docs/screenshots/e2e"
ADB() { adb -s "$SERIAL" "$@"; }
die() { echo "ERROR: $*" >&2; exit 1; }

echo "▸ booting dedicated emulator (emu up jvolution)…"
emu up jvolution || die "emu up jvolution failed"

echo "▸ waiting for device + boot…"
ADB wait-for-device || die "device never appeared"
for _ in $(seq 1 90); do
  [ "$(ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 2
done

echo "▸ building + installing debug build…"
./gradlew :androidApp:installDebug --console=plain >/dev/null || die "installDebug failed"

mkdir -p "$OUT"
SIZE=$(ADB shell wm size | grep -oE '[0-9]+x[0-9]+' | head -1)
W=${SIZE%x*}; H=${SIZE#*x}
INX=$((W / 2)); INY=$((H * 95 / 100))    # terminal input field, near the bottom
echo "▸ screen ${W}x${H}, terminal input at ($INX,$INY)"

launch() { ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 5; }
stop()   { ADB shell am force-stop "$PKG"; sleep 1; }
shot()   { ADB exec-out screencap -p > "$OUT/$1.png" && echo "  ✓ $1.png"; }

# Tap the on-screen element whose text contains $1, by parsing its uiautomator
# bounds (Compose exposes text to the a11y tree). Robust vs hardcoded fractions.
tap_text() {
  ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  local b
  b=$(ADB shell cat /sdcard/ui.xml 2>/dev/null | tr '>' '\n' | grep -F "$1" \
        | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
  [ -z "$b" ] && { echo "  ! element not found: $1" >&2; return 1; }
  local x1 y1 x2 y2
  read -r x1 y1 x2 y2 <<<"$(echo "$b" | grep -oE '[0-9]+' | tr '\n' ' ')"
  ADB shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}

# Type a terminal command and run it. The in-app terminal is always visible at
# the bottom; the trailing BACK hides the soft keyboard for a clean capture.
run_cmd() {
  local txt="${1// /%s}"                  # adb `input text` wants %s for spaces
  ADB shell input tap "$INX" "$INY"; sleep 1
  ADB shell input text "$txt"; sleep 1
  ADB shell input keyevent 66; sleep 2    # ENTER — submit
  ADB shell input keyevent 4;  sleep 1    # BACK  — hide keyboard
}

echo "▸ main flow — terminal-driven feature screens…"
launch
shot 01-sonar
run_cmd "genome"; shot 02-genome
run_cmd "tree";   shot 03-tree
run_cmd "scan";   shot 04-radar
run_cmd "sonar"
run_cmd "status"; shot 05-status
run_cmd "breed lumen"; shot 06-breed-assay   # PAIR-BOND ASSAY overlay (kept last)
stop

echo "▸ settings panel (isolated)…"
launch
tap_text "CFG"; sleep 2          # ⚙ CFG (top-right) located via uiautomator bounds
shot 07-settings
stop

echo "▸ done — screenshots in $OUT/"
ls -1 "$OUT"
