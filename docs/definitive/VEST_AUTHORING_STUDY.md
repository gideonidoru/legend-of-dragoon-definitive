# Haschel vest authoring and filtered color

October 10 follow-up to the [held surface-response experiments](MATERIAL_RESPONSE_STUDY.md). The new work changes costume artwork and garment forms rather than continuing to increase shader strength. It produces a clearer woven-cloth and ivory-ornament treatment on the actual character model. **The artwork still needs seam/material review, and both sculpted vest candidates are held for recorded contacts. Nothing in this study is installed or shipped.**

The owner subsequently selected the stitched treatment as the preferred direction. The [head/neck refinement](NECK_ALIGNMENT_REFINEMENT.md) responds to the excessive head turn and broken neck-base transition while preserving the costume. Native acceptance remains open.

## Authored vest forms

Two local candidates reshape the existing 1,033-vertex, 2,048-triangle torso. Explicit chest, cinched-waist and converging-fold zones change positions; source-edge distance falloff tapers those changes around the retained anchors. They use maximum displacements of approximately 17.9 and 31.8 original model units. Topology, original UVs, per-face colors and palette ownership remain fixed, and smooth normals are regenerated. This is deliberate local mesh authoring, not generated geometry, cloth simulation or a new animation system.

Both saved candidates retain all 30 original anchors across 496 recorded poses each: 992 pose checks and 29,760 direct original-anchor comparisons, with maximum error zero. They add no tested nonadjacent local self-crossings, duplicate faces, disconnected fans or winding defects. The original torso has a 16-edge neck opening. All 16 boundary vertices remain exact. The first verifier incorrectly required a closed surface; its failure is retained, and the corrected verifier checks the actual original opening. The signed triangle-volume calculation is diagnostic for this open surface, not enclosed physical volume.

The stronger shape gives the vest a fuller chest and a more intentional waist, but source anchors and good local topology do not clear contacts with neighboring parts.

## Recorded contacts remain held

A separate audit checks both candidates against the continuous arms, both gloves and the original waist sash across all 496 recorded poses: **4,960 variant/pose/pair checks**. It compares identical-topology triangle IDs with the preceding torso. The 1,583 potentially changed torso triangles are checked; the other 465 triangles retain exact geometry and cannot introduce a changed crossing against the same neighboring mesh. Repeated matrix states share calculations but every recorded sample retains its own result.

| Torso neighbor | Checks with added triangle pairs, lighter / stronger |
| --- | --- |
| Left continuous arm | 496 / 496 |
| Right continuous arm | 496 / 496 |
| Left glove | 0 / 0 |
| Right glove | 79 / 78 |
| Waist sash | 36 / 48 |

The preceding torso already has crossings with both arms and some glove/sash poses. These results describe additional triangle-pair crossings, not new visible defects, penetration depth, source-parent pair counts or contact-free movement. They cannot be compared numerically with the clothing study's source-parent counts. Neither candidate is cleared by attractive standing views. No failing pose is dropped, and no pose-specific original-mesh fallback is used to hide a failure. A static interface repair and further visibility/motion review remain necessary.

## Neural-assisted costume color

Built-in image generation reauthors two original purple/ivory UV tiles as woven cloth and stitched appliqué. The prompt preserves the original panel layout, purple/navy color family, curved ornament and dark openings, while requesting clearer material construction. The second tile uses the first only as a finish reference. Source UV tiles were supplied to that image tool; this is a different authoring route from the earlier entirely local NCNN experiments. All mesh work stays local.

Generated detail and motif placement require art review; a prompt is not proof that every original feature is registered perfectly. Raw generated PNGs, exact prompts, source/result identities and the baked engine tiles remain private. Image generation is nondeterministic. Given the retained PNGs and pinned processing environment, the atlas bake is repeatable; reproducing the exact generated pixels from the prompt alone is not claimed. No new symbols, asset-rights transfer or community approval is asserted.

Only the torso's two corresponding material references are redirected. Free palette slots 2/3 and verified unused space beside the head atlas hold the new tiles. The original palette tiles remain available to every other part. The new tiles use 6× and 8× source scale, respectively, while the base atlas stays **2048 × 2432, 19,922,944 RGBA bytes**. UV units are retained through each tile's origin and scale. No extra base texture allocation is required by this bake.

Original nearest source data supplies the binary STP bit and black-discard mask. Generated alpha is not trusted. The bake restores original black regions and prevents newly black RGB from discarding originally visible non-STP pixels. Independent saved-input checks cover all 570,880 semantic texels in each candidate atlas. All non-torso attribute rows and original atlas bytes outside the two new tiles remain exact.

## Filtering the new detail

Nearest sampling makes the woven detail noisy in the first model renders. A private shader therefore uses trilinear mip-filtered RGB for the two new garment palettes while making discard and STP-pass decisions from their nearest source-encoded texel. Other materials retain their existing fetch path. Explicit UV derivatives are computed before discard and varying control flow and passed to `textureGrad`, which is part of the existing GLSL 3.30 contract. See the [Khronos specification](https://registry.khronos.org/OpenGL/specs/gl/GLSLangSpec.3.30.pdf), section 8.9.

Five original synthetic cases compare nearest and filtered output: constant color, a fine checker, discarded black, visible STP black and a partially discarded block checker. Known 2× minification and independently calculated level-one interpolation give exact expected filtered color in the covered samples. Each pair retains identical coverage and depth. The first checker assumed the fine discard pattern would leave covered pixels; this particular nearest-sampling grid selects only black and correctly discards all of it. That checker error is retained, and the added block case exercises 256 covered pixels without changing the shader.

This prototype generates mips for the complete RGBA8 atlas. Its base storage stays fixed, but the additional levels total **6,640,948 bytes**, for **26,563,892 bytes** across all levels. These are storage calculations, not measured driver residency or upload peaks. Filtering currently operates on encoded RGB; gamma-correct material filtering, distant atlas-border isolation, blending, animated transitions and physical Deck cost remain open. Stable still captures do not prove freedom from shimmering or mip bleeding.

## Capture and presentation evidence

| Comparison | Verified result |
| --- | --- |
| Vest shapes | 48 full-character captures; 32 preceding PNG/depth controls reproduce exactly; only torso positions/normals change |
| Reauthored color | 48 captures; 32 preceding controls reproduce exactly; all 16 paired art comparisons retain exact alpha and depth |
| Filtered garment color | 64 captures; all 48 preceding PNG/depth controls reproduce exactly; all 16 filtered comparisons retain exact alpha and depth |
| Costume-detail framing | 48 shared-camera 600 × 800 captures; only viewport and XY projection change; eight indexed/nearest controls retain equal coverage and at most one channel step across 2,305,456 covered samples |
| Filter fixtures | Ten captures covering five independent color/coverage cases; nearest/filtered depth and coverage remain exact |

All input, manifest, PNG, decoded RGBA and float32 depth hashes are checked. Hidden GPU runs complete cleanup. The character remains 20,446 triangles with a 7,851,264-byte expanded diagnostic vertex stream. No larger mesh or extra normal/roughness texture is added by the costume bake.

The private viewer embeds unedited model captures, offers costume detail/full-character framing, an original-model comparison, eight poses and three staged background sizes. Hidden offline browser checks pass at 1440, 1280 and 390 pixels with keyboard activation, at least 44-pixel controls, no horizontal overflow, no page errors and no network requests. Actual detail, original-reference and 160-pixel staged layouts were inspected. The original-model comparison changes head, body and surface response as well as costume art; it is not a single-variable material comparison. The manually placed Skurfa background reference still lacks native camera, occlusion, contact shadows and scene lighting.

The study uses source commit `607600ebacc06b5559ef0a6b77b7b8fb7bce6356`, private Python 3.12/NumPy 1.26.4/Pillow 10.1, Java 25 and hidden Apple M5 Max OpenGL 4.1 captures on macOS 27.0.1. Preparation, checking, shader/probe sources, commands/logs and generated input identities are retained privately. The public runtime pilot's supported scales and behavior remain unchanged. No engine, installer, launcher, updater, release package, game image, save, credential or runtime is modified or committed by this study.

## Next acceptance work

Make adjoining costume materials and UV transitions coherent, then repair the vest's recorded interfaces without losing its intended form. Follow with glove/finger and boot detail, head/neck material transitions and shared native scene lighting. Integrate through the existing owned mesh/material contract with whole-actor faithful fallback. Finally measure physical Deck frame time, memory, transitions and power and obtain community comparison feedback before selecting defaults. The new artwork is progress toward the modern AA retro target; it is not yet proof of commercial finish.
