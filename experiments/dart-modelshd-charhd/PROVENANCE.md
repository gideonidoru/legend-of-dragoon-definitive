# Experimental Dart assets and code

All work here is a development candidate, with **explicit owner approval required before main or release**. The owner authorized publication of custom Definitive meshes and HD artwork. Original source models, TIMs, animation controls, disc images, saves, credentials and tool runtimes are excluded.

Code and custom geometry contributions follow the repository's AGPL v3 license. Preserve Severed Chains and third-party notices. Legend of Dragoon character designs and underlying game artwork retain their original ownership; this project does not claim that they are public domain or grant rights to the original game. Custom asset publication permission is separate from visual acceptance.

## Character texture snapshots

The `charhd/restoration/combat` snapshot comes from the concurrent CharHD production checkout's Dart 4x candidate. It is deliberately copied as a fixed experimental input; this branch does not edit that checkout or adopt its unfinished engine changes. The field pack was generated using the same pinned Real-ESRGAN x4plus-anime route, palette isolation, fourfold scale and 0.75 strength. Model, TIM, neural weight, parameter, tool and output hashes are recorded in the manifests. The original STP/discard encoding, visible black and duplicated palette gutters are retained. No inference executable or weights are shipped here. Existing project neural-tool attribution remains applicable.

The field atlas was flattened only after checking all used palette footprints: **zero conflicting pixels**. Its source dimensions are 64×112; the replacement is 256×448. Runtime UV normalization continues to use the original logical dimensions. Battle material palettes conflict, so their 2048×1792 atlas requires the separate CharHD material-map engine integration. The battle atlas is a validated comparison input here; native battle body-atlas playback is not claimed.

## Face paint

`resources/charhd-experiment/dart-face-paint-v1.png` is a generated template-aligned paint, 1254×1254 pixels. The generation used a private orthographic original-head reference. The reference and raw original head are not committed. The paint is mapped to explicitly selected native source faces using the head's bone-local coordinates; ModelsHD descendants retain their source-face lineage. It is an authored supplemental albedo layer, not a neural renderer running during gameplay.

Generation direction: preserve the template's composition and feature alignment; Dart as a youthful male with clear blue eyes, golden brown hair and red headband; polished retro AA character paint; defined eyebrows, nose and closed mouth; restrained neutral baked shading. Exclude photographic pores, makeup, facial hair, teeth, aging, recentering, a 3D mockup and new accessories. The delivered paint is a candidate interpretation and requires design review. A generated flat texture alone is not evidence of a successful model.

The first vertex-color baking attempt was rejected because it blurred eyes and mouth. The retained implementation samples the paint at full texture resolution, using a separate texture unit, while the original body texture stays on its existing route. Body UV offsets and normal/roughness maps do not reinterpret the face UVs. Authored face pixels are opaque, including black; ordinary source textures keep their existing PSX STP behavior.

## Custom mesh payloads

`models/parts` contains only the 12 changed source-bound custom part packs, approximately 1.1 MB total. The unchanged original controls remain private. The generator reads the current ModelsHD 0.4 baseline, uses bounded facial landmarks and mild armor/glove/cloth fullness, fixes open attachment boundaries, validates winding and nonzero triangle area after float32 conversion, and retains rigid part order and original keyframes. It is not a new skeleton, a fully remodeled character or a finished retopology.

The current hair spikes, hand construction and much of the side silhouette remain visibly coarse. Improving those deliberately is the next art step, rather than increasing every part's subdivision level. The face layer also needs better side/ear transitions and actual scene-light review. No community acceptance or real Deck performance is asserted.
