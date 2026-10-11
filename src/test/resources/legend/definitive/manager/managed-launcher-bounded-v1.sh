#!/bin/bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
LOG="$ROOT/launcher.log"
[[ ! -L "$LOG" && ( ! -e "$LOG" || -f "$LOG" ) ]] || { printf '%s\n' 'Unexpected launcher log. No game was started.' >&2; exit 1; }
PREVIOUS="$LOG.1"
[[ ! -L "$PREVIOUS" && ( ! -e "$PREVIOUS" || -f "$PREVIOUS" ) ]] || { printf '%s\n' 'Unexpected previous launcher log.' >&2; exit 1; }
LOGDIR="$(mktemp -d "$ROOT/.launcher-log.XXXXXXXX")"
TEMPLOG="$LOGDIR/output"
exec 4>"$TEMPLOG"
cleanup() { exec 4>&-; rm -f -- "$TEMPLOG"; rmdir -- "$LOGDIR" 2>/dev/null || true; }
trap cleanup EXIT
publish() {
  if [[ $(uname -s) == Linux ]]; then mv -Tf -- "$1" "$2"
  else mv -fh -- "$1" "$2"; fi
}
run_manager() {
  # Keep Steam's overlay compatible with the 64-bit runtime.
  if [[ ${LD_PRELOAD:-} == *ubuntu12_32/gameoverlayrenderer.so* ]]; then
    LD_PRELOAD=${LD_PRELOAD//ubuntu12_32\/gameoverlayrenderer.so/ubuntu12_64\/gameoverlayrenderer.so}
    export LD_PRELOAD
  fi
  JAVA="$("$ROOT/bootstrap-java" "$ROOT")" || return
  "$JAVA" --enable-native-access=ALL-UNNAMED -jar "$ROOT/definitive-manager.jar" @MODE@ "$ROOT"
}
set +e
run_manager 2>&1 | { head -c 16777216; cat >/dev/null; } >&4
CODE=${PIPESTATUS[0]}
set -e
if [[ @MODE@ == --play && ( $CODE == 130 || $CODE == 143 ) ]]; then printf '%s\n' 'Stopped from Steam.' >&4; CODE=0; fi
exec 4>&-
[[ ! -L "$LOG" && ( ! -e "$LOG" || -f "$LOG" ) ]] || { printf '%s\n' "Launcher output retained at $TEMPLOG" >&2; trap - EXIT; exit 1; }
[[ ! -L "$PREVIOUS" && ( ! -e "$PREVIOUS" || -f "$PREVIOUS" ) ]] || { printf '%s\n' "Launcher output retained at $TEMPLOG" >&2; trap - EXIT; exit 1; }
if [[ -f "$LOG" ]]; then publish "$LOG" "$PREVIOUS"; fi
publish "$TEMPLOG" "$LOG"
if [[ $CODE == 0 ]]; then exit 0; fi
MESSAGE="Definitive could not start (code $CODE). Details: $LOG"
if command -v kdialog >/dev/null; then kdialog --error "$MESSAGE" || true
elif command -v zenity >/dev/null; then zenity --error --text="$MESSAGE" || true
else printf '%s\n' "$MESSAGE" >&2; fi
exit "$CODE"
