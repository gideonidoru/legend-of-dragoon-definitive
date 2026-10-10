# Field and world-map model routing

Research date: October 10, 2026. This note describes source-backed routes, not visual approval, gameplay validation or finished model upgrades. The reproducible mapper is [modelshd-field-map.py](../../scripts/modelshd-field-map.py). It reads the owner's extraction locally and emits metadata only. Original geometry, scripts, textures and absolute private paths are not part of this deliverable.

## What is now mapped

The existing inventory's **6,650 supported field model references** all resolve to a disc, asset directory, submap cut/location and object/script index. They cover **517 distinct canonical geometry containers**. There are **zero unmatched supported field references**. These objects occur across 731 cuts in the present extracted dataset.

Separately, the field scene overlay table identifies **466 distinct additional geometry containers**, with 511 cut associations. These are loaded as the submap's 3D overlay; they are not evidence of 466 more characters. No canonical geometry is shared between these 466 overlays and the 517 field object containers in this inventory. The loader calls the resource `submapCutModel` and renders it separately from the submap objects. [RetailSubmap overlay load](../../src/main/java/legend/game/submap/RetailSubmap.java#L280), [overlay index table](../../src/main/java/legend/game/submap/RetailSubmap.java#L1981).

Together with the world-map vehicles, teleport resource, shared shadow and save-point resources, this module routes **989 of the 1,324 remaining supported containers**. It does not claim that every one is an actor. None of these 989 is one of the 19 already-shipped battle geometry identities.

| Field object naming evidence | Unique geometry containers |
| --- | ---: |
| Exact named source load or explicit unpacker repair | 10 |
| Additional containers with actor candidates from patch descriptions | 44 |
| Route known, actor identity unresolved | 463 |
| Total field object containers | 517 |

The ten exact field identities are one field/avatar geometry each for Dart, Lavitz, Shana, Rose, Haschel, Albert, Meru, Kongol and Miranda, plus Claire. This is a conservative lower bound on named models, not a count of all costume, cutscene or field variants. The patch descriptions contribute 80 supported appearances over 48 canonical containers; four of those containers also have exact name evidence. The metadata deliberately retains the other names as candidates.

## Exact route derivation

`SUBMAP/NEWROOT.RDT` contains a 1,024-entry table, with eight bytes per entry. The first two unsigned 16-bit values are the early-game and late-game resource locations. `NewRootStruct.getDrgnFile` selects the applicable alternative from current disc/chapter state. For each ordinary disc alternative, the mapper records both possibilities:

```text
disc = (packed >>> 13) + 1
environmentDirectory = (packed & 0x1fff) * 3 + 4
modelDirectory = environmentDirectory + 1
scriptDirectory = environmentDirectory + 2
```

The `+4` branch applies to packed disc indices 0–3, corresponding to extracted `DRGN21`–`DRGN24`. Both early and late records are context; the mapper does not claim both are loaded simultaneously or reachable in every chapter. [NewRootStruct](../../src/main/java/legend/game/types/NewRootStruct.java#L34), [SubmapCutInfo](../../src/main/java/legend/game/submap/SubmapCutInfo.java#L14), [DrgnFiles disc selection](../../src/main/java/legend/game/DrgnFiles.java#L94).

For submap object `i`, the geometry is `modelDirectory/(i * 33)`, and its script is `scriptDirectory/(i + 1)`. The remaining 32 files in the block are animation slots. The texture route is `modelDirectory/textures/i`. Symlinked object geometry is resolved by the runtime's `realFileIndex() / 33` logic; the catalog's existing exact canonical identities provide the shared-geometry grouping. A file existing at an object slot is not proof that it is visible or active throughout the scene. [RetailSubmap.prepareSobjs](../../src/main/java/legend/game/submap/RetailSubmap.java#L554).

The public `cutToSubmap_800d610c` source table binds cuts to location IDs; `SItem.submapNames_8011c108` supplies location labels such as Seles, Hellena Prison and Twin Castle. These are location identities, not actor identities. The current tables contain 792 cut/location entries and 791 overlay entries; the mapper uses each table's explicit bounds instead of assuming equal lengths. [Cut/location table](../../src/main/java/legend/game/submap/RetailSubmap.java#L1790), [location names](../../src/main/java/legend/game/SItem.java#L220).

## Proved party field/avatar identities

Each character template selects its world-map avatar directly from a field object file. `WMap.loadPlayerCharModelFiles` consumes the first file as the avatar geometry. This proves these names for the listed source appearances; exact canonical matches can then share the upgrade across appearances. [World-map avatar loader](../../src/main/java/legend/game/wmap/WMap.java#L1851).

| Actor | Relative field geometry | Primary source |
| --- | --- | --- |
| Dart | `SECT/DRGN21.BIN/101/0` | [DartTemplate](../../src/main/java/legend/lodmod/characters/DartTemplate.java#L74) |
| Lavitz | `SECT/DRGN21.BIN/119/99` | [LavitzTemplate](../../src/main/java/legend/lodmod/characters/LavitzTemplate.java#L63) |
| Shana | `SECT/DRGN21.BIN/119/66` | [ShanaTemplate](../../src/main/java/legend/lodmod/characters/ShanaTemplate.java#L49) |
| Rose | `SECT/DRGN21.BIN/335/132` | [RoseTemplate](../../src/main/java/legend/lodmod/characters/RoseTemplate.java#L62) |
| Haschel | `SECT/DRGN22.BIN/800/33` | [HaschelTemplate](../../src/main/java/legend/lodmod/characters/HaschelTemplate.java#L64) |
| Albert | `SECT/DRGN21.BIN/554/66` | [AlbertTemplate](../../src/main/java/legend/lodmod/characters/AlbertTemplate.java#L96) |
| Meru | `SECT/DRGN22.BIN/755/33` | [MeruTemplate](../../src/main/java/legend/lodmod/characters/MeruTemplate.java#L63) |
| Kongol | `SECT/DRGN22.BIN/800/66` | [KongolTemplate](../../src/main/java/legend/lodmod/characters/KongolTemplate.java#L60) |
| Miranda | `SECT/DRGN23.BIN/116/33` | [MirandaTemplate](../../src/main/java/legend/lodmod/characters/MirandaTemplate.java#L76) |

The unpacker explicitly calls the model at `DRGN23/506/33` Claire, and uses it to repair `DRGN22/863/33` and `DRGN24/260/33`. Those three appearances resolve to one canonical container in the prepared extraction. [Claire model repair](../../src/main/java/legend/game/unpacker/Unpacker.java#L874).

## Patch actor candidates require confirmation

The patch CSV associates descriptions with exact script paths. Combined with the object/script layout, it offers useful candidate names for additional Dart/party variants and NPCs such as Emille, Lisa, Libria, Kaiser, Bulgus and Coolon, along with knights, wardens and a cutscene arrow. However, a script description can name an actor affected by the script or describe a scene rather than the model owned by that script. Therefore the mapper records `candidateNames` and `confidence: unresolved`; it does not upgrade these to `entityName` without stronger evidence. [Patch metadata](../../patches/scripts.csv), [object/script index relation](../../src/main/java/legend/game/submap/RetailSubmap.java#L570).

Exact geometry itself is not a reliable unique person key. In this dataset the geometry associated with a `Kaiser` patch description also appears under `wounded knight`, and several knight descriptions share geometry. Material/costume differences and scene role must remain attached to each appearance. This is a reason to author reusable mesh families while retaining material/actor variants, not to collapse every match to a named person.

## World-map and shared resources

`DRGN0/5715/0` is the Queen Fury model (world model slot 1). `DRGN0/5716/0` is Coolon (slot 2), and `DRGN0/5717/0` is the teleport animation resource (slot 3). Their loader starts at directory `5714 + slot`; the gameplay branches distinguish sailing, Coolon flight and teleport rendering. `DRGN0/5714/0` is retained as a legacy avatar resource but the current load loop fetches only slots 1–3; slot 0 now comes from the character templates. Do not label the legacy source Dart merely from its filename. [Resource loading](../../src/main/java/legend/game/wmap/WMap.java#L3025), [Queen Fury slot](../../src/main/java/legend/game/wmap/WMap.java#L2480), [Coolon flight slot](../../src/main/java/legend/game/wmap/WMap.java#L2524), [teleport slot](../../src/main/java/legend/game/wmap/WMap.java#L3232).

The eight continent scene files `DRGN0/5705`–`DRGN0/5712` are bare TMD world-map scenery loads. Their continent names are known, but they are absent from the existing bounded CContainer inventory. They are therefore an explicit scope gap beyond the 1,324 rather than silently added to that count. [Map model loading](../../src/main/java/legend/game/wmap/WMap.java#L2154), [world-map names](../../src/main/java/legend/game/SItem.java#L235).

`SUBMAP/savepoint/2` is the current save-point CContainer, `SUBMAP/savepoint/4` is another recognized container under the same extracted resource but is not loaded as the current SMap save-point geometry, and `shadow.ctmd` is a shared shadow. Keep those out of the character sculpt budget. [Save-point load](../../src/main/java/legend/game/submap/SMap.java#L4070), [save-point extraction](../../src/main/java/legend/game/unpacker/Unpacker.java#L720), [shadow extraction](../../src/main/java/legend/game/unpacker/Unpacker.java#L1356).

## Authoring implications and remaining identity work

1. Start with the nine proved party field/avatar containers. Their shared uses include world-map presentation and ordinary field scenes, so the benefit extends beyond battle.
2. Review the 44 additional labelled candidate containers against their exact object/script/cut context, especially party costume and story variants. Confirm actor names and materials before writing replacement packs.
3. Resolve the 463 remaining field containers through controlled local reference views and script/cut context. A location name alone cannot distinguish named NPCs, generic people, creatures, furniture or cutscene effects.
4. Keep the 466 scene overlays in a separate environment/prop workstream. Do not schedule them as 466 character models. Preserve collision, scene overlay transforms, material addressing and animation behavior while refining geometry.
5. Extend format recognition separately for the eight bare world-map TMD files and the 80 unsupported mixed-texture references already held by the baseline inventory. Those are not covered by the 1,324 supported-container claim.

No engine files or raw extracted files were changed by this research. The mapper was run headlessly and cross-checked against every supported field reference in the existing inventory. It does not establish an exhaustive actor registry or replace visual identity review.
