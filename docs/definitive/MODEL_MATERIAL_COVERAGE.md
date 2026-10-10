# Model material coverage and next integration seam

On 2026-10-09, privately inspected the default unpacked combat-model container `models/combat/32` and combat TIM for all nine playable character asset folders. Inputs and full per-source SHA256 reports remain outside Git. This is one container per character, not every costume, Dragoon form, effect, animation or campaign scene.

| Character | Textured faces | Used palettes | Integer-raster color conflicts | Conservative sampling-cell conflicts |
| --- | ---: | ---: | ---: | ---: |
| Albert | 289 | 16 | 0 | 0 |
| Dart | 253 | 11 | 0 | 0 |
| Haschel | 313 | 18 | 184 | 190 |
| Kongol | 274 | 19 | 0 | 0 |
| Lavitz | 209 | 9 | 0 | 0 |
| Meru | 248 | 14 | 100 | 129 |
| Miranda | 346 | 16 | 0 | 0 |
| Rose | 294 | 12 | 0 | 0 |
| Shana | 225 | 18 | 0 | 0 |

The sampled containers declare neither CLUT animations nor the extra container subfile. Script-driven palette behavior and alternate assets still require native inspection. Zero conflicts is a prerequisite for the current flat-atlas experiment; it does not approve a runtime pack. Haschel and Meru need separate material addressing: the same UV texels resolve to different colors on different faces. Flattening would change their appearance.

Reproduce with `scripts/audit-model-materials.py` and `scripts/audit-uv-footprints.py` against your privately extracted matching pairs, using new output directories outside the checkout. See [versions, commands and acceptance limits](TEXTURE_UPSCALING.md).

## Private atlas prototype and proposed native extension

Prefer a packed atlas with explicit per-material UV remapping over one full high-resolution texture for every available palette. The renderer already builds floating-point GPU UVs, so a loader seam may supply an atlas transform per original face palette without changing geometry, rigs, scripts, saves or the fundamental renderer. This is a proposal: the current texture event supplies a builder only and does not expose that contract.

The private packing/pose tools now exercise this representation on Haschel and Meru, including their overlapping palette colors. Source-bound nearest controls, neural comparisons and a Haschel variant retaining original head palettes have zero raster-preview coverage differences across three views and twelve sampled keyframes each. Packing uses 11 MiB and 9.125 MiB of 4× RGBA respectively, versus 72 MiB and 56 MiB for full layers of their used palettes. These are allocation estimates, not runtime residency or performance. Flat atlases remain potentially cheaper for non-conflicting pairs. [Commands, source identities, art observations and limits](TEXTURE_UPSCALING.md#separate-material-atlas-prototype).

1. Bind a private pack to original TIM **and model** identities, dimensions, disk/cut/object or character variant, CLUT layout, tool/model versions, atlas hashes and the complete material map. Match before UV relocation or packet mutation; filenames alone are insufficient.
2. Extract used regions with verified sampling coverage and padding. Separate conflicting palettes into different atlas rectangles. Preserve STP/discard and primitive translucency. Reject animated palettes until they have a tested representation.
3. Map each face's original CLUT/UV into its rectangle at GPU-vertex construction through a small loader hook. Existing overrides and absent/invalid maps retain their existing route. Do not replace character registries for presentation.
4. Initially retain nearest filtering. Verify boundaries, translucent black, seams, texture lifetime and mod overrides in native field and battle scenes. One malformed material must preserve original rendering rather than partially remap a model.
5. Bound immutable asset caches and record resident bytes, decode/upload costs and duplicate references. Compare packed crops with full palette layers before choosing. High-resolution RGBA costs more than indexed TIM storage.

Start with the inspected field sample and original versus nearest controls, then a conflicted character such as Haschel. Require native idle/walk/combat comparisons and physical Deck measurements before defaults. The battle atlas path needs its own integration audit; the field-object event alone does not supply it.

The [native integration source audit](NATIVE_MATERIAL_INTEGRATION.md) now traces both routes, including shared part-table/GPU-mesh ownership, pre-mutation source identity, RGBA versus indexed texture units, STP blending and color-quantization differences. It defines a bounded cross-language validator and native control harness as the next implementation steps.

## Art direction and community review

The owner subsequently reviewed the embedded Haschel/Meru studies and rejected the existing enhancements as too subtle. The chosen direction is modernization of the original designs with much clearer faces, richer materials and smoother models. [Stronger textures and surface feasibility study](CHARACTER_REMASTER.md) makes that gap explicit; no current candidate has artistic approval or is promoted to the default preset.

Use original faces, costumes, weapon silhouettes and Skurfa's painted world as references. Keep source-size eye, mouth, buckle and emblem crops alongside enlarged views; smoother pixels can erase markings. Preserve originals in sensitive regions when reconstruction changes their meaning; use hand-authored restoration where warranted. Texture enhancement does not add geometry or fix joints.

Present equal-scene controls with neutral labels, disclose resolution/performance tradeoffs, and collect reasons for preference: identity, paint, material readability, seams, motion stability and handheld readability. Community preference has not been collected. Do not call a candidate community-approved or AAA-ready from a texture sheet, build or developer preference alone.

Reviewed source `3fad47641963b81b5f0be0db3431ab10cca38158` passes fifteen original synthetic visual checks and Linux/Mac build/package jobs in [CI run 37999771780](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/37999771780). The corrected field candidate has zero coverage differences in the limited posed/keyframe inspection. Native replacement, real Deck performance and community acceptance remain open.

The subsequent packing increment `ea6004eb3017abdac1941895cacede6ce1885407` adds five original synthetic checks (twenty total), including contrasting texels immediately below fractional UV boundaries. Standards review found no actionable issue; Spec review found the UV cancellation defect, which was fixed, tested and re-reviewed successfully. All twenty visual checks and Linux/Mac build/package jobs pass in [CI run 38002119371](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/38002119371). The published alpha remains unchanged.

The private viewer increment `6d698a1b1e30bf35328cddb701d7539bc9e7993f` passes twenty-three original visual checks and Linux/Mac build/package jobs in [CI run 38003732991](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/38003732991). Its browser QA covers 1280-, 736- and 320-pixel widths, source-bound image selection, reveal/zoom clipping, keyboard controls and long labels. It remains an offline raster review tool.

The [Java preflight increment](JAVA_MATERIAL_PREFLIGHT.md) adds an independent reader for the same private standard-TIM material format. It introduces no engine or manager behavior change. Seven original Java fixtures and five cross-language fixtures check the contract, alongside thirty-one delivery tests in total. Private Python and Java validation agree on 25 packs: 2×/4× nearest controls for the nine sampled characters, plus seven existing Haschel/Meru nearest, full-neural and curated variants. All 31,388,368 material texels across those packs pass STP/discard/visible-black checks and the applicable original/preserved-pixel checks. This count includes repeated controls; it is not unique game coverage. Full inputs, commands, Java environment, source hashes and validation records remain in the private round-21 report. Native rendering, gameplay, Deck measurements and community art review remain open.

Preflight source `f2b3a8d5b1d0f12ea673d8fe2b134848d3745bf3`, included in the subsequent bootstrap-correction source `bb8d5559b67ec61698033b22e808bcc8b59dd0c5`, passes Linux/Mac package CI plus the complete delivery/visual/cross-language checks in [run 38006083689](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/38006083689). Final private report SHA256 is `39c7b4d0a13808a2b323c920153b74035fa906df4180451647ebe654338d12f6`; it records the exact Java files used for the twenty-five pack checks. The bootstrap correction updates only the public entry wrapper and checksum inventory, preserving the alpha's existing engine/portable/script payloads.
