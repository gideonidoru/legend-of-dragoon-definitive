Dart · CharHD review

This folder is portable and contains every dependency and embedded-texture model.
Use Python 3 to serve this folder, then open its address in a browser:

  python3 -m http.server 38925 --bind 127.0.0.1
  http://127.0.0.1:38925/

It can also be hosted on any static HTTP server. Opening index.html directly may
fail because browsers restrict local model requests. No account or CDN is used.

Drag to rotate; pinch/scroll to zoom. Select a pose, materials, clay or wireframe.
The current pose can be downloaded as a GLB with textures embedded. The earlier
static sculpt is a different authored pose, not a native animation comparison.
The review/ folder contains actual-geometry before/after and inspection images.

This is a visual-review candidate. Native game articulation, alternate texture
binding and Steam Deck performance have not been validated. Main/release selection
requires owner approval. Installed runtime assets are unchanged.

Custom Definitive fan assets: preserve project and original game attribution.
Three.js 0.170.0: MIT; see vendor/THREE-LICENSE.txt.
Project source: https://github.com/gideonidoru/legend-of-dragoon-definitive
Construction: docs/definitive/DART_COMPLETE_MODEL_REVIEW.md on authoring branch.
