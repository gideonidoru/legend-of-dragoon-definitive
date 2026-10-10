# ModelsHD 0.4: broad smoothing pass

Requested milestone: apply the initial inexpensive smoothing pass across the model catalog and update the normal downloadable ModelsHD mod. This is surface refinement, not a bespoke reconstruction of every character. The inventory includes 1,324 remaining supported canonical containers and the existing 19 party battle forms, totaling 1,343.

## Required behavior

- Inspect every supported catalog container, including field actors, battle combatants/stages, scenery, world-map objects and shared resources. Keep a per-container/per-part result. Do not invent actor identities for unresolved resources.
- Publish only generated custom replacement geometry, its source identities, recipe, checksums and coverage metadata. Keep game discs, original controls, extracted input files and authoring runtimes out of Git and downloads.
- Apply one bounded refinement pass. Pin open joint borders and creases over 70 degrees; retain invalid topology, inverted child triangles and surfaces with no geometric benefit. Keep source-part indices and transforms intact.
- Deduplicate exact single-part identities so shared components reuse the same custom payload. A part identity includes geometry, normals, indexed connectivity and rendering layout flags, excluding relocated UV/CLUT/page addresses.
- Apply replacements in the shared TMD mesh preparation path, before allocation. Keep original CPU geometry tables, scripts, animation, texture/palette animation streams and model-instance ownership untouched.
- Inherit current runtime UVs, material words, colors, blending and supplied texture normalization dimensions. Typed indexed/PNG routes, including the field texture override used by CharHD, remain available. Explicitly authored/custom geometry owners retain priority. Deforming effects using original CPU vertex indices retire any cached refinement and keep their native geometry. Arbitrary third-party custom GPU remapping requires separate integration evidence.
- Invalid, missing, mismatched or allocation-failed replacements retain the native model; free any partial candidate allocations before fallback. Mod deselection retains the native path. Unknown geometry is never guessed.
- Bundle the complete mod into the compatible engine release and normal installation/update/rollback path. Players need no Python runtime, private mesh transfer or authoring step.

## Acceptance evidence

Required checks: synthetic smoothing fixtures; exact Python/Java part identity agreement; finite native vertex construction across generated packs; unchanged source tables and texture/animation ownership; indexed and typed PNG addressing; corrupt pack rejection; native fallback and allocation cleanup; complete hashed JAR/package inventory. Review both source standards and this spec before release.

These checks establish compilation, source binding, packaging and headless rendering-data behavior. Visual quality, actual CharHD gameplay, all scene transitions, mod-order combinations and physical Steam Deck frame times remain separate acceptance gates. No claim of commercial perfection or community approval follows from automated tests.

## Coverage from the generated manifest

| Result | Count |
| --- | ---: |
| Canonical containers inspected | 1,343 |
| Containers receiving at least one custom part | 964 |
| Containers retaining every original part | 379 |
| Unique source parts inspected | 10,727 |
| Unique custom replacements bundled | 8,531 |
| Changed part instances across the catalog and core roster | 10,811 |
| Changed unique source triangles → candidate triangles | 281,448 → 1,125,792 |

All nine source-confirmed party field models and all 19 core battle forms receive some smoothing. The remaining catalog contributes 945 containers with changes. These are containers/components, not 964 individually remodeled characters. Flat/no-benefit parts (1,236), duplicate overlapping faces (312), non-manifold vertex fans (180), degenerate faces (209), inverted refinements (246), and non-manifold overlapping faces (13) account for 2,196 retained unique parts. Mixed-bit-depth containers, standalone TMDs and other unsupported formats remain outside the established inventory.

Raw custom payloads total 204,689,228 bytes; the complete compressed JAR is about 41 MiB. The recipe and individual SHA-256 checksums are versioned in `integrations/modelshd/src/main/resources/modelshd/models/world-pass.json`. Earlier `battle/` payloads are retained as provenance, not applied on top of the new pass.

## Local verification, October 10, 2026

Engine baseline before this change: `0232ef464dc89de3f0e8395de94c68242d1c105f`. macOS 27.0.1, Mac Studio `Mac17,14`, Corretto Java 25 (`25+36-LTS`), Gradle 9.1.0. Generation used Python 3.12.0 and NumPy 1.26.4; the full synthetic regression suite runs separately with the project's pinned NumPy 2.5.1/Pillow 12.3.0. Authoring and test runtimes remain outside the repository.

- `scripts/build-modelshd-world-pass.py --files LOCAL_FILES --catalog docs/definitive/model-catalog/model-catalog.json --core-roster integrations/modelshd/src/main/resources/modelshd/models/roster.json --output NEW_LOCAL_OUTPUT`: complete generation passed, with all appearance hashes checked, fixed original open borders, noncollapsed and correctly wound new triangles and source/recipe/tool bindings.
- `./gradlew modelsHdTests`: 31 headless behavior tests pass, including shared field/world loading, indexed/typed PNG normalization, corrupt payload retention, authored-owner priority, full native scenery coordinate range and cleanup after failed second material allocation.
- `./gradlew modelsHdWorldProbe -PmodelProbeFiles=LOCAL_FILES -PmodelProbeReport=LOCAL_REPORT`: all 1,343 native source containers and all 8,531 bundled custom parts construct through the actual geometry event. 10,811 changed part instances activate; 69,192,240 native vertex floats are finite. Original CPU vertex tables remain unchanged. A separately simulated typed PNG route also constructs across every container. Reports remain in the local diagnostic workspace.
- The twelve original synthetic Python fixture groups pass (64 tests), including the five new whole-catalog algorithm fixtures. These checks use invented inputs rather than retail assets.

The native probe uses no-op allocation and isolates TMD construction from auxiliary stream decoding. Original texture/palette animation ownership is preserved by retaining the original container and CPU tables; actual animated palette playback is not established by that probe. No game window is opened.

Next acceptance work: visual comparisons at gameplay size, stock/CharHD texture combinations, real animation and scene transitions, and physical Deck loading/memory/frame-time measurements. Targeted authored reconstruction follows the party-first roadmap after that assessment.

Independent standards/spec reviews found and resolved two regressions before delivery: folded subdivision triangles now retain their complete original part, and vertex-difference animation retires a cached refined mesh before indexing the original CPU vertices. Regression tests cover both. Cached ownership also remains correct when another consumer directly builds the same table.

Final local supported build: `./gradlew --no-daemon --console=plain build modelsHdTests modelsHdBundle definitivePackage portableInstaller -PreleaseTag=definitive-alpha-2026-10-10-modelshd-world` passes on the integrated rendering/audio source. Delivery checks report 154 cases, zero failures/errors and seven deliberately skipped opt-in cases; ModelsHD reports 31 passing cases with none skipped. The game boot suite is deliberately excluded from normal builds. The complete ModelsHD JAR contains exactly 8,531 indexed part files and matches every manifest size/SHA-256, with upstream credits and license notices embedded.


## Hosted verification and installer handoff

[ModelsHD pull request #5](https://github.com/gideonidoru/legend-of-dragoon-definitive/pull/5) is merged into public `main`, at `b144dd0ba971351255e23d21f48a56cddccec02a`. The implementation commits are `6fb1ad111185d27caa4a28b4a2a1f8b54a623cbc`, `f2c6d137a1523f8f5b074177679ce3a87cd176f0` and `1defc9aa155c6fccfeb4dfdcc43974391c624e4e`. [Hosted build 38076846433](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/38076846433) succeeded at the exact merge commit in the synthetic fixture, Linux and macOS jobs. Each platform passed all 31 ModelsHD checks and 162 delivery checks, with four opt-in cases skipped on Linux and seven on macOS.

Both downloaded game packages contain `bundled-mods/ModelsHD-0.4.0.jar`. Its 8,531 indexed custom part files match every public manifest length and SHA-256, with no extra part payloads. The native whole-catalog probe passed again on that merged engine source. A separate audit quantized every candidate vertex to the runtime's float32 precision: all 1,125,792 generated triangles remained noncollapsed and correctly wound against their source subtriangle. Original input and diagnostic reports remain local.

**Combined installer publication remains pending.** The separate ModelsHD release draft is deliberately unpublished: its engine package predates the newer all-HD installer and delivery fixes in the public `definitive-alpha-2026-10-10-all-hd-repair` release, source `5c078329745e9be012cf8e1e000f3deeb0eab792`. Publishing the older package would cause automatic updates to replace those additions. The installer workstream is consolidating ModelsHD 0.4 with that current delivery work into one verified release; do not publish this draft or redirect the primary installer links to it.

The consolidation must preserve the shared `TmdGeometryEvent` path, source-bound geometry index and all 8,531 payloads, native vertex-difference animation fallback, active texture/material handoff, ModelsHD 0.4 bundle task and credits. Re-run ModelsHD checks and verify the complete hashed JAR in the combined Linux/macOS packages before publication. This handoff does not establish physical Deck performance, gameplay animation coverage or visual acceptance.
