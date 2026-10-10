# Head mesh connectivity and texture refinement

Checked 2026-10-10. The private Haschel head now has a **12,240-triangle connectivity-checked candidate**, with a new 1024² neural base-color bake and shared-camera comparison against the earlier 8,000-triangle candidate. It remains unaccepted art and is not installed in the game. All assets, checkpoints, local runtimes and prototype runners remain outside Git. The [earlier reconstruction record](LOCAL_MODEL_RECONSTRUCTION.md) supplies the input/model provenance.

## Why the earlier meshes were held

The previous official GLB validator results remain correct: those files satisfy the interchange format. A stronger, independent geometry audit subsequently found defects in the underlying reduction before UV splitting:

| Earlier candidate | Nonmanifold edges | Duplicate faces | Zero-area UV faces |
| --- | ---: | ---: | ---: |
| 4,000 triangles | 13 | 4 | 16 |
| 8,000 triangles | 16 | 6 | 18 |

These are geometry/texture defects despite a valid GLB container. Neither candidate may advance to native replacement testing on the strength of format validation alone.

Four unwrap configurations were compared at both sizes, preserving their geometry. Packing increased useful texture coverage, but hundreds of small charts and zero-area UV faces remained. Reducing curvature weights or increasing chart cost did not resolve the underlying connectivity defects. Subsequent source/reduction audits checked duplicate triangles, edge incidence, connected vertex fans and nonzero face area.

The 128-grid source's main component has no nonmanifold edges or bad vertex fans. The 256-grid source's main component has no nonmanifold edges but two disconnected vertex fans. The earlier reduction introduced additional defects. Lower-aggression reduction and free-boundary variants were also rejected; some did not reach the requested triangle count.

## Alternative reducer check

VTK 9.7.1 was tested in a separate private authoring environment. Twelve official wheels, totaling 134,712,664 bytes, were individually checked against PyPI filename/size/SHA256 metadata before a hash-required, offline installation. Its BSD notice was read and retained; dependency resolution passes. No VTK dependency was added to the engine or installer. [Official package](https://pypi.org/project/vtk/9.7.1/).

The experiment enabled topology preservation and disabled splitting and boundary-vertex deletion. Those controls can limit the achievable reduction; the supplier does not promise a requested count with all constraints enabled. [VTK decimator documentation](https://vtk.org/doc/nightly/html/classvtkDecimatePro.html).

The synthetic sphere check passed. All four actual-head outputs failed the independent connectivity audit, including 36–132 nonmanifold edges and 35–115 duplicate faces. None was adopted. An option named `PreserveTopology` is not our acceptance proof.

## Bounded connectivity prototype

The two disconnected source fans were separated by duplicating their shared vertex IDs. **Every triangle corner position, triangle order and winding remains exact**, with no face deletion, automatic hole filling or geometric movement in that preparation. The prepared source has 89,072 vertices and 177,946 triangles. Exclusion of the same 622 triangles in other inferred components remains recorded from the earlier study.

A private endpoint-only quadric reduction then checks the edge link condition, duplicate-face creation and local vertex fans before accepting a collapse. It keeps every source boundary vertex and checks face area and orientation. Each cluster of removed source vertices must stay within 0.025 normalized reconstruction units of its final representative. Both each-collapse and original-face normal changes are limited to 30 degrees. These are authoring limits, not game meters or an artistic quality score.

Six original synthetic checks pass: closed-sphere reduction with exact face lineage and immutable input; fixed displacement limits; refusal to collapse a tetrahedron into duplicate faces; retained open-grid boundaries; duplicate-input rejection; and zero-area-input rejection. No retail asset or neural model is used in those checks.

The actual fine head requested 8,000 triangles but stopped at **12,240** when no permitted collapse remained. The constraints were not relaxed to reach the number. Its independent result is:

| Check | Verified result |
| --- | --- |
| Geometry vertices / triangles before UV splits | 6,219 / 12,240 |
| Nonmanifold edges / bad vertex fans | 0 / 0 |
| Degenerate / duplicate faces | 0 / 0 |
| Original boundary vertices retained at exact positions | 460 |
| Original-face lineage reconstructed from vertex ownership | Exact |
| Maximum cluster displacement from original source vertices | 0.0249954 normalized units |
| Maximum final normal angle from its original face | 29.9951 degrees |
| External worker wall time / sampled peak RSS | 42.25 seconds / 0.896 GiB |

The source-to-representative displacement check does not prove continuous surface distance, absence of self-intersections, or rig compatibility. Those remain separate checks. The prototype is not a shipped geometry importer or a production retopology tool.

## Bake and visual review

The selected experimental unwrap has 745 charts, 408,433 covered pixels, zero zero-area UV faces and 43 faces without a covered pixel center. In total, 71 faces have less than one pixel of UV area, regardless of center coverage. Missing faces account for approximately 0.0103% of geometric surface area; the small percentage does not excuse defects in a visible feature. Four-pixel dilation does not establish clean native filtering or mip behavior. Fragmentation still needs deliberate retopology/UV work.

The neural bake reused the verified offline CPU model, input and lock from the earlier record. The first helper attempt failed on a variable collision before mesh export; its logs and partial texture remain retained. The corrected run used a fresh output folder, completed in 8.04 external seconds and sampled 3.823 GiB RSS. No input was uploaded. A 1024² RGBA8 texture would occupy 4 MiB decoded, excluding mipmaps and driver overhead.

The review-ready GLB passes official Khronos Validator `2.0.0-dev.3.10` locally with zero errors, warnings, infos or hints. Buffer-view metadata normalization leaves the entire binary geometry/texture chunk unchanged. UV seam duplication is intentional; the connectivity audit applies to the source geometry before those splits, and the unwrap retains exact source face/vertex correspondence.

| Private output | Bytes | SHA256 |
| --- | ---: | --- |
| Connectivity-checked textured head GLB | 1,232,012 | `ecc6ee37587fed1d8b24f862f061f52c9841a44bc0708901e98c114740b28ef1` |
| Geometry/lineage evidence manifest | — | `964b476e5de718c2b977b0a3f9a4ba4cbdd29acfe4729b2e2b72e1a0a2a60edf` |
| Bake/render evidence manifest | — | `77cdd4e2c6fdab4aeeae3fa7a12dd6370ff4453e71f966be50b4b61aa75d2d11` |

Four-view textured and geometry-only renders use shared framing, camera and preview lighting for the earlier/current candidates. The recognizable face is retained, while the underlying connectivity is cleaner. These are offline portraits with different geometry and UV layouts, not an equal-budget performance comparison or native gameplay capture. The synthetic and actual audits, rejected alternatives, inference logs, dependency locks and runner hashes are retained privately.

## Next acceptance work

Shape the hair into intentional, clean forms; replace the rough inferred neck transition with an authored boundary matched to the original body/part pivot; correct facial proportions against the original design; and build a coherent UV layout with complete coverage. Then verify the native material/geometry contract, animation, lighting, blending, occlusion and teardown. The [Deck protocol](DECK_TEST_PLAN.md) supplies the subsequent physical memory/frame-time/power, input, suspend and recovery checks. Community preference remains unverified.

No triangle count, format pass or CPU authoring timing selects a Deck default. Faithful mode, runtime geometry, engine behavior and the published installer remain unchanged.
