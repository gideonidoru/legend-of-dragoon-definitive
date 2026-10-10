# Continuous elbow surface study

October 10 private follow-up to the [body/material comparison](BODY_MATERIAL_STUDY.md). The visible rigid elbow seam needs an authored joint surface driven by the original animation. Early candidates remain **held**. A later continuous corrective passes the sampled geometry and material controls, but remains private and has not passed final art, native integration or Steam Deck acceptance.

## Binding and construction

The offline [`TwoBoneSurface`](../../scripts/bind-rigid-attachments.py) binds a bounded surface in a common rest space with two normalized, nonnegative bone weights. It uses the actual inverse bind matrices and full rigid pose transforms. Fully weighted endpoint rings follow their respective bones. Input arrays are copied, internal data is immutable, and malformed weights, excessive vertices, nonfinite data and improper transforms fail. Twelve original synthetic attachment/binding tests cover known translated/rotated results, rest-pose roundoff, ownership and rejection. This utility returns positions; it does not preserve bend volume, supply normals or integrate native rendering.

The private authoring experiment clips the upper-arm and forearm surfaces, removes their original elbow caps, and joins their open rings through a continuous loft. Clipped skin colors interpolate at the cut. Both arms retain all 128 textured bracer faces, with exact source positions, UVs and colors before pose transformation. Shoulder and cuff/wrist regions retain their original bone ownership. Normals are regenerated from the posed continuous mesh for the GPU diagnostic, rather than joining separately shaded segment caps.

## Evidence and held candidates

| Candidate | Upper/forearm cuts in source-local Y | Result |
| --- | --- | --- |
| Short joint | 130 / 35 | Full 496-pose audit per arm: 109 held checks on one arm and 356 on the other |
| Wider joint | 100 / 60 | Six bind/extremal triage checks: three held; not advanced to full acceptance |
| Long joint | 50 / 90 | Full 496-pose audit per arm: one arm passes sampled checks; the other has four held checks in an attack transition |

The longest candidate has closed, consistently wound bind topology and no tested bind-surface intersections. Its endpoint rings match direct rigid expectations across the recorded poses. It totals 18,048 character triangles with the same atlas payload as the previous study. Despite passing bind and angle-extreme checks, it intersects in motion 5 at combat-2 tick 2, combat-4 ticks 4/5 and animation-apply-2 tick 1. These four checks include repeated timing routes; they are not four independently authored animations.

The wider candidate's diagnostics found crossings both within the blend and between retained rigid upper/forearm regions. A short blend cannot repair that underlying overlap. Extending the deformable region removes most failures, but the remaining transition still needs a correction. Linear blending can collapse or twist a joint surface; a valid weight sum and exact endpoint positions do not establish acceptable deformation. The intersection test covers nonadjacent triangles, not every possible adjacent fold, tangency, body/world contact or unrecorded animation.

GPU diagnostic captures use shared cameras within each control/candidate case, controlled lighting, original source poses and the previously checked fragment atlas-lookup prototype. The final diagnostic includes 60 images at five pose states and three yaws, including the failing combat-2 transition. All input and captured-pixel identities were verified. Fifteen indexed-to-nearest controls pass with zero coverage differences and at most one channel step across 1,305,589 covered samples. Strong-material coverage also matches. That material result does not clear the changed geometry's four held checks.

A clean-looking view cannot override the held pose audit. Retail-derived meshes, images, pose matrices, private authoring/capture helpers and failure reports stay outside Git; no candidate changes the shipped engine or installer. The binding source at [`044ec4a2f338954352ea3bdb02ecd9f332e51160`](https://github.com/gideonidoru/legend-of-dragoon-definitive/commit/044ec4a2f338954352ea3bdb02ecd9f332e51160) passed all three jobs in [hosted run 38037766008](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/38037766008), including the original synthetic visual fixtures and both platform builds. A new installer release is not needed for this offline helper.

## Curvature-driven corrective

Follow-up comparisons kept the failing transition in the test set. Dual-quaternion blending of the long joint still failed the same four checks. Reconstructing cross-sections along a curve, including rotation-interpolated frames, also retained crossings. Expanding the deformable region to upper-arm Y −20 and forearm Y 120 reduced those failures to one recorded check. Moving the forearm cut farther crossed a costume color boundary and was rejected; changing weights alone did not clear the remaining intersection.

The latest private candidate uses that expanded region and seven 32-vertex interior rings per arm. Proper endpoint frames interpolate by the shortest rotation, then align with the tangent of a cubic Hermite centerline. An analytic curvature correction compresses positive inward radial offsets continuously: the inward distance approaches, but does not exceed, 0.8 times the local curvature radius. Outward offsets remain unchanged relative to the same uncompressed loft. This is an authored shape treatment, not a volume-preserving or global collision solver. It uses no pose-specific exceptions, hidden failing motions or per-frame replacement.

Only the 224 new interior vertices per arm receive that correction. Retained vertices and endpoint rings follow the original bone transforms; bracers and cuff attributes are preserved against the preceding refined source meshes. This does not mean the entire character retains original retail geometry.

| Check | Verified result | Limit |
| --- | --- | --- |
| Geometry | 496 recorded samples per arm, 992 total; 298/288 unique matrix pairs; zero tested nonadjacent intersections, closed consistently wound bind surfaces and nondegenerate triangles | Seven recorded motions and three timing routes; not all animations, adjacent folds, tangencies or body/world contacts |
| Source design | All 256 textured bracer faces and 320 non-skin cuff faces retain source positions, UV/CLUT and corner colors; 217,744 direct source-local vertex placement comparisons, maximum error below 4.6 × 10⁻¹³; endpoint error zero | Compared with the preceding refined arm meshes, not every original character surface |
| Rotation choices | No tested shortest-rotation branch crossings or antiparallel frame events within the recorded routes | Not cross-clip transitions or a perceptual smoothness threshold |
| Independent properties | Seven batches check bounded compression, unchanged outside/perpendicular components, monotonicity and rotation covariance; 36 whole-surface common rigid-transform checks, maximum error below 6.9 × 10⁻¹³ | Deterministic private checks, not native actor integration |
| GPU controls | 64 hidden-context captures; 16 original indexed-to-nearest views pass exact coverage and at most one channel step across 1,398,274 covered samples; strong-material coverage matches; all input, PNG and decoded-pixel identities verified | CPU-prepared poses, controlled lights and the private atlas-lookup fragment prototype |
| Comparison registration | All 15 views shared with the earlier held study have identical projection, lighting and original-model PNG identities | The extra combat-4 transition has no earlier matched candidate capture |

The latest character totals **17,506 triangles**, a 19,922,944-byte RGBA atlas and 6,722,304 bytes of expanded vertex attributes in this diagnostic. These are payload sizes, not measured runtime residency, frame times, memory peaks or Deck performance. The checks use the existing private Python 3.12/NumPy 1.26.4/Pillow 10.1 environment, Java 25 and the Apple M5 Max OpenGL 4.1 context. The source vertex/geometry shaders remain unchanged; the fragment atlas-lookup prototype hash is `c120f3181f2ba4c09ff367461f44e7d2aa1f2d06869ac11596062b280c33ae17`. No new dependency, inference service, shipped shader/engine change or installer release was needed.

The private evidence directory is `Definitive-private-texture-pilot/haschel-elbow-inner-corrective-round65`, beside the checkout. It retains the build manifest, full source/topology checks, temporal checks, comparison registration, GPU manifests and `elbow-evidence.json`. Authoring and verification helpers remain in the private tooling directory `elbow-inner-corrective-round65`; the full audit, temporal check, capture control check and final hash/property verification all exited successfully. The captured triangle count supersedes an earlier preparatory estimate of 17,504.

## Art review and next gate

The reviewed front, attack and side captures show a continuous arm shape without the prior visible elbow construction seam. The bracers remain recognizable. Clothing still has conspicuous facets, hands and boots remain coarse, and the arm treatment needs skin detail and proportion review beside the reconstructed face. This is promising local art progress, not a finished AA/AAA character or community-approved direction.

Next, review movement sequences and garment/arm contacts, refine clothing, hands and boots without erasing costume markings, and compare the actor at actual handheld sizes against original and enhanced backgrounds. After deformation and art review pass, follow the [native ownership/material contract](NATIVE_MATERIAL_INTEGRATION.md), including blending, sorting, teardown and whole-actor Faithful fallback. Physical Deck performance and community comparison remain required before activation or selection as a default.
