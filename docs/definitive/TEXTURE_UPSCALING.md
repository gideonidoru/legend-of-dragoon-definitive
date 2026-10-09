# Legacy model and texture upscaling

Added at the owner's request on 2026-10-09. **Experimental offline 2× pilot implemented; no whole-game remaster or Deck performance claim.** Improve the appearance of original character, enemy and environment models through an optional, reproducible texture-enhancement pipeline. Preserve the game's art direction and make original-texture fallback available independently of gameplay presets.

## Prior community effort

The [Upscaled Graphics Mod page](https://legendofdragoon.org/projects/image-upscaling/) credits theflyingzamboni, Zychronix and Mr. Scentless and includes model textures among its experiments. Its March 16, 2025 update says the old emulator-focused results were discarded and the effort moved to Severed Chains; work was paused around other projects. The owner reports abandonment, but the public page still describes an intention to resume and supplies no completion date. Its present delivery status is therefore unverified. We will not make Definitive depend on its completion, assume its assets are available, or copy previews without established terms.

## Separate enhancement paths

| Path | Intended improvement | Scope |
| --- | --- | --- |
| Offline texture upscaling | Clearer character clothing/faces, enemy detail and environmental surfaces | First target: process textures ahead of play and load an optional external pack. Preserve original mesh, silhouette, UV layout, skeleton and animation. An independent Scale2x baseline is implemented; stronger AI-assisted methods are being evaluated separately. |
| Whole-frame upscaling | Improve output quality or performance at a chosen internal resolution | Separate D14 renderer experiment after baseline evidence; assess motion, UI text, particle effects, latency and power. Sharpening alone is not texture reconstruction. |
| Higher-detail geometry | Smoother silhouettes or authored model replacements | A later, separately scoped option. Image super-resolution cannot automatically add correct mesh topology, rigging or animation. No engine or model-system rewrite is planned. |

The first path extends the artwork goals beyond Skurfa's background coverage. It does not require applying a neural model every frame on a Steam Deck. Respect the existing engine and seek a small compatibility hook only if a demonstrated loader limitation needs one.

## Existing source seams and open questions

At fixed baseline `fba1543543865e29ee572f479003d9b47158eeb3`, [SubmapObjectTextureEvent](../../src/main/java/legend/game/modding/events/submap/SubmapObjectTextureEvent.java) exposes indexed TextureBuilder replacements. [RetailSubmap.prepareSobjModel](../../src/main/java/legend/game/submap/RetailSubmap.java) consumes them and builds the model using original texture dimensions; [SMap.renderSmapModel](../../src/main/java/legend/game/submap/SMap.java) binds the override. This is source evidence for a field-object trial, not proof of all-model high-resolution support.

[CharacterTemplate](../../src/main/java/legend/game/characters/CharacterTemplate.java) supplies character battle texture paths and [Battle](../../src/main/java/legend/game/combat/Battle.java) loads character/enemy TIM data. Those paths do not establish a generic high-resolution RGBA replacement contract. Audit battle loaders, normalized UVs, palette variants, transparency/blending, animated textures and texture ownership before claiming character/enemy coverage. Avoid replacing character registries solely for presentation if that introduces unnecessary save dependencies.

## First implementation slice and acceptance

1. Inventory representative field-character, battle-character, enemy and environment textures privately, recording mappings and unsupported cases. Include small faces, transparent edges, palette swaps and animated surfaces.
2. Use a clearly licensed synthetic fixture to compare a simple reference upscale with candidate enhancement methods. Pin tool/model versions, weights hashes, inference settings, licenses and source/output hashes. Choose by evidence rather than a universal scale factor or marketing label.
3. Process a small private asset set only within the authorized implementation/test scope. Keep extracted and derived game textures, weights and runtimes outside Git; repository deliverables are tools, manifests, validation and permitted synthetic fixtures. Resolve output/pack distribution terms separately.
4. Validate UV seams, alpha edges, palette-dependent variants, animation consistency and preserved visual identity. Reject hallucinated symbols/facial detail, shimmer, sharpening halos and stretched/clipped mappings. Compare at handheld viewing size and enlarged inspection size.
5. Compare original vs enhanced textures on the same Deck scenes/settings: load time, transition peaks, system/texture memory, frame-time percentiles and power. Select sizes from the measured budget; do not assume 8x textures fit or improve handheld results.
6. Demonstrate per-pack disabling/missing-output fallback, scene transition cleanup, suspend/resume and unchanged gameplay/saves. Keep the original texture path intact. Geometry improvements and whole-frame effects need separate acceptance.

Inputs, extracted assets, generated packs and gameplay screenshots stay private unless publication rights and scope are established. That initial planning milestone involved no model downloads or engine changes. The implementation and evidence below supersede its no-implementation status. D16 tracks this work; D03/device evidence and API/mapping audits remain prerequisites to runtime claims. The existing provisional schedule is not a promise of whole-game model remaster coverage.

## Implemented reproducible pilot

`legend.definitive.textures.TexturePilot` uses standard Java 25 ImageIO, an independently implemented [Scale2x algorithm](https://www.scale2x.it/algorithm), and a bounded strict TIM reader. No external model weights are needed. Supports 4/8-bit indexed and 15-bit direct colour; 24-bit and images over 512×512 are rejected. Palette selection is explicit. Border pixels clamp. It generates original, nearest-neighbour and Scale2x comparison previews, plus a separate engine PNG and stable sorted manifest with all input/output SHA256 hashes. Existing output directories are never overwritten. The synthetic TIM fixture is original project code under AGPL v3; no extracted images are checked in.

After `./gradlew managerJar`, run privately:

```sh
java -Djava.awt.headless=true -cp build/libs/definitive-manager.jar legend.definitive.textures.TexturePilot PRIVATE_INPUT_TIM NEW_PRIVATE_OUTPUT_FOLDER 0
```

Optional final arguments `DISK CUT OBJECT` specify an exact field map. Put a reviewed supported output in the active data generation's `texture-packs/pilot/` and enable **Model texture pilot · experimental** in the launcher's mod preferences. Direct development launches can use `-Ddefinitive.legacyTextures=true`. The option defaults off and follows managed update/restore preferences. Battle texture replacement is not implemented.

The adapter uses `SubmapObjectTextureEvent`; it preserves original dimensions for normalized UVs and leaves other mod replacements intact. Validation requires exact disk/cut/object, original TIM SHA256, source dimensions, pipeline identity, palette zero, a single palette (or direct colour), 2× PNG dimensions/hash and byte-exact reproducible RGBA pixels. Missing, corrupt, mismatched or unsupported packs retain original textures. No renderer or geometry rewrite.

**PSX transparency requires care.** Engine PNG alpha is the STP bit: coloured pixels with alpha zero remain visible; only black/zero discards. Preview PNGs instead use ordinary opacity. STP black stays visible. Never feed a conventional opaque preview into the runtime pilot: its pixel validation will reject it.

## Current private evidence and limits

On 2026-10-09, processed Dart's 256×256 combat atlas and enemy 388's combat atlas at palette zero into 512×512 comparisons. Originals and results remain outside the repository in `Development/Definitive-private-texture-pilot`. Per-input reports and manifest hashes are retained there. Headless tests cover explicit palettes, malformed/truncated data, STP/colour preservation, algorithm differences, exact mapping, corrupt pack fallback contract and refusing output overwrite.

Both representative combat atlases contain **64 palettes**. A scan of the first-disc extracted field texture candidates found no single-palette candidates. Consequently, these samples are **offline comparisons only**: the conservative runtime adapter rejects their palette layout. Flattening them to palette zero would change material colours. Before useful real-game coverage, map per-face CLUT/UV usage, detect overlapping UV regions with conflicting palettes, and develop a narrow palette-aware replacement seam. The existing event alone does not establish general model coverage. This is a concrete blocker, not evidence of a successful remaster.

Scale2x selects existing colours and smooths pixel corners; it reconstructs neither faces nor material detail. At 2×, RGBA texture bytes are 4× the original; at 4×, 16×. Deck GPU-memory traffic, transitions, animation seams and visual quality remain unmeasured. Keep the pilot off by default until representative scene A/B evidence justifies it.

## Highest-fidelity visual direction

The owner's goal now includes character/enemy/environment models, texture detail, effects, menus and coherent lighting. Preserve recognizable faces, costume shapes, silhouettes and animation. Evaluate offline neural reconstruction against the original, nearest and Scale2x controls; avoid automatic face beautification or invented symbols. Review material crops and seams on animated models at 1280×800, not just enlarged texture sheets. Carefully authored higher-detail models are a later presentation track with compatible rigs and measured budgets.

DLSS 5's current local neural-rendering implementation targets NVIDIA RTX hardware ([NVIDIA research](https://research.nvidia.com/labs/adlr/DLSS5/)); [Steam Deck uses AMD graphics](https://www.steamdeck.com/en/tech/deck). Our practical experiment is expensive enhancement before shipping, with ordinary GPU rendering during play. That approach does not reproduce DLSS neural lighting and materials. Whole-frame spatial/temporal upscaling remains a separate measured renderer investigation.

## Offline neural comparison — first round

Used the publisher's [Real-ESRGAN model archive](https://github.com/xinntao/Real-ESRGAN/releases/tag/v0.2.5.0) and the [NCNN Vulkan v0.2.0 executable](https://github.com/xinntao/Real-ESRGAN-ncnn-vulkan/releases/tag/v0.2.0). NCNN code declares MIT; Python Real-ESRGAN declares BSD-3-Clause. Publisher licenses/notices remain alongside local tools. No runtime, weights or derived retail images are bundled in Git or the installer.

Archive SHA256: `e0ad05580abfeb25f8d8fb55aaf7bedf552c375b5b4d9bd3c8d59764d2cc333a`; Mac NCNN archive SHA256: `f1cc31f13398126ddec25a186b9e50c7ab3e4f2bc011d0222331b43ce2037d3b`; executable SHA256: `c1c35d92079085de96b9d547fd7e4464bc8a2e9ccf28d7b8c712d72ade91b7cc`. These are observed downloaded identities, not publisher signatures; the old assets do not supply API digest fields. Exact model bin/parameter hashes are pinned in `scripts/compare-neural-textures.py`.

The script requires Pillow 12.3.0, accepts private TexturePilot output folders, validates model hashes, records commands, source/result hashes and timings, and produces a same-scale comparison board. It refuses an output inside this checkout or an existing output folder. Skurfa's cut-5 HD image supplies the private board's world-art reference. The tool runs locally; private images are never uploaded.

| Sample | Neural candidate | Local inference seconds | Alpha pixels differing at 2× comparison size |
| --- | --- | ---: | ---: |
| Dart / palette 0 | AnimeVideo v3 / 2× | 0.240 | 0 |
| Dart / palette 0 | General x4plus / 4× | 0.686 | 0 |
| Dart / palette 0 | Illustration x4plus-anime / 4× | 0.379 | 0 |
| Enemy 388 / palette 0 | AnimeVideo v3 / 2× | 0.147 | 11,403 |
| Enemy 388 / palette 0 | General x4plus / 4× | 0.655 | 20,896 |
| Enemy 388 / palette 0 | Illustration x4plus-anime / 4× | 0.360 | 20,896 |

Environment: Mac Studio M5 Max, official NCNN executable above, tile 256, one processing thread, default GPU selection (log identifies Apple M5 Max), no test-time augmentation. The 4× outputs use Lanczos downsampling for a 2× comparison. Timings include local process startup and image decode/encode; they are neither rigorous performance benchmarks nor Steam Deck inference results.

Visual inspection found smoother armor curves and cleaner enemy outlines, with altered tiny markings and some softened painted detail. The illustrated candidate produces stronger drawn edges; the general candidate sometimes produces a glossy/waxy material impression. Neither is approved for a finished pack. Enemy alpha changes demonstrate why conventional neural output cannot simply replace STP-encoded textures. Current palette guards correctly leave all these samples offline. Model-space seams, animation and player preference remain untested.

Next compare alpha-preserving island processing and palette-aware material reconstruction, then evaluate on animated models against the original and enhanced backgrounds. See [visual quality and Deck budgets](VISUAL_QUALITY.md).

## Palette-aware material and mask experiments

Post-alpha work on 2026-10-09 adds three private tools. None changes the engine, the published alpha or the default artwork.

- `scripts/audit-model-materials.py` reads bounded unpacked TMD containers and 4-bit TIMs, follows per-face relative CLUT/UV references, reports overlaps and conflicting palette colors, and emits a comparison atlas only when colors can be flattened without conflicts. It recognizes one strictly bounded SC field zero-padding layout in addition to standard TIMs. This does **not** broaden the shipped runtime TIM reader.
- `scripts/preserve-texture-transparency.py` reapplies the original binary STP and black-discard semantics to 2×/4× neural RGB. It preserves visible STP black and prevents newly black RGB from discarding originally visible pixels. Input/output/script hashes, dimensions, environment and command are recorded.
- `scripts/compare-material-islands.py` gives each used palette its own padded tile, fills its border from that material only, runs pinned neural weights, blends inferred RGB with original paint, and restores the original STP/discard coverage. The current experiment uses eight source texels of padding, illustration x4plus-anime at 4×, and a 50% RGB blend. Those values are experiments, not approved defaults. Disconnected regions sharing a palette can still influence each other; inclusive UV rasterization is a heuristic, not a filtering-footprint or runtime seam guarantee. Overlapping palette coverage, CLUT animations and extra container subfiles are rejected.

The earlier palette-zero previews could not represent whole models correctly. Auditing actual material references established:

| Private sample | Dimensions | Model parts / textured faces | Used / available palettes | Conflicting / overlapping texels |
| --- | --- | --- | --- | --- |
| Dart combat model 32 | 256×256 | 17 / 253 | 11 / 64 | 0 / 0 |
| DRGN21 field asset folder 35, object 0 | 128×112 | 15 / 313 | 13 / 32 | 0 / 0 |

Neither sampled container declares CLUT animation or the extra subfile. This limited observation does not establish global animation compatibility. Source identities:

| Sample | Original model SHA256 | Original TIM SHA256 |
| --- | --- | --- |
| Dart | `c8b7ebc75104ccc9fe00f38f51a044b90bdf45fff33d41980f520a41754e30be` | `1deccd3e23a34ce6f3ebfec1c75db9e110411c03f1b4b90dbe791c413645a928` |
| Field sample | `c98e332eba70ca91eaf5b3d8ee09dd97b04f365d40182e2feb6f07bbed404d1d` | `d529ad4c3eeb9fa7021eac168e44381dd1e26c4a6ae1a3d7738e87f9d31f2666` |

Correctly mapped whole-atlas neural comparisons still changed 23,502 Dart and 11,681 field alpha pixels in the 4× candidates resampled for 2× inspection. Both the full-atlas mask-restored pass and the palette-isolated pass subsequently produced **zero STP/discard mismatches** against the nearest-scaled original material atlas at native output size. This is pixel-coverage evidence, not a quality score or gameplay result.

| Palette-isolated sample | Output | Local offline seconds | Engine-encoded output SHA256 |
| --- | --- | --- | --- |
| Dart | 1024×1024 | 4.187 | `42460442c2c2faf36cf26643455f3f772b75b9e026da26d307addbd78f08aaa6` |
| Field sample | 512×448 | 2.834 | `24e5908a9518aa273d7e6a445f86f7e8959426fd65b91ef546b4c5fdce214447` |

Timing includes local NCNN startup and processing on the Mac Studio, with script composition. It is neither Deck runtime performance nor a benchmark across models. The first sandboxed run could not access Metal; its failed logs remain private. Successful runs used permitted GPU access. All originals, generated tiles, previews, logs, neural weights and manifests remain outside Git.

Reproduce with your own privately extracted assets and the pinned publisher tooling:

```sh
python3 scripts/test-model-material-audit.py
python3 scripts/test-texture-transparency.py
python3 scripts/test-material-islands.py
python3 scripts/audit-model-materials.py "$PRIVATE_MODEL" "$PRIVATE_TIM" "$NEW_PRIVATE_AUDIT"
python3 scripts/compare-material-islands.py \
  --model "$PRIVATE_MODEL" --tim "$PRIVATE_TIM" \
  --engine "$NCNN_ENGINE" --models "$NCNN_MODELS" \
  --output "$NEW_PRIVATE_COMPARISON"
```

Use Pillow 12.3.0. Each output directory must be new and outside this checkout. Six original synthetic checks currently cover bounds/zero-padding, conflicting palette colors, STP/discard semantics, invalid scales, cross-material border isolation and blending confined to original coverage. Next inspect posed models and source-size facial detail; determine which material regions need hand-authored restoration. The conservative single-palette runtime guard remains intact.

### Posed inspection and sampling-footprint correction

`scripts/inspect-model-poses.py` now compares the original **per-face** palettes against candidate atlases on the original geometry and standard TMD keyframes. It produces three views, a sampled-keyframe GIF, source/output hashes and missing/extra coverage counts. It is a private orthographic software inspection tool: it does not reproduce native lighting, PS1 projection, blending, culling, interpolation, scene scripts or Deck behavior. The GIF uses 100 ms per sampled keyframe for inspection, not a claim about gameplay timing.

This check exposed a concrete defect in the integer-rasterized material atlases: the first field candidate lost 43, 7 and 16 pixels in the three posed views, and 42–44 pixels across its ten front-view keyframes. Binary atlas-mask equality alone did not establish correct UV sampling. Merely dilating masks one pixel also created hundreds of palette-color conflicts and was rejected.

`scripts/audit-uv-footprints.py` instead conservatively intersects UV triangles with complete source texel cells. It adds 753 Dart and 424 field texels to the material coverage without overlapping palettes or conflicting colors in these samples. Boundary touches can overestimate native fragments; it remains a conservative audit gate. The palette-isolated tool's explicit `--sampling-footprint uv-cells` option uses that coverage, rejects overlaps and records this dependency and its version. NumPy 2.5.1 is required alongside Pillow 12.3.0; pinned offline dependencies are in `scripts/requirements-visual.txt` ([Pillow](https://pypi.org/project/pillow/12.3.0/), [NumPy](https://pypi.org/project/numpy/2.5.1/)).

The corrected field candidate has **zero missing and extra pixels** in all three views and all ten original keyframes in this nearest-sampled offline inspection. Its engine-STP PNG SHA256 is `ac8ca7443a6cb2f271cf55c70fc1392e4fae9296ed0ad394f5c054ccd6e8f91b`; local material processing took 2.839 seconds. Animation input SHA256: `8a5aeb40469e7c82935d4005c8fa7b17bff286b376217c06bf29f1c5bd7db0f0`. All artifacts remain private. The 50% isolated treatment keeps more of the original painted cloth and belt detail than the full neural treatment in visual inspection; that is a developer assessment, not community preference or release acceptance.

```sh
python3 scripts/audit-uv-footprints.py "$PRIVATE_MODEL" "$PRIVATE_TIM" "$NEW_PRIVATE_FOOTPRINTS"
python3 scripts/compare-material-islands.py \
  --model "$PRIVATE_MODEL" --tim "$PRIVATE_TIM" \
  --engine "$NCNN_ENGINE" --models "$NCNN_MODELS" \
  --sampling-footprint uv-cells --output "$NEW_PRIVATE_COMPARISON"
python3 scripts/inspect-model-poses.py \
  --model "$PRIVATE_MODEL" --tim "$PRIVATE_TIM" --animation "$PRIVATE_ANIMATION" \
  --candidate "$NEW_PRIVATE_COMPARISON/candidate-engine-stp.png" \
  --label 'Isolated material candidate' --output "$NEW_PRIVATE_POSES"
```

Twelve original synthetic checks cover these tools, including malformed CLUT columns that would otherwise alias later rows, vertex/keyframe references, high palette indices, rotational transforms and fractional UV-cell coverage. CI runs them using original fixtures without game assets or neural weights. Before runtime activation, add exact model/TIM binding, reject animated/conflicting materials, verify native filtering/blending and other mod overrides, and measure the actual Deck cost. The current published pilot still rejects these multi-palette textures.
