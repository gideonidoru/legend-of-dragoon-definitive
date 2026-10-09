#!/bin/bash
# Definitive guided setup (2026-10-09), AGPL v3; see LICENSE.
set -euo pipefail
PACKAGE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# Runtime lives beside the package, never inside its verified inventory.
JAVA="$(/bin/bash "$PACKAGE/bootstrap-java" "$HOME/.cache/legend-of-dragoon-definitive/runtime")"
exec "$JAVA" --enable-native-access=ALL-UNNAMED -cp "$PACKAGE/definitive-manager.jar:$PACKAGE/manager-libs/*:$PACKAGE/libs/*" legend.definitive.manager.ManagerMain --setup "$PACKAGE"
