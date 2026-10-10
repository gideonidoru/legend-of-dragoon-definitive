# ModelsHD

Geometry upgrades for Legend of Dragoon: Definitive, derived from Severed Chains. ModelsHD owns meshes and animation compatibility. Texture artwork, lighting and rendering remain separate workstreams.

**Version 0.4.0 applies the first smoothing pass across the supported model catalog.** All 1,343 canonical containers were inspected: 964 receive some smoothing and 379 retain their originals. The package contains 8,531 unique custom part replacements, reused across 10,811 part instances. This includes the nine confirmed party field models, all 19 party battle forms, eligible NPCs, bosses, enemies, scenery and world-map resources. Unresolved actors retain their catalog labels. This is a broad foundation, not individually rebuilt faces, hair, hands or costumes.

The complete custom geometry is public and bundled with the compatible engine in the normal Definitive installation. Players need no Python, authoring setup or separate model transfer. [Coverage and verification](../../docs/definitive/MODELS_HD_WORLD_PASS.md) · [Publication policy](../../docs/definitive/ASSET_POLICY.md).

## Build and install

From the project root with Java 25:

```sh
./gradlew modelsHdTests modelsHdJar modelsHdBundle
```

Artifacts are `build/modelshd/ModelsHD-0.4.0.jar` and `ModelsHD-0.4.0.zip`. The normal game package includes the JAR under `bundled-mods`; the installer supplies the matching engine. HD artwork selection loads ModelsHD and Skurfa. Original artwork removes the managed visual mods, restoring the native route on the next game/model load. Existing campaigns retain their choices; ModelsHD can be selected in the game's mod menu.

Installation, updates and rollback use the model version from the active verified game release. Older manually copied ModelsHD JARs remain in user data but are not linked alongside the bundled mod. Other custom mods are preserved. For standalone use, copy the complete JAR into a matching current Definitive engine's `mods` folder and remove older standalone ModelsHD JARs from that folder. Version 0.4 requires the shared geometry event added with this engine increment; older engines are not a supported pairing.

## How the pass works

One subdivision pass uses strength 0.7 and a 70-degree crease threshold. Open joint borders and sharp edges stay fixed. Degenerate, overlapping, folded or non-manifold parts retain their originals. Flat surfaces that gain no geometric improvement also stay unchanged. Eligible source triangles become four candidate triangles; this is a topology count, not a measured Deck performance budget.

Exact single-part geometry identities bind each replacement to original positions, normals, scale, polygon flags and indexed connectivity. Runtime UV, palette and page relocation is excluded from the identity. The shared TMD loader prepares rendering geometry before allocation, leaving original CPU tables, part numbering, transforms, collision, scripts, animation and texture-animation data intact. A failed replacement retains the original and frees any staged allocations. Explicit authored tables and custom table types bypass automatic refinement. Effects that deform vertices by original indices switch to native geometry before deformation; an earlier refined cache is retired safely.

The version-1 pack maps each new corner to a source face using convex `sourceWeights`. The reader inherits active UVs, material words, blending and colors. UV interpolation rounds to native eight-bit packet coordinates; texture seams still need visual review. Texture dimensions supplied by the engine remain attached to the native construction route. ModelsHD never loads or selects a texture image.

An optional author override at `model-packs/modelshd/parts/<singlePartGeometrySha256>.json` takes priority over the matching embedded part. Both use the same strict, bounded reader. Invalid overrides retain the native part rather than silently selecting another candidate. The earlier `battle/` payloads and `roster.json` remain as historical authoring/provenance records; the normal 0.4 route uses the new part index and does not apply the legacy battle pass a second time. Legacy whole-model `battle/` overrides are not active in this route.

## Reproduce the custom payload

Keep original extracted inputs in a separate local directory:

```sh
python3 -m venv /PRIVATE/modelshd-tools
/PRIVATE/modelshd-tools/bin/python -m pip install -r scripts/requirements-visual.txt
/PRIVATE/modelshd-tools/bin/python scripts/build-modelshd-world-pass.py \
  --files /PRIVATE/game/files \
  --catalog docs/definitive/model-catalog/model-catalog.json \
  --core-roster integrations/modelshd/src/main/resources/modelshd/models/roster.json \
  --output /PRIVATE/modelshd-world-pass
```

The builder verifies all source aliases against catalog hashes, deduplicates exact parts, checks fixed borders and noncollapsed triangles, and records every changed or retained part. It refuses an existing destination and publishes a completed output atomically. Only generated changed geometry, checksums, recipe and coverage metadata are included in Git; it never exports original control meshes. Gradle checks the complete embedded inventory and every payload digest before packaging.

## Texture compatibility

| Route | Behavior and evidence |
| --- | --- |
| Indexed textures and VRAM replacements | Active UV, palette, page, color and blend words are inherited. Original image pixels and palette-animation ownership are unchanged. |
| Typed PNG/RGBA route, including CharHD field rebuilding | Explicit width/height reach the shared native constructor. Synthetic tests and simulated PNG construction across the catalog pass; actual CharHD gameplay remains to be checked. |
| Authored geometry or custom table types | Their existing geometry owner retains priority. Unknown geometry has no guessed replacement. |
| Arbitrary custom GPU remapping | Separate integration evidence is required. Later GPU edits retain their owner; rebuilding must use the engine's declared material/dimension route. No universal third-party compatibility claim is made. |

## Next

Compare the complete actors at normal Deck gameplay size, exercise scenes and animations with representative texture mods, and measure loading, memory and frame times. Then author clearer faces, hair, hands and costumes for the party, followed by prominent NPCs, boss families and common enemies. [Detailed roadmap](../../docs/definitive/MODELS_HD_ROADMAP.md).

Code remains AGPL v3 under the project LICENSE. Preserve upstream and embedded attribution notices. Custom work is public; disc images, original extracted data, controls, saves, credentials and authoring runtimes remain excluded.
