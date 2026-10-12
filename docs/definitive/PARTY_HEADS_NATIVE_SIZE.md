# Party heads: original in-game size

> Historical original-size checkpoint. The newer source-frame and registered-paint
> correction is documented in [the artistic audit](PARTY_HEADS_ARTISTIC_AUDIT.md).
> Neither checkpoint constitutes visual approval or release acceptance.

**October 11 artistic rejection:** the candidates below pass construction checks
but do not meet the visual goal. Duplicated facial paint, intrinsic head turns,
neck problems and overlapping auxiliary hair remain. See the
[artistic audit and correction gate](PARTY_HEADS_ARTISTIC_AUDIT.md). Private v10
neck clipping is rejected; it is not a replacement for this candidate or a
release asset. All nine heads require owner approval before selection.

The owner changed direction on October 11, 2026 after rejecting Dart's complete
body candidate: the arms and head attachment did not meet the visual goal. That
body remains a rejected experiment. The active work is the other eight heads,
following the accepted Dart appearance route on the existing native bodies.

The owner also rejected both the initially undersized presentation and a larger
Rose/Shana fit. **The original in-game heads now control size.** No new anatomy
ratio or enlargement factor is used. The larger and full-hair-envelope trials
are excluded from this candidate. Full hair bounds can include a braid or neck
and cannot reliably define skull size.

## Current candidate

`integrations/charhd/production/party-reconstruction/head-program-v2` contains
Rose, Lavitz, Shana, Haschel, Albert, Meru, Kongol and Miranda. Each has separate
field and normal battle fits on its original head pivot, a shared custom 2K
texture, source-bound part/UV resources and editable fitted head GLBs.

The existing custom head artwork/reconstruction is reused; no new inference or
new body sculpt was made. Crown and jaw were checked against actual privately
rendered original heads with coordinate guides. The [source controls](../../integrations/charhd/production/party-reconstruction/native-size-head-landmarks-v2.json)
retain individual field/battle dimensions and input hashes. Uniform scale matches
those crown-to-jaw spans and positions; the front face anchor aligns depth. This
preserves the original size reference, not an identical original silhouette or
an exact match of every eye, ear, hair and neck landmark.

Each head contains 6,000 triangles. Sixteen source-bound appearances cover the
eight characters; each field/battle pair shares one 2048 texture. Source bodies,
arms, native animation tables and pivots are unchanged. All twelve selected Dart
packs and all nineteen selected runtime files retain their exact bytes.

## Verification

Mac Studio `Mac17,14`, arm64; isolated Python 3.12 with NumPy/trimesh/Pillow;
Corretto Java 25 and supported Gradle wrapper. New commands:

```sh
python scripts/fit-party-heads.py --files "$OWNED_GAME_FILES" \
  --character rose --mesh "$CUSTOM_HEAD_GLB" --output "$NEW_PRIVATE_FIT" \
  --landmarks integrations/charhd/production/party-reconstruction/native-size-head-landmarks-v2.json
python scripts/build-party-candidate-manifest.py --files "$OWNED_GAME_FILES" \
  --candidate-root integrations/charhd/production/party-reconstruction/head-program-v2
python scripts/verify-party-head-preview.py
./gradlew --no-daemon --console=plain charHdTests charhdJar \
  charHdPartyCandidateProbe -PcharFiles="$OWNED_GAME_FILES" \
  -PcharPartyRoot="$PRIVATE_PROBE_ROOT" -PcharReport="$PRIVATE_REPORT"
```

The private probe root contains the candidate directory plus a copy of the public
`source-audit.json`; it contains no original actor controls. The fitter output
also contains complete source actor controls; **never copy those files into Git**.
Only custom `parts`, UVs, fitted GLBs, texture and sanitized receipts enter the
public candidate directory.

- All sixteen assets pass hash, finite geometry/normals, UV, 6K triangle count,
  embedded/sidecar texture and exact authored crown/jaw scale checks. The validator
  verifies accepted Dart bytes and the nineteen-file runtime baseline, and rejects
  corrupt checksums, traversal and absolute resource paths.
- Twelve CharHD tests pass with no failures. All 192 new source-construction
  combinations pass: eight characters × field/battle × indexed/RGBA/mapped
  materials × ModelsHD off/on × candidate off/on. Each enabled candidate binds
  exactly its head texture and leaves original source tables unchanged.
- The supported build succeeds. Its empty headless probe working folder logs the
  existing missing-font diagnostic; CPU checks still pass. An initial sandbox
  attempt failed because Gradle's coordination socket was blocked; the authorized
  local rerun completed. Neither attempt launched the game.
- There are 160 privately rendered comparisons: head/collar and full body,
  field/battle, front/quarter/side/rear and last stored idle pose. Both variants use
  the same body, pose and camera. These are offline model renders, not game
  captures. Field scene texture preview is incomplete and is not material proof.

## Delivery and remaining acceptance

The optional preview stager combines these sixteen heads with the exact accepted
Dart resources into an explicit overlay. It does not alter the ordinary CharHD
JAR, selected runtime directory or an installed game. A preview JAR is a private
local test artifact built from this authoring branch; compatibility with a
separately published engine/release has not been checked. Custom meshes/textures
and the reproducible staging code are public development assets, ready for
ordinary installer packaging after acceptance. Do not add a separate permanent
manual-transfer or authoring requirement to the eventual player installation.

Owner visual acceptance remains pending. Inspect face size/identity and skull
placement against the original, particularly Rose's separate fringe/ornament
parts, Shana's long braid, Albert's field hair, collar contacts and inferred rear
paint. Crown/jaw agreement is not proof that every feature or attachment matches.

Before selection, check native idle/attack/damage/victory/death, field transitions,
expressions, source reloads, CharHD/other texture replacements and ModelsHD on/off.
The existing atomic appearance transaction retains other geometry owners'
priority; the new heads have not passed broad third-party compatibility or native
visual acceptance. A 2K RGBA image is 16 MiB base data; nine heads would be 144 MiB
if simultaneously resident, before GPU overhead. This is arithmetic, not measured
Steam Deck residency, frametime or power. Physical Deck validation is pending.

Keep this work on the draft authoring branch until explicit owner approval for
main/release. Preserve project, original character art and TripoSR attribution.
Original reference pixels, extracted source controls, ISOs, saves, credentials
and local runtimes remain outside the public payload.
