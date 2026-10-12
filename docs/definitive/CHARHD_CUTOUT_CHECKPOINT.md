# CharHD explicit cutout material · draft checkpoint

October 11, 2026. Protected authoring experiment, based on
`513b4716f6a0156bc389c263def34b24cf6392e8`. No new artwork is selected for runtime,
main or release. This supports preserving holes in separate native hair cards
when their appearance is upgraded; it does not yet correct Rose/Meru's hair.

`alphaMode: "cutout"` must agree between the manifest entry and checked UV
binding. Unknown or mismatched modes fail before appearance mutation. Missing
mode remains opaque and ignores PNG alpha exactly as accepted Dart does. The
image cache includes mode; transparent and opaque uses cannot share semantics.

An explicit face-detail vertex flag enables alpha rejection below 0.5 in both
field and battle shaders. It is a renderer vertex flag, separate from identically
numbered original packet-header bits. Visible black remains visible; colored
transparent texels are holes. Surviving pixels retain the existing opaque PSX
material route. This adds no blended transparency or default visual change.

## Verification

Environment: Mac Studio `Mac17,14`, Apple M5 Max, 64 GB, macOS 27.0.1 / 26A434;
Corretto Java 25+36, Gradle 9.1.0. Windowless GPU reports OpenGL 4.1 Metal 91.7.

```sh
./gradlew charHdTests --no-daemon
clang -framework OpenGL experiments/dart-modelshd-charhd/face-detail-probe.c -o /tmp/face-detail-probe
/tmp/face-detail-probe gfx/shaders /path/to/committed-baseline-shaders
./gradlew build -PfmvHdAssets=/path/to/verified/videos.zip --no-daemon
```

The baseline shader directory contains `tmd.vsh`, `battle_tmd.vsh`, `tmd.gsh`,
`tmd.fsh` and `battle_tmd.fsh` from the exact base commit above. These checks need
local graphics hardware access but create no window or game session.

- Fifteen CharHD tests pass, including indexed/RGBA/mapped cutout routing,
  visible black, opaque compatibility and rejected invalid alpha bindings.
- Both real GPU pipelines pass compile/link, committed-baseline pixel equality
  with detail disabled, independent head/body sampling, opaque alpha/coverage,
  body UV/map isolation, transparent holes and the 127/128 alpha boundary.
- Initial GPU fixture failure was conflicting signed/unsigned sampler units:
  the new active effect-detail sampler defaulted to unit zero. Explicit typed
  sampler assignments repaired the fixture. No failed run is counted as proof.
- The full build passes with the existing 1.1 GiB video source bundle whose hash
  matches the FMV lock (`c3dedfadb8659eead2444a434fe312faae9f304fe5fae00c0223163d6855419b`).
  An initial network-based build failed in `fmvHdAssets` with curl exit 56.
- Delivery suite: 456 tests, zero failures/errors, eight skipped. The ordinary
  `test` task is skipped by this project's supported build; do not count it as
  another suite. No native gameplay or physical Steam Deck result is inferred.

The companion corrected-head registry independently passes 192 source-based
construction combinations and preserves all nineteen selected runtime files.
Actual hair-card assets, multi-angle artistic acceptance, native behavior and
Deck performance remain separate work.
