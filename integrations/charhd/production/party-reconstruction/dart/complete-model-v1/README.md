# Dart · full actor review

Open the `viewer` directory through a local HTTP server, then inspect Dart in
your browser. All viewer dependencies and textures are included.

```sh
python3 -m http.server 38925 --bind 127.0.0.1 --directory viewer
```

Open <http://127.0.0.1:38925/> on that computer. To view from another computer,
serve this custom directory on the local network and use the server's network
address. Opening `index.html` directly may fail because browsers restrict local
model requests.

Five full-actor poses, clay, wireframe, rotation and GLB downloads are available.
The earlier static sculpt is included for comparison. Models are real geometry;
poses are authored studio controls, not native game playback.

This is the owner-review candidate. It does not change the installed game or
select new assets for main/releases. See [construction and validation](../../../../../../docs/definitive/DART_COMPLETE_MODEL_REVIEW.md).

Custom Dart fan artwork/geometry belongs to the Definitive project; preserve
the original game's character attribution and repository license notices.
Three.js is MIT licensed; see `viewer/vendor/THREE-LICENSE.txt`.
