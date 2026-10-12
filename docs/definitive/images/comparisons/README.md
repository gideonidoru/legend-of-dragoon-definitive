# Visual comparisons

The main README uses matched captures from the native Mac game renderer.

| Figure | What it shows |
| --- | --- |
| `field-visuals-off-on.jpg` | The forest cliff field scene, including its layered scenery. |
| `battle-visuals-off-on.jpg` | Evergreen Forest’s repainted ground, trees and foliage, with Dart and the battle interface. |
| `battle-detail-off-on.jpg` | A closer view of Dart's reconstructed head, costume and surface rendering. |
| `meru-native-off-on.jpg` | Meru's geometry, costume and surface rendering in a front-facing pose. |

## Matched in-engine visual comparisons

Each side is captured at **1280 × 800** with matching camera coordinates, character position, rotation, keyframe and normal **1× actor scale**. No actor enlargement is used. The field scene uses its native camera at cut 5, scene 0. The refreshed battle scenes use encounter 0, stage 36 (Evergreen Forest). All thirteen static material regions in this stage use HD repaints.

The baseline disables all seven HD modules and Definitive's visual rendering controls. The enhanced sessions enable the seven modules, including all four EnvHD resource packs, and the visual controls. The turn-order overlay is hidden on both sides; resolution and gameplay convenience settings remain the same. Meru uses a separate battle fixture with her as the sole active party member. Both sides configure the Original font; UIHD automatically supplies Original HD text and the integrated small native battle digits on the enhanced side. Other font families remain on their current paths, so these images do not imply complete font coverage.

The enhanced controls include SMAA, model/material and scene lighting, effect lighting, soft contact shadows, HD filtering, surface detail, interface protection, 0.25 scene bloom, 0.2 sharpening and 0.5 edge smoothing. No active spell is shown, and still images cannot demonstrate film playback, audio or every rendering feature. Audio was muted during capture.

The captures use isolated fresh game states, seeded battle fixtures, held character keyframes and renderer debug frame advancement. The field fixture performs normal title resource cleanup and pauses scripts after loading. These are controlled visual comparisons, rather than continuous gameplay or Steam Deck performance measurements.

The figures preserve the framebuffer pixels without resizing, cropping, retouching or additional sharpening. Only labels, a separating gutter and JPEG encoding are added. `in-engine-comparisons.json` records the exact engine revision, module and image hashes, settings, camera coordinates and capture limitations. The refreshed battle engine and custom modules use main revision `4818f2d9c285b7541f4f4ce82cbedde5b3d19fcf`. The unchanged field figure retains revision `0af4f997e27f49783180cb2ff1c5a04012467289`. Per-figure hashes distinguish their packages; ModelsHD 0.4.0 and CharHD 0.3.0 are used in both.

Only finished figures and sanitized provenance are included here. Original game files, discs, saves and private capture controls remain separate. Original designs and artwork belong to their respective rights holders; custom artwork and geometry retain the project and module notices.

## Archived illustrations

The following figures are retained for their historical image records and are no longer shown in the main README:

- `dart-before-after.png` and `meru-before-after.png`: offline textured and clay views of the earlier ModelsHD 0.3 geometry, using original textures and matching framing.
- `dart-textures-before-after.png` and `meru-textures-before-after.png`: offline texture-only comparisons on original geometry. Meru's image shows a separate 4× texture study.
- `dart-head-reconstruction.jpg`: a studio head comparison whose baseline already includes earlier face artwork.
- `dart-reconstruction-native.jpg`: an older native-renderer head comparison with an enlarged actor.

`texture-comparisons.json` and `character-showcase.json` preserve the image and asset identities for those illustrations. They do not describe the new native-renderer gallery.
