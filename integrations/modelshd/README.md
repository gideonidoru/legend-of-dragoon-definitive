# ModelsHD

Optional character geometry for Legend of Dragoon: Definitive, derived from Severed Chains. ModelsHD owns meshes, proportions, attachments and animation compatibility. Character artwork and rendering remain separate workstreams.

**Version 0.3.0: bundled development geometry pass for all 19 party battle variants.** Nine normal forms, nine Dragoon forms and Divine Dart now have source-bound generated candidates and native-loader checks. This improves eligible surfaces; it does not deliver the individually remodeled faces, hair, costumes or anatomy required for the final visual target. Field and world-map replacements are unfinished. The retained portrait/neck experiments are held and are not shipped by this mod.

ModelsHD and its complete 19-model custom roster are public and included in the normal Definitive download. Players do not need Python, an authoring environment or a separate model transfer. [Publication policy](../../docs/definitive/ASSET_POLICY.md).

Build from the project root with Java 25:

```sh
./gradlew modelsHdTests modelsHdJar modelsHdBundle
```

The complete artifact is `build/modelshd/ModelsHD-0.3.0.jar`; `ModelsHD-0.3.0.zip` contains the same complete mod plus optional authoring tools. The normal Definitive game package includes the JAR under `bundled-mods`, paired with the compatible engine. HD artwork selection loads ModelsHD and Skurfa; choosing original artwork removes the bundled visual mods from the managed workspace. Existing campaigns retain their mod choices and can enable ModelsHD through the game's mod menu. New campaigns initially include installed mods.

Updates and rollback select the model version in the active verified release. Earlier manually copied `ModelsHD-x.y.z.jar` files remain in user data but are not linked alongside the bundled version, preventing duplicate mod IDs. Other custom mod files are preserved.

For standalone use, install the complete JAR in a current Definitive engine's `mods` folder and select ModelsHD. The earlier Deck recovery release lacks the authored-model API and will retain originals with a warning; the new bundled install includes the required engine support.

## Geometry packs and original mode

ModelsHD first checks an optional author override at `model-packs/modelshd/battle/<sourceGeometrySha256>.json`, then uses the matching custom pack embedded in its JAR. Both use the same strict reader. Unknown geometry, invalid packs and unsupported owners retain their original model. Deselecting ModelsHD or choosing original artwork in the managed launcher restores the original route on the next game/model load.

Version 1 requires every original animation part in its original order and coordinate frame. A source-bound identity covers original positions, normals, scale, polygon commands and geometry references; it deliberately excludes relocated UV/palette/page words. Each part declares floating-point XYZ `vertices`, unit `normals` and `faces`. Each triangle or quad names a `sourceFace`, its new `vertices` and `normals` indices, and `sourceWeights`: one convex weight row per new corner over that source face's original corners. The loader copies the active source's material commands, palette/page/blend words and interpolated corner colour bytes. UV interpolation is rounded to the native packet's eight-bit coordinates; this limitation needs review at new texture seams before accepting a retopology. There is no new texture sampler or image loader in ModelsHD.

Players receive the geometry in the install. Authors can reproduce the roster from existing extracted files without changing the originals:

```sh
python3 -m venv /PRIVATE/modelshd-tools
/PRIVATE/modelshd-tools/bin/python -m pip install -r scripts/requirements-visual.txt
/PRIVATE/modelshd-tools/bin/python scripts/build-modelshd-roster.py \
  --files /PRIVATE/game/files \
  --output /PRIVATE/modelshd-authoring
```

The builder requires all 19 sources, validates matching keyframes and fixed open borders, and publishes a new output only after the full roster succeeds. It refuses existing output and never writes into the checkout. Only the custom candidate packs are published into the mod resources after verification. Source controls and raw extraction stay separate. The adjacent public roster manifest binds each candidate to its source identity and checksum; the build refuses an incomplete or corrupted roster.

For a single-character source control and subdivision pilot:

```sh
python scripts/export-modelshd-pilot.py \
  --source /private/game/files/characters/haschel/models/combat/32 \
  --output /private/modelshd-pilot
```

It requires NumPy and the existing source-inspection helpers. The pilot is a pipeline exercise; it does not supply a finished face, costume sculpt or community-approved character. Authored replacements can use denser topology through the same material-face mapping. Clamped budgets are format safeguards, not measured Deck performance budgets.

A pack is fully decoded and validated before GPU preparation. All new parts are prepared before any live part reference changes. Failure frees staged resources and retains the original character. Successful replacement preserves part transforms, animation state, visibility and script-facing part numbers. The game owns normal teardown through its model parts. Animated palettes, auxiliary container data, unknown source geometry and custom geometry owners are held until separately supported.

## Texture mod compatibility

| Route | Current behavior and evidence |
| --- | --- |
| CharHD's current field texture event | ModelsHD 0.2 is battle-only and does not alter that field route. Source audit establishes separation; an actual combined gameplay run remains required. |
| Standard indexed battle textures or replaced VRAM images | Active palette, page, blend and UV words are inherited. Combatant texture binding and image pixels are untouched. Native-loader fixture and private Haschel control checks pass. |
| Standard RGBA/PNG conversion | Texture normalization dimensions now remain attached to the source table and transfer to the new geometry. Native-loader fixtures and a simulated whole-actor PNG conversion pass. This is not a test of every HD texture mod. |
| Custom GPU UV/material mapping | A native source control is compared against the active mesh's UV, palette/page, colour and flag fields before replacement. An incompatible mapping keeps its existing owner and model, with a diagnostic. It needs an explicit handoff before ModelsHD can also replace its geometry. |
| Another custom model/mesh owner | ModelsHD leaves custom table/object types and unknown geometry alone. Mod-order permutations and shared ownership need actual integration tests. |

Texture mods should retain the original part/material identities and use the engine's typed texture-dimension route. For custom atlas layouts, add a declared geometry/material handoff rather than guessing from an image's dimensions or overwriting another mod's UVs. ModelsHD never loads, edits or selects a replacement image.

## Verified development evidence

On the Mac Studio with Java 25, the headless tests exercise the real native vertex constructor with a recording allocation backend. They cover indexed and RGBA addressing, relocated material words, unchanged colour alpha bytes, finite floating-point geometry, full-part validation, malformed packs, original ownership, active custom-UV rejection, rollback and cleanup after a failed second material layer.

The bundled roster covers **452 animation parts: 428 refined, 24 retained unchanged** by the topology safeguards. Geometry changes from 16,577 to 57,449 triangles across the full roster, with individual candidates ranging from 2,687 to 3,436 triangles. The 19 private native-loader probes pass: exact source-control vertex arrays, normal/Dragoon battle-adapter application, finite candidate data and a separately simulated PNG route. Python and Java source identities agree. Stored rigid-pose sampling covers 103 original animation files and 915 keyframes. Twenty Java behavior tests and five original synthetic roster tests pass locally. [Roster evidence and expansion plan](../../docs/definitive/MODELS_HD_ROADMAP.md).

These are CPU/native-loader and resource-lifecycle checks with no-op or recording allocation. They do not prove an OpenGL draw, actual event-driven gameplay activation, scene transitions, real CharHD compatibility, improved anatomy, a complete model rig, physical Steam Deck performance or community acceptance.

Next: compare whole characters at normal gameplay size, then author clearer faces, controlled hair and costume topology through the established replacement route. Inspect actual battle loading, transformations, visibility, teardown and combined texture mods. Measure a three-character battle and the heaviest Dragoon effects on a physical Deck. The [plan](../../docs/definitive/MODELS_HD_ROADMAP.md) keeps all 19 in scope before expanding to field characters and other actors. Judge whole-character improvement before returning to local seam polish; preserve original mode throughout.

Source and integration code use the upstream AGPL v3 license; see the project's LICENSE. Custom model packs and custom HD artwork are public under the owner’s policy. Disc images, full original extraction, source controls, saves, credentials and authoring runtimes stay outside Git. Preserve the embedded model attribution notices.
