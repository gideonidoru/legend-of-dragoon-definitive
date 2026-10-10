# Haschel surface response and costume relief

October 10 follow-up to the [clothing and interface study](GARMENT_SURFACE_STUDY.md). The reconstructed face is substantially clearer than the costume around it. This private comparison tests whether better surface lighting and raised original motifs can close that gap while keeping geometry and textures fixed. **They do not yet deliver the required visible modernization. Both treatments remain held and unshipped.**

## Art decisions

The first response experiment assigns metal to part of the boots using a height threshold. That is wrong: the actual shin guards belong to the lower-leg source palette, and the treatment introduces inappropriate bright foot patches. Its material assignment is rejected even though its mathematical checks pass.

The corrected assignment distinguishes cloth, leather, skin and guards by original part and palette ownership. Lower legs 11/12 use palette 32480 for the shin plates; boot parts 3/4 remain leather. Textured forearm bracers retain their metal assignment. Treating all painted guards as bare conductors then makes the dark navy areas too dark by removing their diffuse response. That treatment is also held.

The next treatment retains diffuse response on colored paint and blends brighter neutral trim toward a conductor response. It preserves the recognizable navy guards without the earlier white foot patches. This color-derived metalness is an artistic proxy, not an authored material map or calibrated physical measurement. Skin, cloth and leather use separate roughness/reflectance choices; the reconstructed head retains its existing rendering.

The finish-only overlay remains too subtle. Full surface response changes the appearance modestly but exposes the older costume art: jagged ornament, painted folds, coarse glove detail and boot forms. Passing shader checks does not establish commercial visual quality.

## Raised original motifs

A further private shader tests relief inferred from bright neutral costume markings on cloth and guards. Four neighboring atlas samples produce a local height gradient; a surface-gradient calculation perturbs the shading normal without changing geometry, UVs, texture bytes, silhouette or depth. Derivatives are evaluated before fragment discard and varying control flow. Atlas sampling stays clamped within each palette region. Degenerate surface derivatives retain the original normal.

The method follows the surface-gradient formulation described in [Mikkelsen's primary paper](https://jcgt.org/published/0009/03/04/paper-lowres.pdf). The private comparison uses view-space positions already baked into its inputs and identity model/camera transforms. It is not a native scene adapter. The amplitude is an authored experiment in original model units; the bright-paint mask can mistake a painted highlight for physical embroidery. No new neural inference, height texture, tangent stream or game shader is installed.

The relief adds a little trim definition in enlarged renders, but contributes very little in the reviewed 160-pixel staged reference. It preserves the old jagged costume motifs rather than rebuilding them. This experiment is **held for insufficient visible improvement**. Increasing its strength is not the next art priority.

## Verified evidence

All character comparisons retain the 20,446-triangle candidate from the clothing study, including its existing motion/contact holds. The expanded diagnostic vertex stream remains 7,851,264 bytes and the 2048 × 2432 RGBA atlas remains 19,922,944 bytes. These are payload sizes, not measured runtime residency or Deck cost.

| Check | Painted-guard response | Source-motif relief |
| --- | --- | --- |
| Captures | 64: 56 character/control views and eight normal-incidence patches | 56 character/control views plus six independent planar fixtures |
| Previous rendering retained | 32 original/current PNGs reproduce exactly | 48 previous PNGs and float32 depth buffers reproduce exactly, including surface-response and material-label controls |
| Original material control | Eight views retain equal coverage and at most one channel step across 738,694 covered samples | The same original control images reproduce exactly |
| Changed surface output | Alpha and float32 depth remain exact | All eight raised-trim views retain exact alpha and float32 depth |
| Independent calculations | Eight constant-normal patches match independently calculated normal-incidence output within one channel step | 3,072 samples check affine plane normals under ordinary, mirrored, rotated and scaled UVs, constant height and degenerate UVs; maximum channel error 0.5 |
| Input preservation | Only high material-tag bits change; the other vertex fields and original low flag bits are exact | Every character input payload is hardlinked to the preceding verified input; no geometry, texture or flag mutation |

Payload, manifest, PNG, decoded RGBA and depth hashes are checked. Both hidden GPU runs complete resource cleanup. The first planar checker excluded every constant-UV sample as an integer-boundary ambiguity; that checker failure is retained. Restricting the exclusion to varying UVs produces the six-case result above without changing the shader or rendered fixtures. These analytical checks establish orientation and the tested local calculation, not all projections, lighting configurations or temporal stability.

The private offline comparison embeds unedited GPU PNGs and the pinned Skurfa cut 14 background. Eight poses, three material selections, three display sizes, keyboard activation and layouts at 1440, 1280 and 390 pixels pass hidden browser checks with no horizontal overflow, page errors or network requests. Buttons remain at least 44 pixels high. Front comparison and handheld layouts were visually inspected. Background placement is manual and lacks native camera, foreground masks, occlusion, contact shadows and reflections; it is not gameplay or a verified in-game character scale.

## Environment and reproducibility boundary

The source checkout is `008ca0a3a3668c2bdf3ceb722d23009b4156afe9`. Experiments use private Python 3.12, NumPy 1.26.4, Pillow 10.1 and Java 25, with hidden Apple M5 Max OpenGL 4.1 captures on macOS 27.0.1. The public visual-tool dependency pins remain unchanged. No engine files, shipped assets, local runtimes, game images, saves or credentials are committed.

Private preparation, bounded Java capture probes, independent fixture/character checkers, input manifests, art decisions and offline presentation receipts are retained outside Git in the versioned material-response and material-relief studies. Their normal procedure is preparation, Java compilation, synthetic fixture capture/check, character capture/check, then offline browser/render review. This document records results; it does not claim that the private character experiments can be recreated from a public clone without lawful source assets and the retained private authoring inputs.

Relief shader identities:

| Stage | SHA-256 |
| --- | --- |
| Vertex | `a654afd3e811d5657c16cc5bf823832c0b1f020d8b9b60bea20eb373b7db49c5` |
| Geometry | `3113e625281e355ac24b8ecc6d869a13018b5529e4b737ebe5601b8e55e835b7` |
| Fragment | `7acfea98e428c5d5c4c49a7cca6c46c03d328e5c368375cd9b598503e6510ede` |

The relief adds a position varying and four conditional atlas fetches. Neither GPU timer measurements nor physical Deck frame time, memory, transition peaks or power evidence are available for it. Passing Mac captures does not establish a suitable Deck default.

## Next visual work

1. Author cleaner original-design costume ornament and stronger garment forms. Judge enlarged detail and handheld size together, using the sharper face as the standard for the body.
2. Improve glove/finger and boot forms, retaining protected source interfaces and repairing the recorded knee/right-wrist contact holds.
3. Establish shared native scene lighting and contact treatment beside original and HD backgrounds. Check motion and temporal aliasing before accepting material detail.
4. Integrate through the existing owned mesh/material contract with bounded data, whole-actor original fallback and verified teardown. Preserve Faithful mode.
5. Measure actual Deck frame time, memory and power, then obtain community comparison feedback before selecting defaults or claiming community acceptance.

This art study does not change the installer, launcher, updater or published alpha release. Commercial quality remains an acceptance requirement, not a label earned by successful compilation or attractive still images.

The subsequent [vest authoring and filtered-color study](VEST_AUTHORING_STUDY.md) develops actual garment forms and neural-assisted costume artwork, bakes it onto the model with original source coverage, and tests filtered color at a fixed base atlas allocation. The new art is clearer in detail; the sculpted vest remains held for recorded arm, glove and sash contacts.
