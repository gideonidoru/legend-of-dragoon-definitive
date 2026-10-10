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
