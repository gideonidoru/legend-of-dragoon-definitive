#!/bin/bash
# Portable Definitive entry point (2026-10-09), AGPL v3; see repository LICENSE.
set -euo pipefail
umask 077
TAG=definitive-current
# Updated by scripts/assemble-installer.py after building the small portable UI package.
EXPECTED=6594360e46cf2ebbebed10ee544403d53007b8d6e31252c85a513ced59838704
CACHE="$HOME/.cache/legend-of-dragoon-definitive"
# Inspect every lexical component, including links before .., before any cache IO.
require_real_path() {
  local rest="$1" prefix='' part
  [[ "$rest" == /* ]] || rest="$PWD/$rest"
  rest="${rest#/}"
  while [[ -n "$rest" ]]; do
    part="${rest%%/*}"
    if [[ "$rest" == */* ]]; then rest="${rest#*/}"; else rest=''; fi
    prefix="$prefix/$part"
    [[ ! -L "$prefix" ]] || { echo 'Choose a real installer cache directory; parent folders must not be linked.' >&2; return 1; }
  done
}
require_real_path "$CACHE"
mkdir -p -- "$CACHE"
CACHE=$(cd -- "$CACHE" && pwd -P)
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
# definitive-owned-stage-v1: recovery runs only while fd9 holds the setup lock.
# Unknown root contents or links are preserved; a name alone never grants ownership.
stage_is_owned() {
  local candidate="$1" kind="$2" receipt="$1/.definitive-stage-owner" entry base mode owned_receipt=0
  [[ -d "$candidate" && ! -L "$candidate" && -O "$candidate" ]] || return 1
  case "$(uname -s)" in
    Linux) mode=$(stat -c '%a' -- "$candidate") ;;
    Darwin) mode=$(stat -f '%Lp' "$candidate") ;;
    *) return 1 ;;
  esac
  [[ "$mode" == 700 ]] || return 1
  if [[ -e "$receipt" || -L "$receipt" ]]; then
    [[ -f "$receipt" && ! -L "$receipt" && -O "$receipt" && $(wc -c < "$receipt") -le 8192 ]] || return 1
    [[ "$(cat "$receipt")" == "$(printf 'definitive-stage-v1\n%s\n%s\n%s\n' "$kind" "$(id -u)" "$candidate")" ]] || return 1
    owned_receipt=1
  fi
  # Legacy stages qualify only for a closed shallow download shape. A valid
  # receipt also owns the known unpack directory; rm never follows its symlinks.
  for entry in "$candidate"/* "$candidate"/.[!.]* "$candidate"/..?*; do
    [[ -e "$entry" || -L "$entry" ]] || continue
    base="${entry##*/}"
    if [[ -d "$entry" && ! -L "$entry" && -O "$entry" && "$owned_receipt" == 1 ]]; then
      case "$kind:$base" in portable:installer|java:jdk) continue ;; esac
    fi
    [[ -f "$entry" && ! -L "$entry" && -O "$entry" ]] || return 1
    case "$kind:$base" in
      portable:installer.zip|portable:bootstrap.log|portable:java-path|java:runtime.tar.gz|*:.definitive-stage-owner) ;;
      *) return 1 ;;
    esac
  done
}
recover_owned_stages() {
  local parent="$1" prefix="$2" kind="$3" candidate suffix
  for candidate in "$parent/$prefix"*; do
    [[ -e "$candidate" || -L "$candidate" ]] || continue
    suffix="${candidate##*/}"; suffix="${suffix#"$prefix"}"
    [[ "$suffix" =~ ^[A-Za-z0-9]{6}$ ]] || { printf 'Preserved unexpected setup stage: %s\n' "$candidate" >&2; continue; }
    if stage_is_owned "$candidate" "$kind"; then
      rm -rf -- "$candidate" || printf 'Could not remove interrupted setup files; preserved remaining stage: %s\n' "$candidate" >&2
    else
      printf 'Preserved unrecognized interrupted setup files: %s\n' "$candidate" >&2
    fi
  done
}
recover_owned_stages "$CACHE" ".portable." "portable"
STAGE=''
cleanup() {
  [[ -z "$STAGE" ]] || rm -rf -- "$STAGE"
}
trap cleanup EXIT
STAGE=$(mktemp -d "$CACHE/.portable.XXXXXX")
printf 'definitive-stage-v1\n%s\n%s\n%s\n' 'portable' "$(id -u)" "$STAGE" > "$STAGE/.definitive-stage-owner"
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
