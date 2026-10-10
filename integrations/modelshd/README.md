# ModelsHD

Optional character geometry for Legend of Dragoon: Definitive, derived from Severed Chains. ModelsHD owns meshes, proportions, attachments and animation compatibility. Character artwork and rendering remain separate workstreams.

**Development pilot, not an accepted visual upgrade or a published installer feature.** The first adapter targets normal player battle models. Field, world-map and Dragoon replacements are unfinished. The retained portrait/neck experiments are held; they are not shipped or installed by this mod.

Build from the project root with Java 25:

```sh
./gradlew modelsHdTests modelsHdJar
```

The optional code-only artifact is `build/modelshd/ModelsHD-0.1.0.jar`. For development, put it in the game's `mods` directory and select ModelsHD through the existing mod manager. The Definitive authored-model API is required; an older engine leaves ModelsHD inactive with a warning. No local runtime or copyrighted model/image is embedded in the jar.

## Geometry packs and original mode

ModelsHD looks for `model-packs/modelshd/battle/<sourceGeometrySha256>.json` relative to the game directory. No pack means the original model. Removing the pack or deselecting ModelsHD restores the original route on the next model load. This is the development Faithful fallback, not a new launcher toggle.

Version 1 requires every original animation part in its original order and coordinate frame. A source-bound identity covers original positions, normals, scale, polygon commands and geometry references; it deliberately excludes relocated UV/palette/page words. Each part declares floating-point XYZ `vertices`, unit `normals` and `faces`. Each triangle or quad names a `sourceFace`, its new `vertices` and `normals` indices, and `sourceWeights`: one convex weight row per new corner over that source face's original corners. The loader copies the active source's material commands, palette/page/blend words and interpolated corner colour bytes. UV interpolation is rounded to the native packet's eight-bit coordinates; this limitation needs review at new texture seams before accepting a retopology. There is no new texture sampler or image loader in ModelsHD.

The exporter produces a source control and a conservative whole-actor subdivision pilot outside the checkout:

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
| CharHD's current field texture event | ModelsHD's first adapter is battle-only and does not alter that field route. Source audit establishes separation; an actual combined gameplay run remains required. |
| Standard indexed battle textures or replaced VRAM images | Active palette, page, blend and UV words are inherited. Combatant texture binding and image pixels are untouched. Native-loader fixture and private Haschel control checks pass. |
| Standard RGBA/PNG conversion | Texture normalization dimensions now remain attached to the source table and transfer to the new geometry. Native-loader fixtures and a simulated whole-actor PNG conversion pass. This is not a test of every HD texture mod. |
| Custom GPU UV/material mapping | A native source control is compared against the active mesh's UV, palette/page, colour and flag fields before replacement. An incompatible mapping keeps its existing owner and model, with a diagnostic. It needs an explicit handoff before ModelsHD can also replace its geometry. |
| Another custom model/mesh owner | ModelsHD leaves custom table/object types and unknown geometry alone. Mod-order permutations and shared ownership need actual integration tests. |

Texture mods should retain the original part/material identities and use the engine's typed texture-dimension route. For custom atlas layouts, add a declared geometry/material handoff rather than guessing from an image's dimensions or overwriting another mod's UVs. ModelsHD never loads, edits or selects a replacement image.

## Verified development evidence

On the Mac Studio with Java 25, the headless tests exercise the real native vertex constructor with a recording allocation backend. They cover indexed and RGBA addressing, relocated material words, unchanged colour alpha bytes, finite floating-point geometry, full-part validation, malformed packs, original ownership, active custom-UV rejection, rollback and cleanup after a failed second material layer.

A private Haschel probe checks all 17 original animation parts. Eighteen source-control meshes reproduce the original native vertex arrays exactly; the subdivision pilot produces 129,360 finite native floats. All 17 parts also construct through a separately simulated PNG route. Python's source identity agrees with the Java reader. Source assets and reports remain private.

These are CPU/native-loader and resource-lifecycle checks with no-op or recording allocation. They do not prove an OpenGL draw, actual event-driven gameplay activation, scene transitions, real CharHD compatibility, improved anatomy, a complete model rig, physical Steam Deck performance or community acceptance.

Next: run the optional mod in a controlled battle, compare at normal gameplay size with texture mods enabled, inspect animation/visibility/teardown, and then author one complete recognizable character replacement. Judge whole-character improvement before returning to local seam polish. Preserve original mode throughout. ModelsHD remains outside the default installer until that acceptance gate passes.

Source and integration code use the upstream AGPL v3 license; see the project's LICENSE. Retail assets, generated model packs, images, saves and authoring runtimes stay outside Git.
