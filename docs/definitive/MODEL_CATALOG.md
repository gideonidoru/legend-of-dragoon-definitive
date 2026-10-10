# ModelsHD: the complete supported model catalog

**All 1,324 remaining canonical geometry containers are cataloged.** The [offline atlas](model-catalog/index.html) is searchable by actor, candidate name, location, encounter, source reference and stable model ID. The [CSV authoring sheet](model-catalog/model-catalog.csv) is for scheduling; the [complete JSON](model-catalog/model-catalog.json) retains every appearance, evidence record, geometry measurement and reuse lead.

To use the atlas from GitHub, download the **Raw** `index.html` file and open it in a browser. Its catalog is embedded and search works offline; no game files or local server are needed. Download the CSV/JSON separately, or use the repository checkout to keep its sibling links available.

This is a complete map of the existing supported inventory, not a claim that every object has a confirmed character name or that all game formats are covered. The catalog contains public custom metadata only. Original meshes, textures, scripts, source-control comparisons, disc images and private paths are excluded.

## What the map establishes

Snapshot: October 10, 2026, based on Definitive source `10acb3b6b76067d805bb0ba42bb1e65545fca46b`. The generated catalog records its source revision, input-inventory hash and tool hashes. The engine was not changed for this milestone.

| Result | Containers |
| --- | ---: |
| Remaining supported canonical geometry, excluding the shipped 19 battle forms | **1,324** |
| Verified loading context or source identity | **1,294** |
| Flat archive containers with caller/actor unresolved | **30** |
| Containers with source-proved entity labels | **230** |
| Field object geometry | **517** |
| Field scene overlays, separate from the field objects | **466** |
| Battle combatant/stage union | **305** |

There are **7,544 source appearances**. All of their bytes were checked locally against their inventoried canonical geometry, not only a representative file. The 230 labeled containers include props, vehicles and named multipart combatants; they are not 230 complete characters. There are 220 distinct source labels. Context categories can overlap through shared geometry: do not add the last three rows as a disjoint count. [Field evidence](MODEL_FIELD_MAPPING_RESEARCH.md) and [battle evidence](MODEL_BATTLE_MAPPING_RESEARCH.md) explain the source routes and naming limits.

### The four expansion phases

| Phase | Verified scope today | Next authoring decision |
| --- | --- | --- |
| 1. Party field/story/world-map models | Nine named party field/avatar containers; one additional legacy avatar route with no proved actor name. Story/costume variants may occur among the unresolved field objects. | Begin with the nine proved field models, then confirm party variants by their scene and script context. |
| 2. Major NPCs | Claire is source-proved. Across the 517 field objects, 44 additional containers carry tentative actor labels; 463 have no actor label yet. Those sets include party variants, people, creatures and props. | Identify prominent story actors before setting the NPC sculpt count. Do not treat a patch's mention of an actor as proof of the model's identity. |
| 3. Bosses/dragons/story combatants | 101 supported containers in special/story combatant contexts. A positive script audit establishes boss-action context for 13 of these; the other 88 remain story/special context. | Rank encounter families and multipart bodies; verify each phase, detached part and transformation. Absence of a boss-action call does not exclude a boss. |
| 4. Common enemy families | 128 supported containers occur in the ordinary enemy bank. | Use measured component reuse to choose family masters; retain palette, material, anatomy and animation exceptions. |

These are context counts, not four final disjoint sculpt totals. The atlas assigns one scheduling bucket per container, in party → NPC → special combatant → ordinary enemy → scenery → unresolved order, while retaining all its contexts. Those exclusive bucket counts are **10 / 1 / 101 / 125 / 551 / 536**, totaling 1,324. Three ordinary-enemy geometries also have special-bank context, explaining 128 contexts versus 125 exclusive enemy jobs. The single NPC bucket is a conservative confirmed identity, not a claim that the game has only one NPC model.

The field route includes cut/location, disc alternative, object index, paired script, texture reference and source evidence. All **6,650 supported field appearances** route successfully, across 731 cuts. The 466 scene overlays belong in a separate scenery schedule; they should not become 466 character remodeling jobs.

## Reuse that can save authoring effort

- **Eight whole-shape groups** share exact ordered positions and indexed connectivity across separate canonical identities. Normal/material differences remain attached to the source identity.
- **32 indexed-topology groups** suggest compatible structures to investigate. Matching connectivity alone does not prove the same character, proportions or animation contract.
- **1,876 shared component groups** identify exact part geometry across containers.
- **160 substantial component-reuse pairs** share at least 100 triangles and at least half of each model's nontrivial triangle weight. Tiny helpers are excluded, and repeated components use multiset matching. These comparisons also include the existing 19 party battle forms as references.

Use these leads to share a body or component authoring task, then validate the original part ordering, pivots, motions and material ownership separately. Never promote a name merely from a shape match. A shared head/body can belong to different costumes, characters or scene roles.

## Dependencies and limits

**620 containers carry auxiliary data or palette-animation pointers.** Define and test the relevant geometry/material contract before replacing them. The topology audit flags 208 containers with non-manifold edges and 124 with collapsed triangles. These are inspection signals; they do not prove the retail asset is broken and are not permission to blindly subdivide it.

Battle replacements have an existing ModelsHD adapter, subject to its per-model limits. Field, scene-overlay and world-map replacements require appropriate hooks that preserve script-visible parts, transforms, occlusion and material ownership. The catalog records these dependencies. Keep CharHD and other texture replacements in control of their active images, UVs, palettes and blending. This map does not establish compatibility for new field/scene adapters.

The original inventory holds **80 mixed-texture-depth references** outside the current decoder. Eight bare world-map TMD scenery loads and DEFF-wrapped effect geometry require separate format inventories; they are outside the 1,324 claim. The 30 unresolved flat resources have no proved caller, so neither “unused” nor a specific actor identity is justified.

## Recommended work order

1. Finish the current 19 battle-form visual and gameplay gates. Use whole-character silhouettes, faces, anatomy and motion as the acceptance target.
2. Add a field/world avatar hook and upgrade the nine proved party field models. Keep the original textures and CharHD selectable; test close dialogue, walking, transitions and world-map movement.
3. Review the 44 candidate-labeled field containers, prioritizing party variants and major NPCs. Record a human-confirmed identity and evidence per appearance; review the other 463 by location and screen importance rather than archive order.
4. Build boss-family packages around prominent encounters and measured component groups. Treat multipart anatomy, animation palettes and phase behavior as separate validation tasks.
5. Author common-enemy family masters and their exceptions. Validate attacks, damage, death and crowded encounters before extending coverage.
6. Schedule visible 3D props/overlays separately and expand format coverage. Leave caller-unresolved resources lower priority until their use is established.

Custom replacement geometry belongs in the public ModelsHD mod and normal Definitive installation. Original data remains local. This milestone publishes the planning catalog; it adds no new replacement meshes or engine behavior.

## Reproduce and verify

Use Python 3.10+ with the pinned offline authoring dependencies in `scripts/requirements-visual.txt`. `$EXTRACTED_FILES` points to privately prepared game data; `$INVENTORY` points to the local inventory JSON. Neither input is published.

```sh
python scripts/inventory-modelshd-sources.py --files "$EXTRACTED_FILES" --output "$INVENTORY"
python scripts/catalog-modelshd-sources.py --inventory "$INVENTORY" \
  --files "$EXTRACTED_FILES" --output docs/definitive/model-catalog
python scripts/test-model-catalog.py
node scripts/test-model-catalog-ui.cjs
```

The independently reproducible Java 25 role scanner and its metadata-hash checks are described in [battle evidence](MODEL_BATTLE_MAPPING_RESEARCH.md). Rerun that scanner when its engine functions, metadata or source scripts change; stale proof fails closed.

Verified locally on the Mac Studio (`Mac17,14`), macOS 27.0.1 (`26A434`), Python 3.12.0, NumPy 2.5.1, Node 26.5.0 and Corretto Java 25: every supported alias matches its expected canonical identity; exactly the 19 core models are excluded; every remaining identity appears once; all scheduling buckets reconcile. The role audit decoded 402 scripts with zero failures and found 16 positive boss-action scripts with no warnings. Arena precedence and geometry deduplication reduce the supported boss-context subset to 13 containers. Seven synthetic tests exercise changed aliases, path escape, candidate-name boundaries, component weighting, script-safe HTML and stale/invalid role evidence. The offline explorer's filters, pagination, route search, lazy evidence details and empty state pass headless behavior checks.

These results establish catalog integrity and source routing. They do not establish visual quality, complete actor naming, animation compatibility, gameplay acceptance, community approval or Steam Deck performance. Actor review and per-family runtime validation remain explicit next steps.
