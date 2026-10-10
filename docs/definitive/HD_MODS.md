# EnvHD, CharHD, UIHD and FxHD

The owner requested four independent artwork mods, installed with Definitive by default. Activation/deactivation belongs to Severed Chains' existing Mods menu. No new installer switches or gameplay changes accompany them. Skurfa remains the separate background mod. The previous mandatory-core experimental texture listener has moved into CharHD; its installer checkbox and launch property are removed.

| Mod / ID | 0.1.0 content | Extension boundary |
| --- | --- | --- |
| EnvHD / `envhd` | 24 separately addressed palette regions on static battle stages 0 and 6; 4× reconstruction with source coverage preserved | Additional verified static stage packs, then field props/world-map surfaces. Existing Skurfa scene artwork is untouched. |
| CharHD / `charhd` | The source-bound field texture adapter; no new character assets or geometry | Approved compatible packs from the model workstream. Existing adapter requires its single-palette 2× contract; multi-palette/remodeled-character runtime integration remains future work. |
| UIHD / `uihd` | Complete default HUD and all nine portraits: 259 assets, including every HUD/basic palette, full menu pages and Dragoon spirit frames | Complete asset coverage; native artistic and physical Deck acceptance pending. See [UIHD production](UIHD_PRODUCTION.md). |
| FxHD / `fxhd` | The orthographic field dust texture at 4× | Separately verified effects/animation families; spells and transformation sheets remain original. |

The mod sources live under `integrations/<id>`, with reviewed runtime resource candidates in each `runtime-assets` folder. These are first pilot candidates, not an accepted whole-game remaster. Original extracted assets, raw meshes, local neural runtimes/weights and intermediate comparisons are not bundled. Underlying game artwork has separate notices from the AGPL source code. Explicit owner authorization in this implementation request covers publication of the derived pilot resources in this repository.

## Build and delivery

`gradle/hd-mods.gradle` compiles four independent mods against the current engine and produces `EnvHD-v0.1.0.jar`, `CharHD-v0.1.0.jar`, `UIHD-v0.2.0.jar` and `FxHD-v0.1.0.jar`. Standard `build` and `definitivePackage` include them alongside Skurfa under `bundled-mods`. Each retains code/artwork notices and release metadata.

Managed launch links all four JARs even when the legacy Skurfa preference selects original backgrounds. It preserves the game's saved enabled-mod configuration during updates/rollback. Bundling is not forced activation: existing players' in-game choices remain theirs. A player using original geometry/art can deactivate the corresponding mod without removing the package or changing saves. Standalone installation uses these four JARs in `mods/`, with this matching engine revision: EnvHD/UIHD/FxHD use the additive event/mesh hooks described below and are not drop-in compatible with unmodified upstream 3.0.0.

## Runtime correctness

EnvHD selects an immutable atlas by raw original model SHA256 and verifies the TIM, deterministic per-palette map, atlas hash, source discard/STP/visible-black classification and original texture-page references before creating GPU resources. It rejects animated/extra model dependencies and faces on other pages. Its meshes are separately owned and leave original geometry, packets, normals, lighting, animation and cached retail meshes untouched. The optional texture uses nearest sampling and clamp; translucent primitive modes retain their source TPAGE bits. Scene unload deletes both the optional meshes and texture.

Stage loading joins the matching model and TIM futures before posting replacement events or constructing resources on the rendering thread. A scene-generation and owning-battle guard rejects a completed load after another stage/battle has taken its place. There is no shared pending TIM between loader workers.

Counterattack darkening operates on original VRAM palettes. EnvHD switches to the original stage while that operation is active and returns to its replacement when the original multiplier is restored. Runtime-enabled texture animation likewise selects the original route. This preserves the legacy effect rather than freezing an undarkened HD scene. Shader color conversion still differs by up to eight-bit quantization from original five-bit color; visual/lighting equality is not claimed.

UIHD runs after ordinary atlas registration and through source-bound PNG/native UI loading hooks. It requires original encoded hashes and exact current pixel/dimension matches. Native UI restoration changes RGB while live indexed VRAM retains visibility, STP and palette animation; changed pixels use original RGB. Display geometry, UV coordinates, scissoring and timing stay in source units. Unpadded sheets use nearest filtering; protected lettering/portrait regions retain original visible pixels. Shared bounded decoding/prewarming and lazy bounded GPU residency reuse the engine's enhancements. See [UIHD production](UIHD_PRODUCTION.md) for selection, budgets and open acceptance work.

FxHD uses a source-bound event for the existing orthographic dust quad. It preserves STP/discard/visible-black classification and the original additive blend mode. Particle transforms, brightness, count, timing and size remain unchanged. The TMD dust effect, footprints and other attached effects remain on their original route. Extra quad/texture ownership is released with attached-effect teardown.

CharHD's existing conservative adapter preserves other field overrides and ignores absent/invalid packs. It no longer requires a launcher property: the in-game mod manager controls whether its listener is registered. No claim of remastered character coverage comes from including this adapter.

## Reproducible offline production

`scripts/build-hd-assets.py` uses the existing palette audit/packing/transparency helpers, pinned general Real-ESRGAN 4× weights, and explicit owner-extracted input/tool paths. It downloads nothing and writes to a new staging directory. Intermediate source tiles/inference logs stay outside the repository. Final resources include source/output hashes; EnvHD uses the independently checked material-pack format. Neural inference is never executed by the installed game.

The first run used Pillow 12.3.0, NumPy 2.3.5, weights SHA256 `713ee713b0353afaa27976f0563a64a5043bd70b9bd8936c2e26e25ebcdbcddf`, parameter SHA256 `35330ececcea33b6c397a72548e788d5d53becee4734c50b7fada36e89f10a86` and inference executable SHA256 `c1c35d92079085de96b9d547fd7e4464bc8a2e9ccf28d7b8c712d72ade91b7cc`. Binary/weights distribution remains separate.

Stage 0's base-level RGBA atlas estimate is 7,569,408 bytes; stage 6's is 7,208,960 bytes. These exclude driver overhead, upload temporaries and other scene assets. They are not measured Deck residency. Skurfa plus other scene content must be budgeted together.

## Acceptance and remaining work

### Environment production extension

EnvHD is extending the pilot with battle panoramas using the built-in image
generation tool. `BattleSkyTextureEvent` supplies a copy of the original MCQ and
preserves another mod's chosen replacement. Reviewed PNGs are bound to both source
and output hashes, exact integer-scaled dimensions, and an explicit visual-review
state. The renderer uses original native dimensions, MAGIC_2 offsets, camera
scrolling, repeated segments, brightness, clear colors, depth, and source visibility.
Owned mesh/texture resources are released on stage changes and battle teardown.
Rejected or missing artwork retains the original panorama.

Skurfa remains protected: the production ownership audit verifies its images and
all 1,044 census source folders before deciding which field scenes are gaps. Its
38 existing packs, 43 runtime bindings, and 87 source-identical cut/period mappings
are excluded from generation. The current field baseline is 612 configurations
before rendered-pixel deduplication and prop/overlay classification. EnvHD does
not register a field-background replacement listener in this extension.

The 74 drawable battle MCQ variants decode to 71 unique bitmaps; one uniform black
bitmap needs no restoration. The production task ledger therefore has 70 distinct
panoramas to restore, with source-identical variants reusing the same artwork.
Visual review, repeat-boundary checks, headless integration, and native acceptance
remain distinct states in `integrations/envhd/production`. Candidate/rejected art
stays outside runtime resources. These counts do not certify complete environment
coverage or native gameplay acceptance.

The current panorama batch has 36 custom revisions across 26 restoration
masters. Forest sunset, Star night, Shirley's Shrine forest, Valley suspended
rocks, Giganto carved figures, Mortal Dragon Mountain storm mist, and world-map
battle stage87 hills passed visual review and are selected; native scene
acceptance is pending. Sixteen masters need repeat-boundary corrections and
three need an intent/layout revision. Forty-four masters still await generation.
Nearby original field context clarified snow-covered wood, carved figures,
glacier ice, ruins, and Mayfil masonry. Skurfa's existing Vellweb artwork was also
used as a material/palette reference for its separate battle viewpoint. All custom candidates
and saved prompts are public production assets; original sources and comparison
boards remain outside Git. Selected pixel-identical variants resolve to one PNG
through separate source-bound manifests, preserving each original MCQ header.

Headless verification found 119 passing delivery tests and four desktop-only
tests skipped. Five ownership and eight import regression tests passed. Independent
decoders agree on all pixels of all 74 battle sources. The packaged Skurfa resources
match the pinned integration byte-for-byte; the EnvHD package contains seven reviewed
panoramas and no production candidates. Import preflight and rollback tests cover
source drift, corrupt prior resources, malformed controls, rejected revisions,
version conflicts, oversized inputs and publication failure.

Headless checks cover separated palette UV placement, source-page rejection, animation rejection, malformed PNGs, transparent colored pixels, existing-mod precedence, installer inclusion independent of Skurfa, and saved game-mod configuration through update/rollback. Existing delivery/material fixtures remain required. Build/package verification must confirm all four IDs, resources and notices, and source-correlated archives.

Regression checks also cover both source-load completion orders, deferred scene execution, obsolete/failed loads, and repeated attached-effect teardown with and without an HD replacement. Teardown cannot post replacement events or recreate HD dust resources.

Source inspection and sampled texture visual review do not prove native scene rendering, combined-mod gameplay, physical Deck costs or player approval. Those remain explicit acceptance steps. No game or visible desktop application is launched by this implementation's headless build/tests. The original pilot covered 24 material regions, five goods icons and one effect. Current UIHD coverage and remaining work are tracked in [UIHD production](UIHD_PRODUCTION.md); Other mods track their own production coverage; UIHD default HUD/portrait asset coverage is complete, with gameplay and visual acceptance pending.

Environment production follows the owner's current order: battle sky/background
panoramas; world-map sky and landscape thumbnails; world-map terrain and scenery;
battle floors/walls/surfaces; then field forests, towns, buildings, and interiors.
Track accepted unique artwork, scene coverage and exception reasons separately
from source-file and delivered-layer counts. CharHD, UIHD and FxHD remain separately
controlled mods for their corresponding artwork.
