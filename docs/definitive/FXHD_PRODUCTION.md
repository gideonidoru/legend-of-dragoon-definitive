# Full FxHD production status

The field-only milestone has been superseded by full retail FX coverage. All 6,466 audited texture-source bindings are selected through 2,160 unique 2× neural-derived palette detail PNGs. The texture source denominator is distinct from visual effect names, repeated source hashes and unique jobs. The six prior RGB field replacements remain selected and take draw priority where their original immutable binding still matches.

| Source family | Bound sources |
| --- | ---: |
| Common battle particles | 40 |
| Dragoon extra textures | 369 |
| Dragoon spells and transformations | 2,037 |
| Item spells | 466 |
| Enemy and boss attacks | 2,628 |
| Cutscene extras | 187 |
| Battle cutscene families | 721 |
| World atmosphere and smoke | 4 |
| Title fire | 4 |
| Field, save-point animation, sparks and shadows | 10 |
| **Total** | **6,466** |

Public source/output/tool hashes are in integrations/fxhd/production/full-coverage.json. Its 4,823 distinct encoded-hash bindings cover every source path; 2,160 deduplicated outputs contain 42,080,098 bytes. Of those, 2,130 change subtexels; 30 retain the native flat/no-improvable palette result rather than inventing colors. The source census includes 137 valid padded/nonstandard TIMs omitted from the earlier CSV, plus world and title assets. Optional extra roots4119, 4126 and 4127 are absent in retail extraction and explicitly guarded by Loader.isDirectory. Non-TIM metadata/support files are separately counted.

The pinned Real-ESRGAN x4plus pipeline uses clamped padding and a shared 65% neural/35% native blend, reduced to 2×. Runtime controls store the original palette index, a neighboring palette index and a bounded mixture amount; the engine resolves both colors from the live CLUT. Original indexed data determines STP, discarded pixels and visible black. Masks are not inferred. All original controls, RGB review images, inference inputs/logs, executables and weights remain outside Git. Native palette animation changes the detail colors naturally; image writes clear only affected detail words.

The public consumer-coverage.json assigns all 158 effect/particle implementation and support sources plus field/world/title/load dispatchers explicit routes, with zero unclassified entries. Textured sprite, TMD, battle-TMD and quad/model particle families receive shared indexed detail. All 65 particle behavior implementations dispatch through those texture or procedural renderers. Procedural lightning, rain, weapon trails, gradients and pixel particles already render at the configured internal resolution with existing SMAA/reduced-flashing settings. Capture/distortion samples the current renderer framebuffer. Controllers modify existing geometry/scissor/animation; they have no separate texture asset. Protected lettering, numbers and reticles belong to UIHD; body/prop and environment art retain ModelsHD/CharHD/EnvHD/Skurfa ownership.

The three native shader paths preserve existing packed-page/nibble addressing and zero-X overrides. Earlier explicit HD textures and protected UI queues take priority. Atomic image upload creates a provisional owner before uploading the CLUT, so overlapping palette writes invalidate it normally. Owner tokens and mod-selection epochs reject stale callbacks. Texture copying mirrors native masked writes and overlapping traversal. CPU row rotations preserve title/water animation, and two bounded world-map snapshots preserve effect pages across inventory overwrite/restoration. Disable/re-enable patches live owners and saved snapshots; obsolete callbacks cannot resurrect overwritten regions. Context recreation reuploads the persistent companion on the renderer thread.

One R32UI GPU companion is fixed at 16 MiB. CPU ownership and source offsets use 4 MiB, with a lazy 16 MiB detail array. Upload staging is at most 16 MiB for initial/recreated full upload and smaller dirty-row ranges afterward. Two CPU backup regions each retain at most 32,768 native words, totaling at most 2.5 MiB; transient row rotation can coexist with both backups and uses at most another 2.5 MiB of snapshot/clones plus 128 KiB of native words. Shared image decoding follows the Graphics cache setting and its engine budget; source descriptors retain hashes/dimensions/reference counts, never original files or palettes. Existing six field GPU images add 80 KiB. JVM/driver overhead and physical Deck frame time/power are unmeasured.

Validation includes complete private-source encoded hash/dimension/native-index correspondence, synthetic padded TIM/mask/census/import rollback fixtures, CPU VRAM partial/masked/overlapping writes, signed row rotation, snapshot restoration and mod changes, shader transparency/lighting/live-palette/UI/stale-control fixtures, full build and incremental package verification. Every original source must resolve selected detail before the complete payload can pass. The exact results and reviewed source commit are recorded in the integration PR; source/CPU/synthetic shader evidence is separate from native all-campaign gameplay and physical Deck acceptance.

FxHD-v1.0.0.jar is built and staged by the standard seven-module installer. Only selected runtime controls and the adapter/notices enter its JAR. Production ledgers/candidates are public repository evidence; original source controls are excluded. Current-only combined release publication remains the orchestrator's responsibility. The old field-selection ledger and candidates retain their historical provenance; they no longer define the full scope or a future batch queue.

Native gameplay, complete scene appearance, busy-scene costs, suspend/resume and physical Steam Deck acceptance remain pending. Full source coverage is not a claim that every effect looks equally improved or has received player visual approval.
