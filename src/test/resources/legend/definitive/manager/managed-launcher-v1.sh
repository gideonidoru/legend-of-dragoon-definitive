#!/bin/bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
LOG="$ROOT/launcher.log"
[[ ! -L "$LOG" ]] || { printf '%s\n' 'Unexpected linked launcher log. No game was started.' >&2; exit 1; }
run_manager() {
  # Java is 64-bit; Steam also exports a 32-bit overlay. Preserve other preload entries.
  if [[ ${LD_PRELOAD:-} == *ubuntu12_32/gameoverlayrenderer.so* ]]; then
    LD_PRELOAD=${LD_PRELOAD//ubuntu12_32\/gameoverlayrenderer.so/ubuntu12_64\/gameoverlayrenderer.so}
    export LD_PRELOAD
  fi
  JAVA="$("$ROOT/bootstrap-java" "$ROOT")" || return
  "$JAVA" --enable-native-access=ALL-UNNAMED -jar "$ROOT/definitive-manager.jar" --play "$ROOT"
}
if run_manager >> "$LOG" 2>&1; then exit 0; else CODE=$?; fi
if [[ --play == --play && ( $CODE == 130 || $CODE == 143 ) ]]; then printf '%s\n' 'Stopped from Steam.' >> "$LOG"; exit 0; fi
MESSAGE="Definitive could not start (code $CODE). Details: $LOG"
if command -v kdialog >/dev/null; then kdialog --error "$MESSAGE" || true
elif command -v zenity >/dev/null; then zenity --error --text="$MESSAGE" || true
else printf '%s\n' "$MESSAGE" >&2; fi
exit "$CODE"
