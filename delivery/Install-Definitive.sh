#!/bin/bash
# Portable Definitive entry point (2026-10-09), AGPL v3; see repository LICENSE.
set -euo pipefail
umask 077
TAG=definitive-current
# Updated by scripts/assemble-installer.py after building the small portable UI package.
EXPECTED=5cff6d8a34d25c9dd1c1462ba5a1a8daa6a5fcccc57b7a06f5d32d9aadbabe84
CACHE="$HOME/.cache/legend-of-dragoon-definitive"
mkdir -p -- "$CACHE"
[[ ! -L "$CACHE" ]] || { echo 'Choose a real installer cache directory.' >&2; exit 1; }
LOG="$CACHE/installer-bootstrap.log"
LOCK="$CACHE/.portable-lock"
# Keep the lock inode; its existence is not ownership. The kernel releases the
# lock after the last inheriting process ends, including untrappable termination.
[[ ! -L "$LOCK" ]] || { echo "Unexpected linked setup lock: $LOCK" >&2; exit 1; }
[[ ! -d "$LOCK" ]] || { echo "An older setup left a directory lock. Close older installers, then remove that empty directory and retry: $LOCK" >&2; exit 1; }
exec 9>> "$LOCK"
if command -v flock >/dev/null; then
  flock -n 9 || { echo 'Another setup is still running. Wait for it to finish and retry.' >&2; exit 1; }
elif command -v lockf >/dev/null; then
  lockf -s -t 0 9 || { echo 'Another setup is still running. Wait for it to finish and retry.' >&2; exit 1; }
else
  echo 'The operating-system file-lock tool is unavailable. Setup has stopped without downloading.' >&2; exit 1
fi
STAGE=''
cleanup() {
  [[ -z "$STAGE" ]] || rm -rf -- "$STAGE"
}
trap cleanup EXIT
STAGE=$(mktemp -d "$CACHE/.portable.XXXXXX")
# Open a private inode first; atomically replace the log name without following links.
exec 8> "$STAGE/bootstrap.log"
case "$(uname -s)" in
  Linux) mv -fT -- "$STAGE/bootstrap.log" "$LOG" ;;
  Darwin) mv -fh -- "$STAGE/bootstrap.log" "$LOG" ;;
  *) echo 'Unsupported logging platform.' >&2; exit 1 ;;
esac
prepare() {
  printf '%s\n' '5' '# Downloading the installer from GitHub…' >&3
  for tool in curl unzip; do command -v "$tool" >/dev/null || { echo "$tool is needed to prepare the installer."; return 1; }; done
  curl --fail --location --silent --show-error --proto '=https' --proto-redir '=https' --retry 2 --connect-timeout 20 --max-time 900 --speed-limit 1024 --speed-time 60 "https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/$TAG/Definitive-Installer.zip" --output "$STAGE/installer.zip" || return 1
  printf '%s\n' '35' '# Checking the installer checksum…' >&3
  if command -v sha256sum >/dev/null; then ACTUAL=$(sha256sum "$STAGE/installer.zip" | awk '{print $1}'); else ACTUAL=$(shasum -a 256 "$STAGE/installer.zip" | awk '{print $1}'); fi
  [[ "$ACTUAL" == "$EXPECTED" ]] || { echo 'Installer checksum mismatch. No installer has been launched.'; return 1; }
  unzip -q "$STAGE/installer.zip" -d "$STAGE/installer" || return 1
  printf '%s\n' '55' '# Preparing Java 25. First use may download the runtime…' >&3
  JAVA=$(/bin/bash "$STAGE/installer/bootstrap-java" "$CACHE/runtime") || return 1
  printf '%s\n' "$JAVA" > "$STAGE/java-path"
  printf '%s\n' '90' '# Starting the guided installer…' >&3
}
if command -v zenity >/dev/null; then
  (prepare 3>&1 >&8 2>&1 || exit 1; echo 100) | zenity --progress --auto-close --no-cancel --title='The Legend of Dragoon · Definitive' --text='Preparing the installer…' || { zenity --error --text="Setup could not start. See $LOG for details."; exit 1; }
else
  prepare 3>/dev/null >&8 2>&1 || { command -v kdialog >/dev/null && kdialog --error "Setup could not start. See $LOG for details."; exit 1; }
fi
IFS= read -r JAVA < "$STAGE/java-path"
set +e
"$JAVA" --enable-native-access=ALL-UNNAMED -cp "$STAGE/installer/definitive-manager.jar:$STAGE/installer/manager-libs/*" legend.definitive.manager.ManagerMain --setup "$STAGE/installer" 2>&1 | { head -c 16777216; HEAD_CODE=$?; cat >/dev/null; exit "$HEAD_CODE"; } | tee -a /dev/fd/8
RESULTS=("${PIPESTATUS[@]}")
CODE=${RESULTS[0]}
set -e
if [[ ${RESULTS[1]} != 0 || ${RESULTS[2]} != 0 ]]; then
  echo 'Could not save complete installer startup diagnostics.' >&2
  if [[ $CODE == 0 ]]; then CODE=1; fi
fi
if [[ $CODE != 0 ]]; then
  MESSAGE="Setup could not start (code $CODE). See $LOG for details."
  printf '%s\n' "$MESSAGE" >&2
  if command -v zenity >/dev/null; then zenity --error --text="$MESSAGE" || true
  elif command -v kdialog >/dev/null; then kdialog --error "$MESSAGE" || true; fi
fi
exit "$CODE"
