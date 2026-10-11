# Dart field head selection regression

The reported symptom is an old field head that changes to the new head around
battle preparation, then returns to the old head after battle. The requirement
is that the accepted custom field head stays selected throughout ordinary field
play and after returning from battle; battle retains its own accepted fit.
Preserve accepted artwork, native animation tables and other mod owners.

## Confirmed cause and scope

At source baseline `adf864be75153774455cd9bebc7561335d297b80`, the selected
CharHD manifest recognized only one native Dart field head. An owner-input check
of Seles `SECT/DRGN21.BIN/41/0` deterministically failed with ModelsHD disabled:
`Bundled CharHD head did not activate`. The native head identity was
`7ec2a8d20854bdffb131236bfec72593e54b3a40ccd08232afb8a5f372583706`, absent
from the manifest. This isolates a binding gap from model-mod priority and
texture relocation. The default `101/0` check passed all 24 combinations.

The Dart-labelled catalog routes resolve to four exact head identities across
1,086 references in the owner's present extraction. Some catalog actor names
are patch-metadata candidates. This is coverage of those inventoried routes,
not proof of every script, pose, game scene or alternate source extraction.

| Native head prefix | Actual source representative | Recorded ModelsHD baseline |
| --- | --- | --- |
| `b84e13a11adb` | `SECT/DRGN21.BIN/101/0` | native identity |
| `82d567bfa9c5` | `SECT/DRGN21.BIN/32/0` | native identity |
| `7ec2a8d20854` | `SECT/DRGN21.BIN/41/0` | `ae9365fe2fd18b715a67c3c1a82a6a65d50c22a34e1b1e8a5281ba5a04c56d57` |
| `640e2a8491d6` | `SECT/DRGN21.BIN/116/0` | native identity |

## Change

Three explicit source-bound custom pack/UV declarations extend the current
strict loader. Only the source declaration and associated resource checksum
change. The accepted vertices, normals, faces, UV coordinates, field fit,
texture and every pre-existing pack/UV/image stay unchanged. No battle head,
body variant or new artwork is promoted. The ModelsHD handoff for the Seles
variant is its exact existing refined geometry; unknown owners keep priority.

The intentional duplicated declarations preserve the existing source-identity
and checksum validation without introducing a permissive runtime alias. They
reference one existing 2K texture. ZIP compression reduces the repeated custom
payloads in the shipped JAR. `scripts/bind-dart-field-heads.py` checks or
regenerates the three declarations; it never emits original game data.

## Checks and limits

`charHdTests` includes an input-free regression asserting all four bindings and
unchanged accepted geometry/UV/material. `charHdReconstructionProbe` accepts
`-PcharFieldModel=SECT/DRGN21.BIN/41/0` to run any known source through field and
battle, three texture routes and both mod toggles. Both original CPU packets
and positions must remain unchanged. Owner-supplied original inputs stay private.

`charHdHeadTransitionProbe` checks four actual field sources, ModelsHD off/on
and eight repeated cycles per configuration: field before battle, battle head
allocation, retained field cache, retired mesh rebuilt from CPU tables and
fresh field reload. It asserts distinct exact field/battle appearance identities
and the accepted supplemental texture. This is 64 CPU/cache lifecycle cycles.
It does not run an actual game-state transition, draw on a GPU or establish
Steam Deck visual acceptance.

The reported change immediately before battle is not separately reproduced in
live gameplay. Fixing the confirmed old-field fallback is necessary, but the
Deck acceptance check remains: observe Dart in the reported scene, begin a
fight, return to that scene and verify the new field head remains selected.
The publisher must integrate and validate the exact source before updating the
single current installer/release; these local results are not publication proof.

## Local verification receipt

- All four representative sources pass 24 route/mod combinations each (96 cases).
- Thirteen CharHD tests and 32 ModelsHD tests pass with no failures or skips.
- Sixty-four actual-source head cache cycles pass. Substituting the old manifest
  only in temporary build resources makes the same probe fail at Seles `32/0`:
  the expected accepted field identity is replaced by the native `82d567...`.
  This verifies the new probe detects the original selection gap.
- Every pre-existing selected custom pack, UV and image is byte-identical to the
  pinned source baseline. The built CharHD JAR contains all fifteen selected
  bindings with matching resource checksums and uses one existing head image.

The empty probe working directory logs an existing missing-font warning; these
CPU/cache probes do not draw text. Source-bound test reports remain private.
Physical Deck visual acceptance and the complete installer publication gate
remain outstanding.
