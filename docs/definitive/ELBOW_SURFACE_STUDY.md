# Continuous elbow surface study

October 10 private follow-up to the [body/material comparison](BODY_MATERIAL_STUDY.md). The visible rigid elbow seam needs an authored joint surface driven by the original animation. The first continuous candidates are **held**, rather than installed or selected as defaults.

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

## Next correction

Inspect the specific failing transition with the same controls, then compare improved rotation blending or an authored pose correction. Keep the full recorded-pose audit and preserved bracer/endpoint checks. Reject repairs that hide the failing motion, detach surfaces, distort character proportions or rely on per-frame fallback to conceal a bad model. After deformation and art review pass, follow the [native ownership/material contract](NATIVE_MATERIAL_INTEGRATION.md), world integration, physical Deck performance and community comparison gates.
