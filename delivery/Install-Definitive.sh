#!/bin/bash
# Portable Definitive entry point (2026-10-09), AGPL v3; see repository LICENSE.
set -euo pipefail
TAG=definitive-alpha-2026-10-09-installer-fix
# Updated by scripts/assemble-installer.py after building the small portable UI package.
EXPECTED=02bc7c24b6a10c54d6a4da8fa0a6430e4180c24ea6f7e366129a070d6edb4338
CACHE="$HOME/.cache/legend-of-dragoon-definitive"
mkdir -p -- "$CACHE"
[[ ! -L "$CACHE" ]] || { echo 'Choose a real installer cache directory.' >&2; exit 1; }
LOG="$CACHE/installer-bootstrap.log"
LOCK="$CACHE/.portable-lock"
mkdir "$LOCK" 2>/dev/null || { echo 'Another Definitive installer is preparing. Wait for it to finish.' >&2; exit 1; }
STAGE=''
cleanup() {
  [[ -z "$STAGE" ]] || rm -rf -- "$STAGE"
  rmdir -- "$LOCK" 2>/dev/null || true
}
trap cleanup EXIT
STAGE=$(mktemp -d "$CACHE/.portable.XXXXXX")
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
  (prepare 3>&1 > "$LOG" 2>&1 || exit 1; echo 100) | zenity --progress --auto-close --no-cancel --title='The Legend of Dragoon · Definitive' --text='Preparing the installer…' || { zenity --error --text="Setup could not start. See $LOG for details."; exit 1; }
else
  prepare 3>/dev/null > "$LOG" 2>&1 || { command -v kdialog >/dev/null && kdialog --error "Setup could not start. See $LOG for details."; exit 1; }
fi
IFS= read -r JAVA < "$STAGE/java-path"
"$JAVA" --enable-native-access=ALL-UNNAMED -cp "$STAGE/installer/definitive-manager.jar:$STAGE/installer/manager-libs/*" legend.definitive.manager.ManagerMain --setup "$STAGE/installer"
