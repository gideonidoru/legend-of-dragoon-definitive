# ModelsHD

Optional character geometry for Legend of Dragoon: Definitive, derived from Severed Chains. ModelsHD owns meshes, proportions, attachments and animation compatibility. Character artwork and rendering remain separate workstreams.

**Version 0.2.0: optional development geometry pass for all 19 party battle variants.** Nine normal forms, nine Dragoon forms and Divine Dart now have source-bound generated candidates and native-loader checks. This improves eligible surfaces; it does not deliver the individually remodeled faces, hair, costumes or anatomy required for the final visual target. Field and world-map replacements are unfinished. The retained portrait/neck experiments are held and are not shipped by this mod.

Build from the project root with Java 25:

```sh
./gradlew modelsHdTests modelsHdJar modelsHdBundle
```

The optional code-only artifact is `build/modelshd/ModelsHD-0.2.0.jar`; `ModelsHD-0.2.0-tools.zip` also includes the local generation recipe. Neither contains retail geometry or replacement images. For development, put the jar in the game's `mods` directory, remove older ModelsHD jars from that directory, and select ModelsHD through the existing mod manager. **Use a current source build containing the authored-model API introduced in commit `6efdc8b`. The earlier Deck recovery release lacks this API and will leave ModelsHD inactive with a warning.** ModelsHD remains outside the default installer while visual and Deck acceptance are pending.

## Geometry packs and original mode

ModelsHD looks for `model-packs/modelshd/battle/<sourceGeometrySha256>.json` relative to the game directory. No pack means the original model. Removing the pack or deselecting ModelsHD restores the original route on the next model load. This is the development Faithful fallback, not a new launcher toggle.

Version 1 requires every original animation part in its original order and coordinate frame. A source-bound identity covers original positions, normals, scale, polygon commands and geometry references; it deliberately excludes relocated UV/palette/page words. Each part declares floating-point XYZ `vertices`, unit `normals` and `faces`. Each triangle or quad names a `sourceFace`, its new `vertices` and `normals` indices, and `sourceWeights`: one convex weight row per new corner over that source face's original corners. The loader copies the active source's material commands, palette/page/blend words and interpolated corner colour bytes. UV interpolation is rounded to the native packet's eight-bit coordinates; this limitation needs review at new texture seams before accepting a retopology. There is no new texture sampler or image loader in ModelsHD.

Generate the full roster from your existing extracted files, without changing the originals:

```sh
python3 -m venv /PRIVATE/modelshd-tools
/PRIVATE/modelshd-tools/bin/python -m pip install -r scripts/requirements-visual.txt
/PRIVATE/modelshd-tools/bin/python scripts/build-modelshd-roster.py \
  --files /PRIVATE/game/files \
  --output /PRIVATE/modelshd-0.2.0
```

The builder requires all 19 sources, validates matching keyframes and fixed open borders, and publishes a new output only after the full roster succeeds. It refuses existing output and never writes into the checkout. Copy the generated `model-packs` directory into your development game folder; keep its `verification` controls and `roster-manifest.json` separately. Controls are not upgrade packs. A private transfer archive may contain `mods/ModelsHD-0.2.0.jar` and `model-packs/modelshd/battle/*.json`; source-derived packs stay off public GitHub.

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

The 0.2.0 roster covers **452 animation parts: 428 refined, 24 retained unchanged** by the topology safeguards. Geometry changes from 16,577 to 57,449 triangles across the full roster, with individual candidates ranging from 2,687 to 3,436 triangles. The 19 private native-loader probes pass: exact source-control vertex arrays, normal/Dragoon battle-adapter application, finite candidate data and a separately simulated PNG route. Python and Java source identities agree. Stored rigid-pose sampling covers 103 original animation files and 915 keyframes. Eighteen Java behavior tests and five original synthetic roster tests pass locally. [Roster evidence and expansion plan](../../docs/definitive/MODELS_HD_ROADMAP.md).

These are CPU/native-loader and resource-lifecycle checks with no-op or recording allocation. They do not prove an OpenGL draw, actual event-driven gameplay activation, scene transitions, real CharHD compatibility, improved anatomy, a complete model rig, physical Steam Deck performance or community acceptance.

Next: compare whole characters at normal gameplay size, then author clearer faces, controlled hair and costume topology through the established replacement route. Inspect actual battle loading, transformations, visibility, teardown and combined texture mods. Measure a three-character battle and the heaviest Dragoon effects on a physical Deck. The [plan](../../docs/definitive/MODELS_HD_ROADMAP.md) keeps all 19 in scope before expanding to field characters and other actors. Judge whole-character improvement before returning to local seam polish; preserve original mode throughout.

Source and integration code use the upstream AGPL v3 license; see the project's LICENSE. Retail assets, generated model packs, images, saves and authoring runtimes stay outside Git.
