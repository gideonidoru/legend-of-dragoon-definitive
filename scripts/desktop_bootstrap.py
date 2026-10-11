"""Stable Steam Deck shortcut; resolve and verify current setup on every launch."""

LATEST = 'https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/latest/download/'


def desktop_entry():
    # Keep the same literal shell text valid under native Desktop Entry parsing.
    # No dollar/quote/field-code escaping is needed inside the Exec argument.
    shell = f"""set -o pipefail; umask 077; mkdir -p ~/.cache/legend-of-dragoon-definitive; (echo Legend of Dragoon - Definitive setup; mktemp -d /tmp/definitive-installer.XXXXXX | xargs -I DEF_STAGE /usr/bin/env -C DEF_STAGE /bin/bash -c 'set -euf -o pipefail; cleanup() {{ mv -fT desktop-bootstrap.log ~/.cache/legend-of-dragoon-definitive/desktop-bootstrap.log || echo Could not save the startup log.; rm -f desktop-bootstrap.log Install-Definitive.sh SHA256SUMS expected.sha256; pwd | xargs rmdir; }}; trap cleanup EXIT; (echo Downloading the latest verified installer; for attempt in 1 2 3; do rm -f Install-Definitive.sh SHA256SUMS expected.sha256; if curl --fail --location --show-error --proto =https --proto-redir =https --retry 2 --connect-timeout 20 --max-time 180 --max-filesize 1048576 --output SHA256SUMS {LATEST}SHA256SUMS && curl --fail --location --show-error --proto =https --proto-redir =https --retry 2 --connect-timeout 20 --max-time 180 --max-filesize 1048576 --output Install-Definitive.sh {LATEST}Install-Definitive.sh && grep -xE [a-f0-9]{{64}}[[:blank:]]{{2}}Install-Definitive[.]sh SHA256SUMS > expected.sha256 && sha256sum --check --strict --status expected.sha256; then break; fi; echo Download or checksum mismatch. Retrying the current release.; done; test -s expected.sha256 && test -s Install-Definitive.sh && sha256sum --check --strict --status expected.sha256 || {{ echo Installer checksum mismatch. No installer was launched.; exit 1; }}; /bin/bash Install-Definitive.sh) 2>&1 | tee desktop-bootstrap.log') 2>&1 || {{ echo Setup could not start.; echo Diagnostic logs:; echo ~/.cache/legend-of-dragoon-definitive/desktop-bootstrap.log; echo ~/.cache/legend-of-dragoon-definitive/installer-bootstrap.log; echo ~/.cache/legend-of-dragoon-definitive/installer.log; echo Press Enter to close.; read -r; exit 1; }}"""
    if any(ch in shell for ch in '\\"`$%'):
        raise ValueError('Bootstrap Exec must not require nested escapes or field codes')
    return ('[Desktop Entry]\nType=Application\nName=Install Legend of Dragoon: Definitive\n'
            'Comment=Guided Steam Deck setup with visible startup diagnostics\n'
            'Icon=applications-games\nTerminal=true\nExec=/bin/bash -c "' + shell + '"\nCategories=Game;\n')
