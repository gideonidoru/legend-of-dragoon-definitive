# ModelsHD: the party first, then the world

ModelsHD owns geometry, proportions, attachments and animation compatibility. The initial scope is **19 party battle models**, including all normal and Dragoon forms and Divine Dart. Texture artwork and rendering/lighting remain separate workstreams. The installer includes the complete mod and its public custom roster. It uses a small authored-geometry engine API; no engine rewrite is planned.

## Bundled 0.3.0 geometry pass

One conservative subdivision pass refines eligible rigid parts, retains open joint borders and uses the active model's texture/material addressing. Non-manifold or degenerate source parts remain unchanged. The generated geometry is a foundation for individual character remodeling, not completion of the modern AA visual target. No new faces, sculpted costumes or accepted head/neck reconstruction are implied.

| Character | Normal triangles, original → candidate | Dragoon triangles, original → candidate |
| --- | ---: | ---: |
| Dart | 870 → 2,946 | 1,005 → 3,171 |
| Lavitz | 890 → 3,164 | 952 → 3,436 |
| Shana | 891 → 2,769 | 941 → 3,317 |
| Rose | 807 → 2,847 | 887 → 3,167 |
| Haschel | 791 → 2,723 | 820 → 2,848 |
| Albert | 866 → 2,948 | 958 → 3,418 |
| Meru | 806 → 2,687 | 875 → 3,029 |
| Kongol | 795 → 2,958 | 894 → 3,042 |
| Miranda | 809 → 2,843 | 855 → 3,027 |
| Divine Dart | — | 865 → 3,109 |

The complete roster has 452 original animation parts. Of these, 428 are refined and 24 retain their source geometry. Total triangles increase from 16,577 to 57,449. These are topology counts, **not measured frame-time or memory budgets**. All 19 canonical source identities are distinct; shared design work is possible, but animation-part mapping must be proved for each variant.

## Finish these 19 before expanding

1. **Whole-character art direction.** Evaluate the complete body at Deck gameplay size and in close views. Keep the original design, silhouette, costume and personality. Work on faces, hair, hands, armor shapes and clothing folds as coordinated upgrades; do not spend repeated rounds polishing an isolated seam while the rest of the actor is unfinished. No new texture images or shader edits belong to this workstream.
2. **Normal party roster.** Establish a complete authored Dart reference with face, hair, hands and costume topology; use Meru and Kongol as contrasting proportions and clothing controls. Apply that standard to all nine normal forms, giving each character individual recognition checks. The existing Haschel experiments remain references until whole-actor anatomy and motion are accepted.
3. **Dragoon roster.** Carry approved facial/proportion work into all nine Dragoon forms and Divine Dart. Preserve wings, armor, weapons, animation pivots, part visibility and transformations. Related characters can share authoring components where suitable, but each form retains its own source identity and export verification.
4. **Play and release gate.** Run actual battle activation, attacks, damage, victory, death, transformations and model reloads. Compare originals and upgrades with stock textures, CharHD and another representative texture replacement; exercise alternate mod order and custom UV-owner fallback. Measure physical Deck frame times, loading, memory and power with three party members and demanding effects. Include faithful fallback, missing/corrupt-pack recovery and mod deselection. Seek whole-character feedback before promoting the character work to an accepted visual remaster.

The material handoff inherits active UVs, palettes, pages, blending and typed HD texture dimensions. Custom atlas/GPU mappings retain their current owner unless an explicit handoff is added. Source-face interpolation currently rounds to native eight-bit UV coordinates; new texture seams require inspection. These restrictions must not be silently relaxed to gain coverage.

## Remaining-model inventory

On October 10, the private extracted data scan examined 114,307 files. It recognized 7,667 bounded relative TMD container references; 7,587 decoded through the current inspector. Exact canonical geometry deduplication found **1,343 geometry containers, including the 19 party battle forms**, leaving **1,324 additional distinct containers**. Eighty references use mixed texture bit depths and remain outside this inspector's support.

These numbers are **not a count of individual characters or required new sculpts**. A container can be a person, prop, multipart boss, effect or alternate scene representation. Different containers can be near-duplicates or share most components. Repeated source references are often the same geometry in different scenes. Standalone and other unsupported formats are not established as exhaustively covered. The detailed private inventory records aliases and held reasons; the custom party replacements are now public; the full original extraction and source controls are excluded.

Reproduce the exact-duplicate inventory:

```sh
python scripts/inventory-modelshd-sources.py \
  --files /PRIVATE/game/files \
  --output /PRIVATE/modelshd-remaining-inventory.json
```

## Expansion order after the party battle gate

| Phase | Scope | Work and exit criteria |
| --- | --- | --- |
| A | Party field, story and world-map representations | Associate source containers with named actors and scenes through load sites and scripts. Group duplicates and related designs. Extend ModelsHD through field/world hooks without touching CharHD texture ownership. Preserve script-facing part indices, occlusion, transitions and original animation. Validate the party across all four discs. |
| B | Prominent story NPCs | Rank by dialogue proximity, screen time and emotional importance. Reuse approved body/components where appropriate; author recognizable individual heads and costumes. Check dialogue gestures and close camera angles. |
| C | Bosses, dragons and transformations | Prioritize prominent encounters, then remaining bosses. Treat multipart bodies, animated palettes, auxiliary data, detached parts, phase changes and effects attachments as explicit contracts. Prove loading, damage and phase transitions for each family before scaling coverage. |
| D | Common enemy families | Group geometry relatives and palette variants. Author a family master, map variants individually and retain their active textures. Validate a representative attack/damage/death cycle plus each topology exception. Measure crowded encounters on Deck. |
| E | Remaining NPCs and visible 3D props | Prioritize assets that occupy meaningful screen area or distract from the improved party. Batch small background actors and shared props through proven templates; avoid polishing invisible geometry. Preserve collision and interaction behavior. |

Before each phase, turn the private aliases into a semantic roster: actor/family name, contexts, source identities, shared components, special animation/visibility behavior, current texture owner and priority. Record unmapped containers as unresolved. Set a defensible unique authoring count only after this classification, rather than treating every file as a separate redesign.

The mod is included in the normal install and remains selectable throughout. Texture packs keep their own images and selection; the rendering thread owns lighting and Deck APU features. For animated palettes, mixed bit depths, custom atlas layouts or new geometry owners, define and test an explicit material/geometry handoff before adding support.

## Verification and limits

Environment: Mac Studio `Mac17,14`, Apple M5 Max, 64 GB, macOS 27.0.1 (`26A434`), Corretto Java 25 (`25+36-LTS`), NumPy 2.5.1. Engine source before this increment: `774a58604720e36e788d190e0315f3fbbe2673d9`; local upstream reference: `fba1543543865e29ee572f479003d9b47158eeb3`.

Commands and results:

- `./gradlew --offline modelsHdTests modelsHdJar`: 20 Java tests pass, no skipped tests; complete mod JAR builds with the public custom roster. Covers battle normal/Dragoon selection, part-state preservation, materials, malformed input, fallback and allocation rollback.
- `python scripts/test-modelshd-roster.py`: five synthetic tests pass. Covers full roster generation, exact corner weights, missing/unsupported sources, staged-output cleanup, existing-file preservation and joint-border rejection. No retail fixtures are used in CI.
- `python scripts/build-modelshd-roster.py --files … --output …`: all 19 custom candidates produced with source/control/output hashes, part reports and original animation hashes. Open-border vertices are unchanged, held geometry is unchanged and new triangles are noncollapsed. All 103 available matching standard animation files, containing 915 stored keyframes, produce finite rigid-transformed candidate vertices.
- `modelshd.PrivateActorProbe source control candidate report`: 19/19 private CPU/native-loader probes pass. Source controls reproduce native vertex arrays exactly; the battle adapter applies every normal and Dragoon candidate; candidate native data is finite. Active native addressing and simulated PNG texture normalization pass. Probe commands and reports are retained with the private packs.

These results do not establish actual OpenGL rendering, engine animation interpolation, scene transitions, joint collision, combined CharHD gameplay, complete anatomy, visual acceptance, community approval or physical Deck performance. The optional package is a development candidate. The complete ModelsHD JAR is now bundled with the compatible engine. Both embedded and override packs use the same strict reader. Managed installation, artwork selection, version updates, rollback and older manual-copy coexistence are checked headlessly. Custom meshes and custom HD artwork are public under the [owner’s publication policy](ASSET_POLICY.md); disc images, original extraction, source controls, saves, credentials and authoring runtimes remain excluded. An older standalone engine without the authored-model API retains originals with a warning.
