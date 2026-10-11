# Party character authoring

Dart's accepted head is already selected by normal CharHD. This directory now also contains eight development head fits and nine initial costume/body/weapon studies. These custom assets are public authoring work. They are not additional runtime selections, owner visual acceptance or a downloadable release.

The owner's latest correction is that the studies are close but do not exactly honor the original game art. Original references now control the design; generated images serve only as finish experiments. Every new candidate requires the [character art fidelity check](../../../../docs/definitive/CHARACTER_ART_FIDELITY.md), independently of its construction and animation checks. Haschel's incorrectly specified trouser color and Meru's altered motif layouts are recorded examples of drift. Previous generated prompts are preserved as historical evidence, not corrected silently or reused as canonical descriptions.

## Head proportions

The first fit used the complete hair bounding box. Shana's braid pulled her face down into her collar; Albert's field and battle bodies also needed separate sizing. The revised fit measures crown to chin, excludes hair ornaments and long hair from skull height, and aligns the central front face surface rather than the centre of rear hair volume. Uniform scale preserves the authored facial shape. The existing native head pivot and stored poses remain in use.

[The explicit landmarks](head-proportion-landmarks-v1.json) pin the input mesh checksum and each character's crown/chin coordinates. Field refinements for Shana, Albert, Lavitz and Haschel retain the jaw height and use individual body-context framing controls. A posed body/skull ratio is a reproducible inspection measurement, not a universal anatomy rule: bent knees, armor, petite bodies and Kongol's build need individual judgment.

All nine were compared against their full bodies in field and battle pose zero, front, 35°, side and rear views; the last stored idle pose was also rendered. Dart uses its byte-identical accepted head as the reference. The eight revised heads passed 192 actual Java construction combinations: field/battle, indexed/simulated RGBA/mapped materials, ModelsHD off/on and candidate off/on. These are CPU construction checks. No native gameplay or physical Deck validation of these eight heads has occurred.

Each `candidate-v1` includes two custom source-bound packs, two exact UV bindings, editable fitted GLBs, one 2048 texture and receipts. [The candidate registry](candidate-manifest.json) is used only by the explicit development probe. Normal packaging still selects the accepted Dart resources in `runtime-assets/charhd/characters/characters.json`.

Remaining head work includes separate original hair/fringe parts that can overlap the reconstruction, rear material quality, neck attachment cleanup, expressions and native animation coverage. The offline field source-material preview does not apply the complete scene texture setup; use it for mesh framing, not field material acceptance.

## Bodies, hands, boots and weapons

Every character has a generated `body-source-v1.png`, the exact prompt and a `body-candidate-v1` custom body/material study. Offline reconstruction uses the pinned TripoSR supplier and a 2K atlas. Initial combat partitions transfer the custom triangles into existing rigid native bone frames; separate heads are excluded so the accepted/reviewed head work remains independent.

The nine studies currently populate **138 of 152 non-head combat parts**. Fourteen parts require original fallback; see [the coverage inventory](authoring-inventory.json). Populated parts are not necessarily correctly registered or finished. The partitioner records uncapped boundary edges and source-fit distances. It checks finite stored idle samples, but does not certify joints, grips or source packet compatibility through the native loader.

These are unsuitable as release bodies yet: open partitions can reveal gaps during animation, single-view reconstruction leaves weak side/rear materials, and some grips and weapons need rematching. Dart now has a separate neutral field study; the other eight field adaptations remain pending.

## Dart body study, October 10

The owner approved [body-source-v1.png](dart/body-source-v1.png) as the visual target: “this is a beautiful and perfect image.” [VISUAL-TARGET.json](dart/VISUAL-TARGET.json) records its checksum and the approval scope. Keep the exact already accepted head/hair mesh and paint, the asymmetric armor, original costume identity and human proportions. The image approval does not approve a resulting mesh or select new bodies for main/release.

The new [neutral field source](dart/field-body-source-v2.png) retains the asymmetry of the approved battle design. [field-body-candidate-v2](dart/field-body-candidate-v2/characters.json) populates 14/14 field body parts with its own 2K material and the original field pivots. It uses a separate standing sculpt, rather than stretching the crouched battle sculpt onto the field skeleton. The editable raw custom sculpts, prompts, fitted body GLB and source-bound packs are public authoring assets. The generated head in each raw sculpt is excluded from the fit; the exact accepted head is supplied separately. Front/side/rear and first/last stored field-pose previews were inspected offline. Field scene gameplay and owner visual acceptance remain pending; rear paint and collar attachment still need work.

The engine now shares one immutable body image and one GPU upload across its rigid parts. Mesh leases retain the texture through queued drawing and release it after the last mesh retires. Six focused tests cover immutability, multiple owners, queued retirement, exactly-once backend deletion, upload failure/retry and context replacement. The actual native Dart battle model asserted **17 painted parts and two distinct GPU textures**. At 2048×2048 RGBA8, this is a calculated **32 MiB of base GPU images rather than 272 MiB** with independent uploads per part; it is not a Deck measurement or total process memory estimate. The CPU manifest retains distinct field and battle images for future construction.

The explicit source probe passed **24 field/battle construction combinations**, including indexed, simulated RGBA and simulated mapped routes, ModelsHD off/on and candidate off/on. It verifies finite generated vertices, unchanged source positions/packets, full painted-part coverage and two shared images per model. [BODY-VALIDATION-v2.json](dart/BODY-VALIDATION-v2.json) records checksums, environment and results.

An isolated Mac native battle run passed seven smoke/construction tests and captured 21 scripted pose samples from seven available animation files. **The visual animation gate failed:** the detailed battle body separates at joints and exposes open partitions in moving poses. The tests checked loading, material ownership and finite transforms; they did not assert beautiful or continuous moving geometry. These screenshots remain private because they contain original game scenery and UI. Initial missing-disc links and a nonexistent-animation assumption were corrected in the private harness; those failed attempts are not counted as successful validation.

Nearest-surface partitioning, anatomical region overrides, socket caps and a native cage with rebaked paint were investigated. None met the complete-body standard: partitioned sculpts break apart; cage fits sacrifice the approved silhouette and material clarity. Do not keep repeating texture or seam tweaks to hide this. The next body step is an articulated surface with correct joint ownership and grip anchors, using deliberately overlapping closed rigid pieces where appropriate or limited weighted deformation where rigid seams cannot remain closed. Prototype and inspect that deformation with the accepted head and current custom material before generating more body art or expanding it to the other eight. Verify native moving poses and scene reloads, then request owner comparison/approval before main/release. A new [rear material projection](dart/body-rear-material-v2/bake-receipt.json) is an independent, unselected study and still needs visual review.

The next body pass should repair the topology and native grips character by character, starting with Dart, then use native idle/attack/damage/victory/death tests. Apply approved custom body work through the existing CharHD geometry/material transaction. Check original/CharHD/other texture owners and ModelsHD on/off, then measure three-party Deck residency and frame time before selecting density or resolution defaults.

## Reproduction

Use a local extracted game directory and the separate offline authoring environment. Original reference pixels, full original actor controls, game files, runtimes and model weights stay outside Git. Only the custom deliverables and bounded receipts belong here. Preserve the engine AGPL license, underlying character-art attribution and [TripoSR MIT notice](../../../../experiments/dart-modelshd-charhd/reconstruction/TRIPOSR-LICENSE.txt).

```sh
# AUTHORING_PYTHON points to the separate NumPy/trimesh/Pillow authoring environment.
# OWNED_GAME_FILES points to the owner's locally extracted game files.
# RAW_HEAD is the matching reconstructed custom GLB; PRIVATE_FIT is a new folder outside this checkout.
"$AUTHORING_PYTHON" scripts/fit-party-heads.py --files "$OWNED_GAME_FILES" --character shana --mesh "$RAW_HEAD" --output "$PRIVATE_FIT"

# Copy only custom parts, UVs, GLBs, texture and receipts into the character candidate folder.
# Do not copy the private complete native actor control JSONs or original comparison pixels.
"$AUTHORING_PYTHON" scripts/build-party-candidate-manifest.py --files "$OWNED_GAME_FILES"
./gradlew charHdPartyCandidateProbe -PcharFiles="$OWNED_GAME_FILES" -PcharReport="$PRIVATE_REPORT"
```

`render-party-candidates.py` produces matched private inspection views and optionally accepts `--previous` to compare two fits using the same camera and body. `fit-party-bodies.py` creates initial combat partitions or Dart's explicit `--form field` study, not completed animation-safe replacements. `--segmentation` accepts checksum-bound anatomical polygons; `--overlap-rings` and `--depth-weight` are bounded development fit controls, not quality guarantees. The reconstruction runner supports `--character`, `--asset-kind body` and explicit `--triangles 12000 24000`; its receipt still identifies the original shared head-reconstruction pipeline name.

```sh
# Explicit development overlay, outside the checkout; normal packaging never selects it.
"$AUTHORING_PYTHON" scripts/stage-dart-body-candidate.py \
  --candidate integrations/charhd/production/party-reconstruction/dart/body-candidate-v1 \
  --field-candidate integrations/charhd/production/party-reconstruction/dart/field-body-candidate-v2 \
  --output "$PRIVATE_OVERLAY"
./gradlew charHdDartBodyProbe -PcharFiles="$OWNED_GAME_FILES" \
  -PcharCandidateResources="$PRIVATE_OVERLAY" -PcharReport="$PRIVATE_REPORT"
./gradlew build deliveryTest stageDefinitivePackage
```

The supported headless build/regression/package command passed on the recorded Mac/Java 25 environment. It packages the existing accepted runtime assets, not these development bodies. The native study uses a separate private installation and test harness; it is not run by normal packaging. No physical Deck body performance or gameplay validation occurred.

No new installer release is published for this authoring iteration. Promotion requires reviewed before/after comparisons, native model/material verification and the existing release gates.
