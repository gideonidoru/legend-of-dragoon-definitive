# Party heads: artistic audit and correction gate

October 11, 2026. **Not visually accepted or release ready.** The owner requires
approval of all nine heads before this work enters main or a release. Accepted
Dart remains the reference; the other eight candidate heads are under correction.

## Findings

1. **Blocker: two faces in one texture.** Haschel's standalone reconstructed
   head shows a smaller painted jaw over a larger face. This reproduces without
   any original game geometry. Removing the projected high-resolution layer
   leaves one coherent, softer face; the untextured render also has one face.
   The existing bake blends source artwork with inferred surface colors without
   registering corresponding anatomical features. A UV hash or 2K image size
   cannot establish that the paint follows the facial volume. Inspect eyes,
   nostrils, mouth, chin, moustache and sideburns at pixel scale. Existing heads
   need a coherent registered bake, not another overlay.
2. **Blocker: portrait orientation is treated as neutral.** The original fitter
   assumes every reconstruction faces precisely along +X, fits crown/chin and
   the frontmost central surface, then cancels the initial native head rotation.
   The source portraits and reconstructed faces have an intrinsic turn. Sampled
   eye-line directions are about 16–22 degrees from the assumed axis, with
   additional roll. Those measurements describe sampled painted surface points,
   not certified anatomical axes. Native head axes differ between field and
   battle models. Match a verified neutral anatomical frame to the original
   bone-local frame, then retain the original pose.
3. **Blocker: neck repair damages the head.** The private v10 experiment clips
   against an infinite oblique plane and retains selected hair triangles using
   color/spatial heuristics. Rear Rose and profile Shana expose holes and ragged
   fragments in the scalp/hair. That approach is rejected. The fitter now refuses
   those controls; the helper and controls are retained only with the private
   rejected study. Earlier statements that the neck join was corrected were
   premature and are superseded by this audit.
4. **Blocker: separate native pieces overlap the reconstruction.** A depth/part
   ownership render identifies Rose's original part 7 fringe in front of the
   new head (battle head part 10). The old fringe and new hair do not form one
   coherent silhouette. Meru's native separate ponytail also overlaps the
   reconstructed hair. Audit head-owned auxiliary pieces before replacing or
   retaining them. Do not hide pieces independently if the main head replacement
   might be refused by another geometry mod.
5. **Verification gap: field material routing.** Some offline field references
   use incomplete scene texture/palette routing. They are geometry/pose aids,
   not accurate evidence of in-game facial colors. Resolve native routing before
   calling the field comparisons material-correct.

## Character review

| Character | Current artistic verdict | Required attention |
| --- | --- | --- |
| Dart | Existing accepted runtime benchmark; include in the nine-head review | Recheck current field/battle poses and actual materials; preserve accepted bytes while studying alternatives |
| Rose | Reject current fit/neck repair | Turn and roll, fringe ownership, jaw paint, horn/circlet alignment, nape and rear hair holes |
| Lavitz | Needs correction | Register eyes/nose/jaw to the volume; retain the original battle head turn and field pose; inspect neck skin and profile |
| Shana | Reject damaged neck repair | One jaw, original head size/pose, natural ear–jaw–nape relationship, continuous braid root and rear scalp |
| Haschel | Reject current layered face | Remove the duplicate jaw; align nose, mouth, moustache and sideburns with the actual geometry; preserve mature identity and headband |
| Albert | Needs correction | Facial registration, original skull size, turn/pitch, coherent jaw/ear, hair and collar; resolve field reference materials |
| Meru | Needs correction | Original head pose, single face/jaw, bow and ponytail ownership, original proportions and collar contact |
| Kongol | Needs correction | Register broad jaw/eyes/nose; preserve original distinctive markings, mohawk and anatomy; remove excess generated neck |
| Miranda | Needs correction | Original face pose, one chin, ear/nape attachment, circlet and hair silhouette; inspect texture seams in profile |

## Correction sequence

Start with Haschel, where the standalone duplicate-face repro is unambiguous.
Keep the rejected calibrated-bake/neck prototype separate. Prepare a neutral
frontal source preserving the existing character design, with no exposed neck
column; reconstruct and inspect the actual textured mesh and an untextured
control. Use that result to choose the pipeline before spending inference work
on the remaining seven. A source illustration is an authoring input, not proof
of a corrected model. Do not repeat the flawed pose/bake across the party.

## Review and release criteria

- One facial surface reads as one face from front, quarter, both profiles and
  rear-quarter angles. No displaced duplicate jaw, ghost eyes/lips or paint
  masquerading as geometry. Check a lossless textured image against its
  untextured counterpart and authored facial landmarks.
- Keep original skull size and original head pose. Compare each field/battle
  head against the source on the same unchanged body, pose and camera. Check
  facial direction separately from the camera's orbit angle.
- The chin ends in one jaw; the neck enters beneath the skull behind that jaw.
  Ears, mastoid/nape and hair roots remain continuous. No neck skin stretching
  into the face, holes, floating skin fragments or visibly doubled interfaces.
- Retain original character identity, age, facial proportions, ornament shapes,
  hairstyles, markings and color palette. Added detail must suit the game world.
- Audit auxiliary head/hair pieces as a unit. Correct geometry/material bindings
  must compose with CharHD, ModelsHD and other texture replacements; original
  source tables, CPU animation indices and other geometry owners keep priority.
- Render matched lossless head and whole-body comparisons for all nine. Inspect
  animation poses, especially field turns and battle idle, attack, damage,
  victory and death. Finite vertex tests alone do not prove natural anatomy.
- Verify source identities, UV/texture hashes, selected Dart preservation,
  supported compilation and native visual/animation behavior. Record physical
  Steam Deck performance separately; offline rendering is not Deck validation.
- Obtain explicit owner approval of all nine before runtime selection, main or
  release. Publish approved custom code, meshes and artwork through normal
  installation; exclude original reference pixels/data, discs, saves, private
  diagnostics, credentials and authoring runtimes.

## Latest correction checkpoint

The neutral-source study is stored under `head-program-v3`. The reconstruction
runner defaults to coherent neural surface paint; legacy unregistered projection
is now an explicit reproduction option. A separate hash-bound landmark bake
replaces frontal facial paint with one registered source. Profile facial paint
is prohibited in that tool. Rose's output reproduced pixel-for-pixel from the
portable custom inputs. Eight sampled facial mapping grids do not fold. These
checks validate correspondence mechanics, not the artistic precision of every
hand-authored landmark.

All eight now have field/battle fits retaining original anatomical frames and
bone poses, using explicit crown/chin 3D points. Neutral framing anchors are
posed only after selection. Sixteen fitted heights and native frames verified;
192 indexed/RGBA/mapped CPU construction combinations passed. There are 198
lossless original-body comparisons across all nine, including the unchanged
accepted Dart head. The headless probe logged a missing default-font fixture;
its model checks completed successfully. No game or Steam Deck was tested.

The new front faces remove the earlier layered-face defect, but the set still
fails review: auxiliary native hair overlaps Rose/Meru, and several ear/nape,
underside-jaw and hair transitions remain rough in profile. Keep the goal active
and keep these candidates out of normal runtime selection, main and release.
Do not relabel construction success as approval readiness.

## Registered surface checkpoint · v4

The latest public custom registry is `head-program-v4`, with sixteen field/battle
bindings. The selected CharHD runtime assets remain unchanged. The frontal paint
now has explicit anatomical limits for source ears: an ear cannot be transferred
onto a cheek or sideburn outside its authored mesh volume. Profile/rear paint
adds hair and bounded ear detail only. Five facial feature volumes remain
unchanged by that pass. Source paint illustrations still require correspondence
controls; do not directly project them as final textures.

Haschel's clay control exposed actual folds in the reconstructed underside of
the mandible, in addition to the misplaced paint. A bounded welded relaxation
preserves topology/UVs and the central mouth/chin, followed by the registered
paint. His private full-profile face-paint experiments and stronger neck
retractions are rejected and are not in this registry. The retained candidate
improves the frontal double-face defect but still has rough profile skin and
sideburn transitions. No anatomical acceptance is claimed from the 252 moved
vertices or numerical geometry reproduction.

Fresh checks: sixteen finite, hash-bound, original-size/source-frame fits; 192
actual-source CPU construction combinations; twelve accepted Dart parts and
nineteen selected runtime files unchanged. Fifteen CharHD tests and windowless
field/battle GPU cutout pixel checks pass. The supported full build passes using
the existing checksum-verified FMV source bundle after its network download
failed. See `CHARHD_CUTOUT_CHECKPOINT.md` for commands, environment and limits.

There are now 252 matched lossless private comparisons: all nine, field/battle,
head/collar and whole body, front, both quarter/profile sides, rear and final
stored pose. The gallery opens as a local file without a web server. Its initial
QA found missing opposite/rear body views for the accepted Dart benchmark; those
were rendered from his unchanged accepted head and the original baseline body.
These views are not a full bespoke body upgrade or native gameplay evidence.

**Artistic verdict remains fail.** Frontal facial ownership is much clearer, but
Rose's retained original fringe, Meru's native ponytail, inferred profile/nape
paint and several underside-jaw transitions still need correction. Keep the
active goal and all-nine approval gate. Do not resume broad portrait generation
or count additional bake iterations as quality evidence; use matched textured,
clay and original-body views to resolve each specific remaining defect.
