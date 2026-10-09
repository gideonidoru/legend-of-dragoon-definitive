# Native material integration audit

Inspected source `ea6004eb3017abdac1941895cacede6ce1885407` on 2026-10-09. The separate-palette atlas is currently an offline comparison format. This audit identifies the next implementation boundaries; no native adapter or changed rendering behavior is delivered by it.

## Existing routes

| Responsibility | Current source | Integration consequence |
| --- | --- | --- |
| Raw field models, aliases and original TIMs | [`RetailSubmap.prepareSobjs`](../../src/main/java/legend/game/submap/RetailSubmap.java) | Capture the raw model identity before constructing `CContainer`. Virtual model references share containers; texture references also share `Tim` instances. |
| Field texture override event | [`SubmapObjectTextureEvent`](../../src/main/java/legend/game/modding/events/submap/SubmapObjectTextureEvent.java) | Existing contract supplies texture builders only. A packed override needs a texture and its validated UV map together; a later mod must never leave the map paired with a different texture. |
| Field override upload and mesh creation | [`RetailSubmap.prepareSobjModel`](../../src/main/java/legend/game/submap/RetailSubmap.java) | Existing PNGs use original TIM dimensions for UV normalization. Packed rectangles require their own transform and atlas dimensions. |
| Packet parsing and model preparation | [`TmdObjTable1c`](../../src/main/java/legend/game/tmd/TmdObjTable1c.java), [`Models.prepareObjTable2` / `adjustModelUvs`](../../src/main/java/legend/game/Models.java) | Parsing changes packet ILEN fields; model preparation shares part tables and later changes UV/CLUT/TPAGE packets. Hash the original bytes first and resolve material identity before relocation. |
| PNG type conversion | [`UvAdjustmentMetrics14.PNG`](../../src/main/java/legend/game/tmd/UvAdjustmentMetrics14.java) | Changes texture bit depth while retaining relative CLUT and blend-mode bits. Atlas addressing should use a validated original palette identity, without stretching 8-bit packet UVs to high-resolution atlas coordinates. |
| GPU vertex construction | [`TmdObjLoader`](../../src/main/java/legend/game/tmd/TmdObjLoader.java) | The existing 24-bit route writes normalized float UVs. A narrow mapping input can transform each face's original UVs at this point while preserving positions, normals, colors, translucency and adjacency. |
| Field rendering and release | [`SMap.renderSmapModel` / script destructor](../../src/main/java/legend/game/submap/SMap.java) | Field RGBA textures bind on unit 0 and are released with object destruction. Keep any new ownership/cache contract explicit across scene transitions. |
| Battle source textures and models | [`Battle.loadCombatantTim` / `loadCombatantModelAndAnimation`](../../src/main/java/legend/game/combat/Battle.java) | Indexed TIM uploads and VRAM relocation are separate from model loading. The field event does not integrate combat materials. |
| Battle texture binding | [`BattleEntity27c.renderBttlModel`](../../src/main/java/legend/game/combat/bent/BattleEntity27c.java) | Optional per-combatant VRAM currently binds on unit 1. A new RGBA atlas must use the 24-bit sampler on unit 0 while retaining indexed VRAM required by other rendering/effects. |
| Fragment semantics | [`tmd.fsh`](../../gfx/shaders/tmd.fsh), [`battle_tmd.fsh`](../../gfx/shaders/battle_tmd.fsh) | Both shaders have an RGBA sampler route, all-zero discard and nonzero STP tests. Primitive translucency remains separate from conventional PNG opacity. |
| Texture defaults | [`TextureBuilder`](../../src/main/java/legend/core/renderer/TextureBuilder.java) | Defaults are unfiltered and wrapping. Set nearest filtering and clamp explicitly for the experiment; padding alone is not a linear/mipmap acceptance test. |

## Ownership before activation

`prepareObjTable2` assigns the container's part-table references directly to model parts. Field setup tracks visited containers before applying UV adjustments, and each part table caches its GPU object. A per-object material map cannot safely assume it owns these packets or cached meshes. First prove that every shared owner uses the same verified map, or give the optional presentation its own mesh ownership without modifying the shared original. A first native pilot may reject shared-container groups until that representation is tested. Do not infer alias safety from matching filenames or source hashes.

Use an immutable validated material selection, rather than independent texture/map lookups. Preflight the entire model and source TIM before changing any GPU resource. Existing mod overrides retain priority. Unknown sources, malformed packs, animated CLUTs, extra dependencies and absent mappings retain the original route as a whole. Separate experimental opt-in from Faithful mode and from the published single-palette pilot.

The private inspector quantizes scaled UVs before integer atlas translation to avoid a demonstrated floating-point boundary cancellation. Native normalized UV interpolation has its own precision behavior and needs real adjacent-texel/seam checks. Its success cannot be inferred from the Python fix.

Palette colors also need an explicit comparison tolerance: the original shaders decode five-bit channels as floating-point fractions of 31, while the current PNG tools encode eight-bit channels using integer `value * 255 / 31`. Byte-exact preserved PNG materials mean exact agreement with that decoded eight-bit control; they do not prove bit-identical native indexed rendering under lighting. Faithful mode continues to use the original indexed renderer. Native control comparisons must distinguish color quantization from material misaddressing.

## Next implementation and proof

1. Read and validate the bounded private atlas format in Java against unchanged model/TIM identities. Start with standard TIMs, and retain rejection of the SC field padding variant until a separate strict decoder check is added. Verify full palette references, atlas bounds/nonoverlap, scale, STP/discard/visible black and explicitly preserved pixels. Cross-check the same synthetic and private control artifacts with both languages.
2. Add the smallest typed UV-map input at vertex construction with old callers unchanged. Preflight all faces before mesh replacement, and establish per-owner mesh lifetime. Test original versus nearest controls with contrasting palette reuse, fractional UVs, missing maps and mod precedence.
3. Run an explicitly opted-in native comparison harness with the current shaders, source geometry and original keyframes, before integrating scene loading. Record screenshots, sampler state, blend modes, source identities and resource counts. Keep private images and extraction outside Git.
4. Integrate field and combat routes independently. Test idle/walk/combat, transitions, teardown, aliases and existing overrides. Then profile resident bytes, decode/upload time, p95/p99 frame time and power on an actual Deck at 1280×800.

Original-art comparisons, scene integration, physical Deck measurements and community acceptance remain open. The available native RGBA route makes an engine rewrite unnecessary for this first material experiment; it does not establish finished lighting, new geometry or a completed visual remaster.
