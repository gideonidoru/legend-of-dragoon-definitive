# Dart: complete actor for visual review

This milestone delivers a whole, editable Dart review candidate: body, armor,
hands, boots, accepted CharHD head/hair and a separate custom sword. It replaces
the disconnected body-part approach in the authoring experiment with a continuous
body and an authored deformation rig. It is ready for owner inspection, not
selected for the game, main branch, installer or release.

## Inspect

Open `integrations/charhd/production/party-reconstruction/dart/complete-model-v1/viewer`
through a local HTTP server. The viewer contains all its dependencies; no CDN,
account, original game files or neural runtime is needed. The owner session serves
only this custom review directory on the Mac Studio at port 38925. From another
Mac on the same network, use the Studio's address rather than `localhost`.

Rotate, zoom, switch among neutral, relaxed, ready, guard and stride, or inspect
clay/wireframe. “Earlier static sculpt” loads the previous original-design-v3
candidate. That comparison uses the same studio and scale convention, but the
poses differ. It is not a matched native animation comparison. Download model
exports the currently selected GLB, with textures embedded.

## Construction

- Original normal-form Dart design remains the reference: asymmetric red armor,
  bare right upper arm, bandana, gold hair, original belt arrangement, dark
  trousers and tan boot cuffs. A clean front modeling pose separates the arms.
- Offline TripoSR produces a 30,000-triangle authoring volume from custom artwork.
  The generated head is removed; the owner-approved, public CharHD head is fitted
  into the collar. The selected runtime head, body packs and texture bytes are
  unchanged. Native packet winding is reversed for glTF; supplied head normals
  and the custom atlas are retained.
- The continuous body contains 26,581 triangles; head 5,748. Sword adds 498,
  giving 32,827 triangles in armed poses and 32,329 in neutral.
- The body keeps its authored atlas through position-only skin weights. Equal
  positions at UV seams receive equal deformation. Shoulder plates follow the
  chest with a feathered lower boundary. Rear/side smoothing is bounded to 8 mm;
  front projection and floor anchors are protected. A shoulder-aware head cut
  retains the raised shoulder contours. The head is counter-turned into a neutral
  facing direction and positioned by its neck-base center, not its hair bounds;
  the base overlaps the collar by 20 mm. This corrects the earlier flat shoulder
  cut and offset head fit reported by the owner.
- A registered front/side/rear paint sheet replaces noisy reconstruction colors
  with clearer armor, belt, trouser and boot construction. The transfer checks
  visibility and leaves mesh/UVs unchanged. The opposite side still uses inferred
  and adjacent paint; rear shapes are inferred rather than verified from a new
  artist-built turnaround. Projection paint contains some shading, so this is
  not a complete physically based material set.
- Fifteen authored control bones drive approximate dual quaternion skinning.
  The five controls are studio poses, not game animation. GLBs contain baked
  posed geometry; the editable skin controls/weights are in `authoring-skin.npz`.
  This is not a standard glTF animated rig or an engine integration.

The independent blend implementation follows the mathematics described by
[Kavan et al., 2008](https://users.cs.utah.edu/~ladislav/kavan08geometric/).
It uses NumPy/SciPy locally; no supplier shader code is copied. Three.js 0.170.0
is vendored for the review viewer under its MIT license. TripoSR attribution and
MIT license remain in the existing reconstruction directory. Preserve the
project's [asset policy](ASSET_POLICY.md), upstream license and original game's
character attribution.

## Evidence and limitations

Receipts pin artwork, input volume, paint, tool revisions, final GLBs and builder
hashes. The authoring validator checks all five payloads, finite attributes,
nondegenerate triangles, UV ranges, unchanged body topology/UVs across poses,
identical head positions across poses and outward head winding. Thirteen independent
authored fixtures cover identity, rigid motion, half-turn radius, antipodal
rotations, translation, input rejection, IK lengths, UV duplicates and retaining
high shoulder geometry while removing the generated head.

Browser verification loads the actual models, inspects front/quarter/side/rear,
switches materials and poses, and checks desktop/mobile presentation. These are
studio and integrity results. They do not establish self-collision safety,
original-animation fidelity, gameplay behavior or Steam Deck performance.

The new body atlas does not yet map to original-face texture replacements. Keep
the selected CharHD/ModelsHD route unchanged while building source-specific
field/battle articulation, appearance ownership and alternate-texture fallback.
Before production selection, review grip/armor contacts, inferred rear shapes,
head/body proportion and costume fidelity; then validate native motions and
CharHD/other-mod compatibility. Owner visual approval is required before this
experiment enters main or a release. Acceptance of the concept artwork is not
acceptance of this mesh.

## Reproduce

Use the already isolated Python tooling with NumPy, SciPy, trimesh and Pillow:

```sh
python experiments/dart-modelshd-charhd/build-complete-dart.py \
  --mesh integrations/charhd/production/party-reconstruction/dart/complete-model-v1/body-rest-source.glb \
  --previous integrations/charhd/production/party-reconstruction/dart/original-design-v3/body-sculpt-v1.glb \
  --output /absolute/new/authoring-output
python experiments/dart-modelshd-charhd/test-continuous-rig.py
python experiments/dart-modelshd-charhd/verify-complete-dart.py
python scripts/verify-party-authoring.py
```

Output must be a new directory. No game files are read by these tools. Art
generation used the built-in image editor; the exact prompts and custom input
images are versioned beside the candidate. The volume receipt records the
offline, pinned TripoSR runner. Original disc data, source reference controls,
unchanged extracted models/animations and runtimes remain outside this payload.
