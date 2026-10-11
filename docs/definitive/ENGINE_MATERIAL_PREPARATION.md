# Material preparation during battle setup

The player reported sluggish battle setup alongside intermittent rendering faults.
The broader engine review requires measured improvements while preserving the
original UV, palette, STP/discard and artwork contracts. This increment addresses
a measured CPU cost in the material preparation subcomponent. It does not establish
that the complete physical Steam Deck battle entrance is smooth or that the
reported rendering faults are resolved.

## Measured baseline

At accepted main `ac6f96960a86c5681d50f009ab72465f3c21622e`, a private headless
probe exercised the actual loaders selected by EnvHD, using matching local original
model/TIM data and current embedded artwork. It covered 74 stage bindings and 73
unique atlases: two `MaterialAtlas` packs and 71 `BattleMaterialAtlas` packs.
The probe did not launch the game, create a graphics context or publish original data.

Each atlas was initially loaded, then measured in five paired trials: clear the
existing shared decoded cache, perform the complete loader call, then immediately
repeat that call. Compressed resource reads, hashing, layout, source coverage and
pixel validation are included. Original source reads and pixel hashing are outside
the timing. “Cache cold” describes the decoded cache, not cold filesystem pages.
Initial JVM/native-decoder startup is recorded separately and excluded from these
paired medians. These are Apple M5 Max CPU measurements, not Deck frame times,
first-launch guarantees, GPU uploads or full transition timings.

| Stage | Baseline cache cold | Corrected cache cold | Baseline reuse | Corrected reuse |
| --- | ---: | ---: | ---: | ---: |
| 2497 | 106.692 ms | 34.935 ms | 107.520 ms | 17.425 ms |
| 2503 | 82.612 ms | 26.876 ms | 82.912 ms | 13.989 ms |

Private phase instrumentation on the unchanged loader found roughly 29–43 ms in
ImageIO decoding, 36–44 ms in source coverage and packing-gap validation, and
17–21 ms in conversion into the native upload buffer after initial warmup.
The other loader already used the bounded image cache. Its 71 atlas medians were
16.265 ms with an empty decoded cache and 5.513 ms with immediate reuse.

## Correction and retained contracts

`MaterialAtlas` now calls the existing `ArtworkResources.image` bounded native
decoder and respects the existing image-cache setting. It receives independently
owned RGBA bytes and copies them into its existing direct, read-only upload buffer.
Coverage validation reads the same exact ARGB values from those RGBA bytes.

All source/hash/layout/provenance/palette/STP/discard/preserved-pixel and all-zero
packing-gap checks remain on every preparation, including cache hits. No cache
budget, queue, new setting, artwork, UV map, mesh, shader or GPU upload route changes.
The existing cache retains encoded-content identities and its decoded-buffer lease
is released before the material checks run. `CharHdMod.applyCombat` also uses this
shared loader; the correction is not limited to the two environment stages.

## Regression and exact-pixel evidence

Four final synthetic actual-loader cases cover repeated preparation/cache reuse,
independent direct read-only buffers surviving cache retirement, cache disabling,
full PNG-oracle agreement including transparent colour bytes, and current coverage
rejection after a cache hit. Replaying those final fixtures with the unchanged
baseline `MaterialAtlas` class produces two cache-contract failures and two passing
controls; corrected source passes all four. The focused material/artwork/stage set
contains 28 passing cases, including existing negative source/coverage controls.

The same complete 73-atlas CPU probe was rerun after the correction. All 73 complete
RGBA fingerprints match the baseline, and decoded bytes return to zero after each
cache clear. The independent ImageIO pixel oracle checked every pixel of all three
installed `MaterialAtlas` packs: the two EnvHD stages and Dart's CharHD pack, totaling
7,364,608 pixels with zero differing RGBA bytes. Direct/read-only buffer and decoded
lease retirement checks also pass. Original inputs and detailed receipts stay in
private diagnostics; no original game extraction is added to the repository.

## Remaining acceptance

Full source gates and independent review are separate from these focused results.
Physical Deck profiling still needs the actual scripted battle transition, first
mesh construction and GPU upload, texture visibility through attack/camera changes,
field/battle head transitions, frame pacing and input response. This increment does
not claim that those broader engine or campaign requirements are complete.
