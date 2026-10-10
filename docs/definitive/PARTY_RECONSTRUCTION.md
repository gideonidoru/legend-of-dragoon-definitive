# Party reconstruction

The owner approved Dart's accepted head/hair candidate for normal CharHD delivery on October 10, 2026, then requested the remaining eight party characters followed by armor/clothing, hands, boots and weapons across all nine. CharHD is the one player-facing package. The supported scope is the nine normal field and battle representations; Dragoon forms and Divine Dart remain a later adaptation pass with their own source identities.

## Order and acceptance

1. **Ship the accepted Dart reference.** Preserve its exact head mesh/material. Package the selected custom shapes and UVs together in CharHD, retain ModelsHD composition and restore coherent models when CharHD is disabled. Verify source, build, package and native battle behavior before main promotion. Do not create a downloadable release for every iteration.
2. **Faces and hair for the other eight.** Start with Rose, then Lavitz, Shana, Haschel, Albert, Meru, Kongol and Miranda. Author individual identities; retain hair silhouettes, circlets, facial hair and expressions. Build field and battle fits on the original pivots. Do not assume Dart's head-part index applies to another character. Rose already demonstrates separate face/fringe parts.
3. **Complete all nine bodies.** Rebuild armor or clothing panels, shoulder/hip volumes, hands and grips, boots and weapon surfaces. Keep original costume designs, asymmetric details, colors, weapon proportions and joint/attachment boundaries. Give unarmored characters authored cloth/skin treatment rather than invented armor. Haschel's fists/bracers are his weapon surfaces.
4. **Validate complete characters.** Compare before/after at gameplay size and close views, front/side/rear and stored poses; then native idle, attack, damage, victory, death and scene reloads. Verify indexed textures, CharHD atlas materials, another texture replacement, ModelsHD on/off and unknown custom owners. Profile three party actors plus effects on a physical Deck before changing default density/resolution. Native Mac construction does not prove these later gates.

## Per-character work

| Character | Costume and hands | Boots and weapon | Current bespoke state |
| --- | --- | --- | --- |
| Dart | Crimson armor paneling, gloves/grip, cloth legs | Brown/armored boots, broadsword | Accepted head/hair; preferred body shapes and armor atlas; complete body pending |
| Rose | Violet-black costume, gold trim, gloves | Tall fitted boots, slender sword | Source audited; custom head authoring started |
| Lavitz | Original green/silver armor, gauntlets | Greaves/boots, spear | Source audited |
| Shana | Original pale dress and decorative trim, natural hands | Original boots, bow/grip | Source audited |
| Haschel | Original martial clothing, hands and bracers | Original footwear, fists/bracers | Source audited; earlier neck studies remain references |
| Albert | Original royal armor/clothing, gauntlets | Original boots, spear | Source audited |
| Meru | Original dancer clothing, wrists/hands | Original footwear, hammer/grip | Source audited |
| Kongol | Original massive armor silhouette, large hands | Heavy footwear, axe/grip | Source audited |
| Miranda | Original white/green outfit, gloves/hands | Original boots, bow/grip | Source audited |

The [source audit](../../integrations/charhd/production/party-reconstruction/source-audit.json) binds all 18 source containers and records every part's source identity and local/posed bounds. It contains metadata only. Semantic part roles stay pending until checked against the private isolated-part renders; a bounding box is not anatomical proof. Original reference images and actor controls remain outside Git.

## Delivery contract

`runtime-assets/charhd/characters/characters.json` selects accepted custom packs, known ModelsHD baseline identities and optional exact UV/material bindings. The appearance event runs after automatic geometry refinement. CharHD builds its candidate from the original source material packets and only accepts its recorded baseline as a handoff; another custom geometry or appearance owner keeps priority. Geometry and material are installed together, after all checks pass. The source CPU tables, native animation hierarchy and script-facing indices remain unchanged.

The shared bounded engine reader is also used by ModelsHD, retaining its existing API facade and strict source/material interpolation rules. The head sampler uses texture unit 7, separate from current FxHD's integer sampler on unit 6. Normal CharHD packaging includes only selected resources and license notices; authoring images, editable GLBs and receipts remain public development assets.

This plan is in progress. The remaining eight heads and nine complete bodies are not yet finished or selected for release. Each candidate needs a comparison and actual model/material verification before being presented as an upgrade.
