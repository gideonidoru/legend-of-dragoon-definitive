# ModelsHD 0.2.0 development package

Optional battle geometry for Legend of Dragoon: Definitive. This package contains mod code and a local recipe for the nine normal party models, nine Dragoon forms and Divine Dart. It contains no retail models or images.

This is a first surface pass. Individually remodeled faces, hair, costumes and anatomy remain in progress. Combined texture-mod gameplay, visual acceptance and physical Steam Deck performance are not yet verified.

## Engine requirement

Use a current Definitive source build containing the authored-model API introduced in commit `6efdc8b`. The earlier Deck recovery installer release lacks this API; ModelsHD will retain original models with a warning. This package does not update your engine or the guided installer.

## Generate your models locally

Use your existing extracted `files` directory. Install Python dependencies in a separate authoring environment; neither Python nor NumPy is a game runtime requirement.

```sh
python3 -m venv /PRIVATE/modelshd-tools
/PRIVATE/modelshd-tools/bin/python -m pip install -r scripts/requirements-visual.txt
/PRIVATE/modelshd-tools/bin/python scripts/build-modelshd-roster.py \
  --files /PRIVATE/game/files \
  --output /PRIVATE/modelshd-0.2.0
```

Choose a new output folder outside this tools folder. The builder leaves source files untouched and publishes all 19 packs together. Copy the generated `model-packs` directory into your development game folder. Keep `verification` and `roster-manifest.json` separately; controls are not upgrade packs.

Copy `mods/ModelsHD-0.2.0.jar` into the game's `mods` directory. Remove older ModelsHD jars from that directory, then select ModelsHD in the existing mod manager. It retains active native textures and typed HD texture dimensions. Custom GPU atlas mappings or other model owners keep their existing model unless a compatible handoff exists.

Deselect ModelsHD to restore the original route on the next model load. Missing or invalid packs retain original geometry. Source-derived geometry stays private and must not be uploaded to the public repository.

See the [roster evidence and expansion plan](docs/definitive/MODELS_HD_ROADMAP.md) and the [source integration documentation](https://github.com/gideonidoru/legend-of-dragoon-definitive/tree/main/integrations/modelshd). Source and mod code remain under [AGPL v3](LICENSE); this does not grant rights to retail game assets.
