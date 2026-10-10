# Party character authoring

Dart's accepted head is already selected by normal CharHD. This directory now also contains eight development head fits and nine initial costume/body/weapon studies. These custom assets are public authoring work. They are not additional runtime selections, owner visual acceptance or a downloadable release.

## Head proportions

The first fit used the complete hair bounding box. Shana's braid pulled her face down into her collar; Albert's field and battle bodies also needed separate sizing. The revised fit measures crown to chin, excludes hair ornaments and long hair from skull height, and aligns the central front face surface rather than the centre of rear hair volume. Uniform scale preserves the authored facial shape. The existing native head pivot and stored poses remain in use.

[The explicit landmarks](head-proportion-landmarks-v1.json) pin the input mesh checksum and each character's crown/chin coordinates. Field refinements for Shana, Albert, Lavitz and Haschel retain the jaw height and use individual body-context framing controls. A posed body/skull ratio is a reproducible inspection measurement, not a universal anatomy rule: bent knees, armor, petite bodies and Kongol's build need individual judgment.

All nine were compared against their full bodies in field and battle pose zero, front, 35°, side and rear views; the last stored idle pose was also rendered. Dart uses its byte-identical accepted head as the reference. The eight revised heads passed 192 actual Java construction combinations: field/battle, indexed/simulated RGBA/mapped materials, ModelsHD off/on and candidate off/on. These are CPU construction checks. No native gameplay or physical Deck validation of these eight heads has occurred.

Each `candidate-v1` includes two custom source-bound packs, two exact UV bindings, editable fitted GLBs, one 2048 texture and receipts. [The candidate registry](candidate-manifest.json) is used only by the explicit development probe. Normal packaging still selects the accepted Dart resources in `runtime-assets/charhd/characters/characters.json`.

Remaining head work includes separate original hair/fringe parts that can overlap the reconstruction, rear material quality, neck attachment cleanup, expressions and native animation coverage. The offline field source-material preview does not apply the complete scene texture setup; use it for mesh framing, not field material acceptance.

## Bodies, hands, boots and weapons

Every character has a generated `body-source-v1.png`, the exact prompt and a `body-candidate-v1` custom body/material study. Offline reconstruction uses the pinned TripoSR supplier and a 2K atlas. Initial combat partitions transfer the custom triangles into existing rigid native bone frames; separate heads are excluded so the accepted/reviewed head work remains independent.

The nine studies currently populate **138 of 152 non-head combat parts**. Fourteen parts require original fallback; see [the coverage inventory](authoring-inventory.json). Populated parts are not necessarily correctly registered or finished. The partitioner records uncapped boundary edges and source-fit distances. It checks finite stored idle samples, but does not certify joints, grips or source packet compatibility through the native loader.

These are unsuitable as release bodies yet: open partitions can reveal gaps during animation, single-view reconstruction leaves weak side/rear materials, some grips and weapons need rematching, and field bodies have not been adapted. Sharing the same 2K body atlas across rigid parts also requires shared CPU/GPU image ownership before native multi-actor tests; uploading an independent copy per part is unacceptable for the Deck target.

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

`render-party-candidates.py` produces matched private inspection views and optionally accepts `--previous` to compare two fits using the same camera and body. `fit-party-bodies.py` creates only initial combat partitions and custom UV/materials, not completed animation-safe character replacements. The reconstruction runner supports `--character`, `--asset-kind body` and explicit `--triangles 12000 24000`; its receipt still identifies the original shared head-reconstruction pipeline name.

No new installer release is published for this authoring iteration. Promotion requires reviewed before/after comparisons, native model/material verification and the existing release gates.
