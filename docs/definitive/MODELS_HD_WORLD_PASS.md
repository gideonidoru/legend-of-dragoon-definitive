# ModelsHD 0.4: broad smoothing pass

Requested milestone: apply the initial inexpensive smoothing pass across the model catalog and update the normal downloadable ModelsHD mod. This is surface refinement, not a bespoke reconstruction of every character. The inventory includes 1,324 remaining supported canonical containers and the existing 19 party battle forms, totaling 1,343.

## Required behavior

- Inspect every supported catalog container, including field actors, battle combatants/stages, scenery, world-map objects and shared resources. Keep a per-container/per-part result. Do not invent actor identities for unresolved resources.
- Publish only generated custom replacement geometry, its source identities, recipe, checksums and coverage metadata. Keep game discs, original controls, extracted input files and authoring runtimes out of Git and downloads.
- Apply one bounded refinement pass. Pin open joint borders and creases over 70 degrees; retain invalid topology and surfaces with no geometric benefit. Keep source-part indices and transforms intact.
- Deduplicate exact single-part identities so shared components reuse the same custom payload. A part identity includes geometry, normals, indexed connectivity and rendering layout flags, excluding relocated UV/CLUT/page addresses.
- Apply replacements in the shared TMD mesh preparation path, before allocation. Keep original CPU geometry tables, scripts, animation, texture/palette animation streams and model-instance ownership untouched.
- Inherit current runtime UVs, material words, colors, blending and supplied texture normalization dimensions. Typed indexed/PNG routes, including the field texture override used by CharHD, remain available. Explicitly authored/custom geometry owners retain priority. Arbitrary third-party custom GPU remapping requires separate integration evidence.
- Invalid, missing, mismatched or allocation-failed replacements retain the native model; free any partial candidate allocations before fallback. Mod deselection retains the native path. Unknown geometry is never guessed.
- Bundle the complete mod into the compatible engine release and normal installation/update/rollback path. Players need no Python runtime, private mesh transfer or authoring step.

## Acceptance evidence

Required checks: synthetic smoothing fixtures; exact Python/Java part identity agreement; finite native vertex construction across generated packs; unchanged source tables and texture/animation ownership; indexed and typed PNG addressing; corrupt pack rejection; native fallback and allocation cleanup; complete hashed JAR/package inventory. Review both source standards and this spec before release.

These checks establish compilation, source binding, packaging and headless rendering-data behavior. Visual quality, actual CharHD gameplay, all scene transitions, mod-order combinations and physical Steam Deck frame times remain separate acceptance gates. No claim of commercial perfection or community approval follows from automated tests.

## Coverage from the generated manifest

| Result | Count |
| --- | ---: |
| Canonical containers inspected | 1,343 |
| Containers receiving at least one custom part | 1,004 |
| Containers retaining every original part | 339 |
| Unique source parts inspected | 10,727 |
| Unique custom replacements bundled | 8,777 |
| Changed part instances across the catalog and core roster | 11,103 |
| Changed unique source triangles → candidate triangles | 312,587 → 1,250,348 |

All nine source-confirmed party field models and all 19 core battle forms receive some smoothing. The remaining catalog contributes 985 containers with changes. These are containers/components, not 1,004 individually remodeled characters. Flat/no-benefit parts (1,236), duplicate overlapping faces (312), non-manifold vertex fans (180), degenerate faces (209) and non-manifold overlapping faces (13) account for 1,950 retained unique parts. Mixed-bit-depth containers, standalone TMDs and other unsupported formats remain outside the established inventory.

Raw custom payloads total 227,433,038 bytes; the complete compressed JAR is about 45 MiB. The recipe and individual SHA-256 checksums are versioned in `integrations/modelshd/src/main/resources/modelshd/models/world-pass.json`. Earlier `battle/` payloads are retained as provenance, not applied on top of the new pass.

## Local verification, October 10, 2026

Engine baseline before this change: `0232ef464dc89de3f0e8395de94c68242d1c105f`. macOS 27.0.1, Mac Studio `Mac17,14`, Corretto Java 25 (`25+36-LTS`), Gradle 9.1.0. Generation used Python 3.12.0 and NumPy 1.26.4; the full synthetic regression suite runs separately with the project's pinned NumPy 2.5.1/Pillow 12.3.0. Authoring and test runtimes remain outside the repository.

- `scripts/build-modelshd-world-pass.py --files LOCAL_FILES --catalog docs/definitive/model-catalog/model-catalog.json --core-roster integrations/modelshd/src/main/resources/modelshd/models/roster.json --output NEW_LOCAL_OUTPUT`: complete generation passed, with all appearance hashes checked, fixed original open borders, noncollapsed new triangles and source/recipe/tool bindings.
- `./gradlew modelsHdTests`: 28 headless behavior tests pass, including shared field/world loading, indexed/typed PNG normalization, corrupt payload retention, authored-owner priority, full native scenery coordinate range and cleanup after failed second material allocation.
- `./gradlew modelsHdWorldProbe -PmodelProbeFiles=LOCAL_FILES -PmodelProbeReport=LOCAL_REPORT`: all 1,343 native source containers and all 8,777 bundled custom parts construct through the actual geometry event. 11,103 changed part instances activate; 74,482,656 native vertex floats are finite. Original CPU vertex tables remain unchanged. A separately simulated typed PNG route also constructs across every container. Reports remain in the local diagnostic workspace.
- The twelve original synthetic Python fixture groups pass (63 tests), including the four new whole-catalog algorithm fixtures. These checks use invented inputs rather than retail assets.

The native probe uses no-op allocation and isolates TMD construction from auxiliary stream decoding. Original texture/palette animation ownership is preserved by retaining the original container and CPU tables; actual animated palette playback is not established by that probe. No game window is opened.

Next acceptance work: visual comparisons at gameplay size, stock/CharHD texture combinations, real animation and scene transitions, and physical Deck loading/memory/frame-time measurements. Targeted authored reconstruction follows the party-first roadmap after that assessment.
