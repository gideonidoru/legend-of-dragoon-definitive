# Dart AA reconstruction experiment

Approval required before main or release. This branch contains a new custom head and 2K material set for Dart, fitted to his original field and battle head pivots. The body currently uses the previous ModelsHD pass; this is not a finished bespoke AA character.

The custom geometry is in `models/parts`. The geometry-bound UVs and texture are under `../resources/charhd-experiment/reconstruction`. The opt-in CharHD extension paints only these exact reconstructed heads. Other character textures and original CPU animation/attachment data retain their existing routes.

`dart-head-source-v1.png` is generated custom source artwork, not an in-game result. `dart-material-views-v1.png` is authored material input, not evidence of runtime quality. Comparisons must use the exported mesh or native game. No original full model controls or game files belong in this directory.

Local tools: pinned MIT TripoSR source revision 107cefdc244c39106fa830359024f6a2f1c78871, model SHA-256 429e2c6b22a0923967459de24d67f05962b235f79cde6b032aa7ed2ffcd970ee; CPU inference, mesh simplification and UV baking. The tools and weights are external prerequisites and are not packaged with the game. See TRIPOSR-LICENSE.txt. Custom source scripts, geometry and textures are distributed under the project AGPL v3 terms. Dart and The Legend of Dragoon belong to their original owners; this is an unofficial fan project.

Build the isolated mod with the parent experiment init script. Native probe inputs and all original controls must be private external paths. No normal installer or release selects these head overrides.
