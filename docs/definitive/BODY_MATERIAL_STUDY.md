# Haschel body and material refinement

October 10 private follow-up to the [posed native shader study](NATIVE_SHADER_STUDY.md). The target is a coherent modern interpretation of the original character, with a readable face, natural limbs and costume detail that belongs beside the enhanced painted backgrounds. A technically valid mesh does not establish that finish. The candidate remains outside Git and is not installed or shipped.

## Selective skin treatment

The broad uniform arm repaint was too orange beside the reconstructed face and could repaint dark untextured cuff/trim surfaces. A replacement mask selects warm, untextured arm colors while retaining textured bracers and dark trim. Three treatments compare a median face-palette tone, a warmer tone with a smooth limb gradient, and the original color variation remapped into the face palette. The palette is sampled from the existing head bake; it is an authoring aid, not a measured physical albedo or new neural inference.

The private run captured 24 images: three color treatments and a GPU visibility mask at six pose/view combinations. All input and captured-pixel identities were verified. Eighteen comparisons retained exact alpha and every pixel outside the visible skin mask. Geometry, normals, UVs, material flags, the atlas, recorded poses and controlled lighting were unchanged. The warm graded treatment is a more cohesive direction than the previous uniform paint; it still lacks authored skin detail and full world-lighting review.

## Shoulder shape

Two upper-arm candidates relax five previously fixed shoulder outline vertices while retaining seven authored shoulder/elbow anchors and the original part transforms. They use two subdivision steps at strengths 0.55/0.8 and crease thresholds 120°/145°, then balance radial volume. Those anchors are explicit art choices, not a verified native joint-contact contract.

Both parts in both candidates pass finite geometry, closed manifold connectivity, consistent winding, duplicate/degenerate face and tested nonadjacent intersection checks. Signed volume matches the source within floating-point rounding. The retained anchors are exact across 496 recorded native poses per part: 1,984 pose/part checks and 13,888 anchor comparisons. This does not prove inter-part contact, cloth collision freedom or every motion's appearance.

Each candidate then produced 24 GPU captures: 18 original-model images (indexed, nearest and strong materials at each of six views) and six candidate images. Both sets of original controls passed: identical coverage and at most one channel step across 524,348 covered samples per set. Capture and payload hashes were checked. Compared with the previous warm-skin candidate, only the two upper-arm attribute spans change; all other vertex rows, projection, lighting, VRAM, atlas, UVs and material flags remain exact. Candidate silhouette differences are expected and are recorded separately from the unchanged-model controls.

The stronger profile softens the shoulder cap in the reviewed front, side and raised-arm views. The rigid elbow seam and garment opening still show segmented construction. Neither candidate has been accepted as final character art. The model remains 18,068 triangles and retains the same 19,922,944-byte RGBA atlas payload; this is unchanged payload size, not measured Deck cost or runtime residency.

## Next work and acceptance

1. Review original-design references while refining the elbow and collar/garment transitions. Preserve recognizable proportions, bracers and costume markings rather than introducing generic armor.
2. Develop an explicit joint/contact treatment, then inspect motion sequences and pose extremes. Exact retained anchors alone do not establish convincing deformation.
3. Match the body, face and clothing treatment to a shared world-lighting comparison with original and enhanced backgrounds. Review both enlarged detail and actual handheld character sizes.
4. Integrate through the [owned mesh/material contract](NATIVE_MATERIAL_INTEGRATION.md) with whole-actor original fallback, bounded data, blend ordering and teardown. Keep art experimentation independent of Faithful gameplay.
5. Measure frame times, upload/transition peaks, memory and power on physical Deck hardware before selecting asset sizes or defaults. Obtain community comparison feedback before claiming community acceptance.

The study used the same Mac Studio GPU, private Python environment and hidden shader harness recorded in the preceding study. No new dependencies, model inference, cloud service, engine/shader file changes or installer release were needed. The first shoulder-payload preparation stopped on a local variable collision; its partial outputs/logs were retained and corrected inputs were generated in new versioned directories before capture. Geometry and pixels, including the initial failure, remain private.

The subsequent [continuous elbow study](ELBOW_SURFACE_STUDY.md) records the held early lofts and a later curvature-driven corrective that passes all 992 sampled arm/pose geometry checks. Its 64 GPU captures retain verified original-material controls. The continuous arm treatment remains private; final art, body/world contact, native activation and Deck performance are still open. The offline binding utility and private corrective do not change shipped game behavior.
