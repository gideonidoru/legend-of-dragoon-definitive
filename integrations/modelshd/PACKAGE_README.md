# ModelsHD 0.3.0

The complete mod contains custom geometry for all 19 party battle variants: nine normal forms, nine Dragoon forms and Divine Dart. These custom models are public and tracked with source identities and checksums. They are also included in the normal Definitive install. No separate generation or transfer is required for players.

This is a first surface pass. Individually remodeled faces, hair, costumes and anatomy remain in progress. Combined texture-mod gameplay, visual acceptance and physical Steam Deck performance are not yet verified.

## Installation

The normal Definitive installer downloads the compatible engine and ModelsHD together. The HD artwork setting loads the bundled mod; original artwork removes it from the managed workspace. New campaigns include installed mods by default; existing campaigns keep their mod selection and can enable ModelsHD through the game’s mod menu.

For standalone use, copy `mods/ModelsHD-0.3.0.jar` into a current Definitive engine’s `mods` folder and select ModelsHD. Remove older standalone ModelsHD JARs from that folder to avoid duplicate mod IDs. The earlier Deck recovery engine lacks the required authored-model API and will retain originals with a warning.

An optional authored pack at `model-packs/modelshd/battle/<sourceGeometrySha256>.json` takes priority over the embedded roster. Unsupported geometry/material owners and invalid input retain original models. ModelsHD preserves active texture settings; it does not select or supply new texture images.

## Optional authoring tools

The included scripts reproduce candidates from your extracted game files. Python, Pillow and NumPy are authoring dependencies only; they are not required to install or play. Keep raw extraction, source controls and diagnostics in a separate working folder. Publish custom candidates with provenance under the project’s public-asset policy.

See the [roster and expansion plan](docs/definitive/MODELS_HD_ROADMAP.md) and the [source documentation](https://github.com/gideonidoru/legend-of-dragoon-definitive/tree/main/integrations/modelshd). Code remains [AGPL v3](LICENSE); preserve the embedded model attribution notices. Disc images, the original extracted data set, saves, credentials and local runtimes are excluded.
