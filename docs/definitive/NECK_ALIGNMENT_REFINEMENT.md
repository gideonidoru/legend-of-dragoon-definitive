# Haschel head alignment and neck refinement

October 10 follow-up to the [stitched costume study](VEST_AUTHORING_STUDY.md). The owner selected **stitched costume** as the preferred direction and rejected the excessive head turn and visible hard edge at the neck base. The private comparison now keeps that costume and revises the head placement, lower-neck form and skin response. **This is an improved authoring preview, not an installed replacement or final anatomy/animation acceptance.**

## Diagnosis and revised fit

The preceding width/top fit retained a portrait mesh ending over the source torso's original neck. Their separate shapes, normals and finishes exposed an abrupt transition. In the resting recorded pose, the head and torso frames differ by approximately 62.55° in combined three-dimensional rotation. Matching source bounds and applying the recorded transform correctly had not established a convincing anatomical attachment.

The new private rig interpolates the head's relative rotation toward the torso, retaining 38% of its rotation vector and bounding the resulting combined angle to 40°. The resting value becomes 23.77°. These are frame-rotation measurements, not just yaw, anatomical limits or evidence that the original animation was anatomically invalid. This adjustment also attenuates relative pitch/roll and requires review against intended combat gaze before native use.

A further fixed 18° local yaw correction addresses the portrait mesh's own turned face within the original fit basis. That authoring angle is not a landmark-registration proof. Both the face and retained rear pieces use the correction.

A manually authored head-base anchor is placed relative to the collar, with the original pose's relative translation changes retained. The lower central skin region blends from the revised head frame to saved torso-space targets derived from the costume's actual 16-point collar outline. The jaw and upper face remain rigid; UV seam duplicates share the same positional binding. The revised placement shortens the exposed neck.

The 128 overlapping original torso neck triangles are removed in this private replacement. Retained costume rows remain exact. The existing independently animated rear pieces follow the corrected head frame; they are retained rather than hidden. No original pose is dropped and no defective pose is silently switched to a different candidate.

Head normals are rebuilt with the reconstructed mesh's positive cross-product convention, which differs from the source body helper's convention. The private head uses the skin surface response. Lower-neck vertex color factors approach the retained source skin color while keeping the original head UVs and texture coverage. This changes the head/hair finish as well as the neck; it is not a geometry-only comparison or a newly generated face.

## Verification and comparison

| Check | Recorded result |
| --- | --- |
| Native-source recorded poses checked | 496 across seven motions and three source routes |
| Finite positions, weights in range, exact rigid/torso ownership at weight endpoints | Pass |
| Geometric position continuity across UV duplicates | Pass in all 496 poses |
| Collapsed triangles below area threshold `1e-8` source units squared | None in the checked head poses |
| Costume atlas bytes, retained torso/costume rows, arms and other body parts | Exact; head and rear attachments are the intended exceptions |
| Head UV coordinates and source vertex IDs | Exact |
| Upper-body captures | 32 across eight views and four controls/candidates |
| Full-character captures | 24 across eight views and three controls/candidates |
| Prior stitched PNG and float32-depth controls | All 16 reproduce exactly |
| Framing and other model data between upper-body/full views | Same model payload; only capture size and XY projection differ |

The comparison provides the previous stitched fit, an alignment-only control and the revised neck. Front, side, rear, attack, movement and deep-bend images use shared framing and input lights. These are unedited private GPU captures. Resting views were inspected; the new angle and collar transition are visibly more coherent than the previous fit.

A finite, non-collapsed mesh and identical UV duplicate placement do **not** prove collision-free deformation, an exact watertight head/collar weld, complete texture de-lighting or anatomical quality. The collar boundary still needs an explicit shared-surface solution and motion/contact review. The head-angle preview needs intended look-target review before adopting it as an animation rule. The original torso is open at the neck and the replacement remains a separate bound assembly.

The private responsive comparison embeds its images and makes no network requests. It is a model/art review, not native gameplay or physical Steam Deck evidence.

## Reproduction, retained trials and next work

The work starts from project commit `586d021c93e196cb02f9aa4dc582e9eb2be25d17`, using the previously retained 12,240-triangle head, refined body, native-source pose records and stitched costume atlas. The environment remains Mac Studio, Apple M5 Max, macOS 27.0.1, Corretto Java 25, Python 3.12.0, NumPy 1.26.4 and the existing hidden OpenGL 4.1 harness. No engine behavior, installer, launcher, updater or release package changes.

Private round 87 retains the saved binding, inputs, capture/depth hashes, 496-pose verifier, full-view verifier, exact commands and tooling copies. Earlier rounds 82–86 retain rejected or incomplete trials, including a wrongly oriented normal rebuild, an overbroad neck deformation that affected the chin, and an incorrect color-field offset. They are not the final comparison. The final verifier checks the real color columns and exact unchanged costume rows rather than relying only on successful rendering.

Next, author an explicit collar/neck surface connection, remove remaining baked-light and skin-material discontinuities, and check the revised assembly for self/neighbor contacts and intended gaze across the animation catalog. Keep stitched costume as the preferred direction; the stronger sculpted vest remains held separately. Prove the native mesh/material contract and measure physical Deck performance before installing a replacement. Preserve whole-actor Faithful fallback.

All game-derived meshes, source images, costume artwork, captures and local authoring tools remain outside Git. Public documentation records decisions and verification scope only.
