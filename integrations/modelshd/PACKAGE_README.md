# ModelsHD 0.4.0

The first broad geometry smoothing pass for Legend of Dragoon: Definitive. All 1,343 supported canonical model containers were inspected; 1,004 receive some smoothing and 339 keep their originals. The complete mod contains 8,777 unique source-bound custom part replacements. Individual faces, hair and costumes have not been rebuilt.

The normal Definitive installer supplies this mod together with its compatible engine. Select HD artwork to load it, or original artwork to restore the native route on the next game/model load. Existing campaigns can enable ModelsHD through the game's mod menu.

For standalone installation, copy `mods/ModelsHD-0.4.0.jar` to a matching current Definitive engine's `mods` folder. Remove older standalone ModelsHD JARs to avoid duplicate mod IDs. Version 0.4 requires the shared geometry event in the corresponding Definitive release.

The pass keeps open joint borders, sharp creases, source part numbering and animation ownership. Flat parts with no geometric benefit and invalid topology retain their originals. Current UV/material words and declared texture dimensions pass through. Authored geometry owners take priority. Corrupt replacements fall back to native geometry.

Optional author overrides use `model-packs/modelshd/parts/<singlePartGeometrySha256>.json`; players need no authoring tools or external pack directory. The old whole-model `battle/` override path is not active in the 0.4 global route.

Headless construction and packaging checks do not establish visual acceptance, actual CharHD gameplay, all scene transitions or physical Steam Deck performance. See the included [coverage report](docs/definitive/MODELS_HD_WORLD_PASS.md) and [roadmap](docs/definitive/MODELS_HD_ROADMAP.md).

Source/mod code remains AGPL v3; preserve LICENSE and embedded attribution notices. Custom geometry is public and bundled. Game discs, original extracted assets, controls, saves, credentials and local runtimes are excluded.
