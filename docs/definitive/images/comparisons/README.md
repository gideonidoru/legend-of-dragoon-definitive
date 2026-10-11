# README visual comparisons

These two presentation boards show original battle geometry beside the exact custom ModelsHD 0.3 geometry included in the October 10, 2026 FMVHD installer release. They are offline orthographic previews, not native-renderer captures or Steam Deck gameplay screenshots.

Both sides use the same original textures, keyframe 0, camera yaw of -18 degrees, framing and lighting. The upper row shows textured models; the lower row uses untextured geometry to make the surface changes visible. Dart changes from 870 to 2,946 triangles, and Meru from 806 to 2,687.

The candidate and image checksums were checked against the public model packs and the comparison records before adding these boards. Only the finished presentation PNGs are included here. Original model files, texture atlases, source controls and diagnostic workspaces remain separate.

These depict the first broad geometry refinement. They do not show new character textures, finished face/costume remodeling or accepted whole-game visual quality. Original character designs and artwork belong to their respective rights holders; custom mesh attribution is retained in the ModelsHD notices.

## Texture comparisons

`dart-textures-before-after.png` and `meru-textures-before-after.png` isolate texture changes on unchanged original geometry. Each side uses the same original keyframe, camera, framing, lighting and offline nearest-sampling renderer. The first view uses keyframe 0 at yaw 0; the second uses Dart keyframe 5 or Meru keyframe 6 at yaw pi/3. The presentation figures preserve the matched render pixels without retouching or additional sharpening.

Dart's after image uses the exact authored armor-v1 atlas selected by the development CharHD runtime pack. Meru's after image uses the public 4x candidate atlas; it is not selected runtime artwork. Both remain development artwork, with native visual and physical Steam Deck acceptance pending. These figures show original geometry rather than ModelsHD smoothing, so the texture changes are the only asset difference between each pair.

Before assembly, every pose image was checked against its inspection manifest, and each after atlas was checked against the corresponding public custom atlas. `texture-comparisons.json` records the figure, custom atlas, model/texture/animation source identities, renderer identity, keyframes and pose-render hashes. It contains no machine paths or private commands. Only the finished presentation figures and sanitized provenance are added here; original extracted assets, texture controls and private diagnostic boards remain separate.

Original designs and artwork belong to their respective rights holders. Custom texture artwork retains the CharHD notices and project asset policy. The figures are illustrative comparisons, not a claim of finished whole-game coverage or gameplay performance.

## Dart reconstruction showcase

`dart-head-reconstruction.jpg` is the unchanged finished 35-degree combat studio comparison from the accepted Dart reference study. The baseline is the earlier Definitive preferred face pass, not an untouched original head. The after view shows the actual reconstructed head geometry and painted material later selected for CharHD 0.3.0. It is a studio inspection, not a gameplay screenshot.

`dart-reconstruction-native.jpg` is the unchanged paired Mac renderer inspection from the same study. Both sides use the same enlarged actor inspection pose, camera, body artwork and environment. It is not normal gameplay framing or a Steam Deck capture. The candidate was captured through the historical experimental adapter; the subsequent normal CharHD promotion retained the exact selected custom geometry and material. The image does not establish current engine visuals or device performance.

The selected 2K material hash matches the public runtime `head-v1.png` and the historical experiment inventory. The promotion record selects the exact reference assets at `29c33e32926f0aaf3d465044a532dd77ce38d2c9`; historical experimental labels in those figures predate acceptance. `character-showcase.json` records finished-figure hashes, material identity and comparison scope without private paths or diagnostic logs. No image retouching, generated replacement pixels or additional sharpening was applied.

## Matched in-engine visual comparisons

`battle-visuals-off-on.jpg` and `battle-detail-off-on.jpg` show two matched camera views of Dart and Berserk Mouse in battle stage 0. These are real native Mac game-renderer captures, using the engine and selected artwork from the public `definitive-alpha-2026-10-11-envhd` installer, source `0af4f997e27f49783180cb2ff1c5a04012467289`.

Each side is captured at 1280 × 800. Camera coordinates, Dart's position, rotation, keyframe and normal 1× actor scale match. The closer view changes only the camera between figures. The baseline has no custom HD modules installed and disables Definitive's visual rendering controls. The enhanced session loads all seven published HD modules, including all four EnvHD resource packs, and enables the visual controls. The turn-order overlay is hidden on both sides; gameplay convenience settings and resolution otherwise remain the same.

The enhanced settings include SMAA, model/material and scene lighting, effect lighting, soft contact shadows, HD filtering, surface detail, interface protection, 0.25 scene bloom, 0.2 sharpening and 0.5 edge smoothing. A still battle cannot demonstrate every one of those features: no active spell is shown, and Skurfa field artwork, films and audio are outside these views. Audio was muted during capture.

The capture harness uses an isolated fresh game state and seeded battle fixture. It holds actor animation and uses the renderer's debug frame advancement to update the camera studies. Initial paused captures reproduced the published engine's zero-tick viewport shrink; final figures advance a simulation step per presentation. No production renderer or shader fix was applied. These controlled comparisons do not establish continuous gameplay correctness, a viewport fix or physical Steam Deck performance.

The framebuffer pixels are placed side by side without resizing, cropping, retouching or additional sharpening. Only labels, a separating gutter and JPEG encoding are added. `in-engine-comparisons.json` records settings, camera coordinates, image hashes and verified published engine/module hashes. Finished figures and sanitized provenance are public; original game files, discs, saves and the private capture workspace are excluded.

`field-visuals-off-on.jpg` adds a native forest field comparison at cut 5, scene 0. It uses separate fresh sessions and the native field camera, with identical actor positions, rotations and frozen keyframes verified from both session logs. The field starts directly from a controlled title-to-field fixture, with normal title resource cleanup. Both sides retain the same layered-scene arrangement and character scale. The selected field artwork belongs to the background module already attributed in the project credits; it is not new EnvHD painting or proof of every field restoration. The same published-engine, processing and device-validation limits apply.
