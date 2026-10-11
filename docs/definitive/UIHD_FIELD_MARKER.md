# UIHD player-marker palette coverage

The field player's animated triangle uses the big-arrow pixels with three different encounter palettes. The big-arrow TIM supplies only the red palette; low and medium encounter colours use two palette rows from the small-arrow atlas. UIHD previously bound its 4x big-arrow artwork only to red. The published engine's actual marker queue therefore selected enhanced artwork for 12 of 36 frame/colour combinations and native artwork for the other 24.

UIHD now explicitly registers the same source-bound 4x animation for the two additional player palette bindings. A small optional indexed UI reference retains each native index and the source brightness. The Standard shader uses the authored artwork's brightness relative to that reference while the current native palette supplies hue. A different live texture index keeps native rendering. Ordinary UI artwork continues using the existing exact-colour reference behavior.

Original texture words, encounter palette words, animation UVs, shape coverage, black/discard, STP, positioning, indicator configuration and reduced-motion behavior remain authoritative. This fixes missing HD selection for the blue and green player marker; it does not replace the marker silhouette or claim physical Steam Deck visual acceptance. Disabling UIHD or changing the bound source restores native rendering for all three colours. Reusing its upload slot retires every derived encounter binding before checking new source/image/CLUT eligibility. The two extra lazy selections cost at most 417,792 additional resident texture bytes, within the existing 32 MiB UI budget. There is no added texture unit, drawing pass, or per-frame driver query.

## Verification

- `UiPaletteDetailTest` uses original synthetic TIM data to check exact source indices, visible index zero, discard at a nonzero index, visible black/STP, cropped nibble addressing, malformed controls and unchanged native bytes.
- `UiHdFieldMarkerProbe` boots the actual UIHD JAR through the mod loader, invokes `SMap.loadMiscTextures`, and renders every frame through `MapIndicator` and the actual Standard texture selector. It checks 36/36 HD combinations, disabling/re-enabling UIHD, remembered sources, changed-source retirement, moved image/CLUT metadata, valid-source reload, stable residency and unchanged native words/palettes. It uses a private imported-game fixture; no original data enters Git.
- `NativeUiPaletteDetailProbe` uses synthetic inputs in a windowless native context and the actual Standard shader, retained UI texture preparation, pooled model selection and rendering. A CPU colour/coverage oracle checks all sixteen 4x subtexels across live colours and native transparency passes, zero-colour and STP cases, changed indices, private ownership, pool reset, visible index zero, and fractional offsets crossing into a different native neighbor. Final success/reporting occurs after cleanup, including native teardown. The fault control reports failure after actual context destruction.

The optional entrypoints are loaded as an init script:

```
./gradlew --offline --no-daemon -I gradle/uihd-source-probe.gradle \
  uihdFieldMarkerProbe -PuihdSourceFixture=/absolute/private/fixture
./gradlew --offline --no-daemon -I gradle/uihd-source-probe.gradle \
  uihdFieldMarkerProbe -PuihdSourceFixture=/absolute/private/fixture \
  -PuihdMarkerEnabled=false
./gradlew --offline --no-daemon -I gradle/uihd-source-probe.gradle \
  nativeUiPaletteDetailProbe -PuiPaletteContextPath=/absolute/context.dylib \
  -PuiPaletteReportPath=/absolute/report.json
```

The native probe reuses the existing `scripts/headless-depth-clear-context.c` helper and its teardown fault mechanism. Add `-PuiPaletteInjectCleanupFailure` to exercise final failure reporting. No window or game starts. Mac readback verifies this host's OpenGL implementation; GLES and Steam Deck gameplay remain separate checks.
