# Party reconstruction

The owner approved Dart's accepted head/hair candidate for normal CharHD delivery on October 10, 2026, then requested the remaining eight party characters followed by armor/clothing, hands, boots and weapons across all nine. CharHD is the one player-facing package. The supported scope is the nine normal field and battle representations; Dragoon forms and Divine Dart remain a later adaptation pass with their own source identities.

## Order and acceptance

Original character art controls every new design. The owner rejected the current studies as close but inexact on October 10, 2026. Follow the [character art fidelity gate](CHARACTER_ART_FIDELITY.md): verify original silhouettes, costume construction, asymmetry, colors and motif layouts before further reconstruction. Generated studies may demonstrate finish but cannot establish canonical details. Existing accepted runtime selections remain unchanged while development work is corrected.

1. **Ship the accepted Dart reference.** Preserve its exact head mesh/material. Package the selected custom shapes and UVs together in CharHD, retain ModelsHD composition and restore coherent models when CharHD is disabled. Verify source, build, package and native battle behavior before main promotion. Do not create a downloadable release for every iteration.
2. **Faces and hair for the other eight.** Start with Rose, then Lavitz, Shana, Haschel, Albert, Meru, Kongol and Miranda. Author individual identities; retain hair silhouettes, circlets, facial hair and expressions. Build field and battle fits on the original pivots. Do not assume Dart's head-part index applies to another character. Rose already demonstrates separate face/fringe parts.
3. **Complete all nine bodies.** Rebuild armor or clothing panels, shoulder/hip volumes, hands and grips, boots and weapon surfaces. Keep original costume designs, asymmetric details, colors, weapon proportions and joint/attachment boundaries. Give unarmored characters authored cloth/skin treatment rather than invented armor. Haschel's fists/bracers are his weapon surfaces.
4. **Validate complete characters.** Compare before/after at gameplay size and close views, front/side/rear and stored poses; then native idle, attack, damage, victory, death and scene reloads. Verify indexed textures, CharHD atlas materials, another texture replacement, ModelsHD on/off and unknown custom owners. Profile three party actors plus effects on a physical Deck before changing default density/resolution. Native Mac construction does not prove these later gates.

## Per-character work

| Character | Costume and hands | Boots and weapon | Current bespoke state |
| --- | --- | --- | --- |
| Dart | Crimson armor paneling, gloves/grip, cloth legs | Brown/armored boots, broadsword | Accepted head/hair; preferred body shapes and armor atlas; complete body pending |
| Rose | Violet-black costume, gold trim, gloves | Tall fitted boots, slender sword | Development head fits; initial body partitions |
| Lavitz | Original green/silver armor, gauntlets | Greaves/boots, spear | Development head fits; initial body partitions |
| Shana | Original white tunic and shorts, blue trim and bronze fittings, natural hands | Original boots, bow/grip | Crown/chin proportion correction; initial body partitions |
| Haschel | Original martial clothing, hands and bracers | Original footwear, fists/bracers | Development head fits; initial body partitions |
| Albert | Original royal armor/clothing, gauntlets | Original boots, spear | Separate field/battle proportion correction; initial body partitions |
| Meru | Original dancer clothing, wrists/hands | Original footwear, hammer/grip | Correct field face part 6; development head/body fits |
| Kongol | Original massive armor silhouette, large hands | Heavy footwear, axe/grip | Development head fits; initial body partitions |
| Miranda | Original white/red-brown and gold outfit, gloves/hands | Original boots, bow/grip | Development head fits; initial body partitions |

The [source audit](../../integrations/charhd/production/party-reconstruction/source-audit.json) binds all 18 source containers and records every part's source identity and local/posed bounds. It contains metadata only. Semantic part roles stay pending until checked against the private isolated-part renders; a bounding box is not anatomical proof. Original reference images and actor controls remain outside Git.

## Delivery contract

`runtime-assets/charhd/characters/characters.json` selects accepted custom packs, known ModelsHD baseline identities and optional exact UV/material bindings. The appearance event runs after automatic geometry refinement. CharHD builds its candidate from the original source material packets and only accepts its recorded baseline as a handoff; another custom geometry or appearance owner keeps priority. Geometry and material are installed together, after all checks pass. The source CPU tables, native animation hierarchy and script-facing indices remain unchanged.

The shared bounded engine reader is also used by ModelsHD, retaining its existing API facade and strict source/material interpolation rules. The head sampler uses texture unit 7, separate from current FxHD's integer sampler on unit 6. Normal CharHD packaging includes only selected resources and license notices; authoring images, editable GLBs and receipts remain public development assets.

The [public authoring record](../../integrations/charhd/production/party-reconstruction/AUTHORING.md) describes the individual head proportion controls, the 192 passing development head construction checks and all nine custom body/material studies. Dart's approved battle image demonstrates the desired finish; its design still has to match original character art. A separate neutral field sculpt now covers 14 body parts. Shared immutable images and renderer texture leases allow one body upload across rigid parts; the development Dart model passed 24 source construction combinations and a native two-texture assertion. The [Dart body record](../../integrations/charhd/production/party-reconstruction/dart/BODY-VALIDATION-v2.json) explicitly records the failed moving-geometry visual gate: 21 captured native poses exposed open partitions and detached regions. The [inventory](../../integrations/charhd/production/party-reconstruction/authoring-inventory.json) keeps these numerical checks separate from fidelity and animation acceptance. Character design fidelity, complete articulated topology, grips, rear materials, the other eight field adaptations and physical Deck residency/performance remain unfinished. Verify Dart's design and solve its moving surface before scaling the body work to the other eight; do not promote a beautiful still pose that changes the original design or breaks during animation.

This plan is in progress. The remaining eight heads and nine complete bodies are not yet finished or selected for release. Each candidate needs a comparison and actual model/material verification before being presented as an upgrade.

## Original-design party pass — October 11, 2026

All nine normal-form character controls have now been located and compared with a new custom artwork set. The [original-design CharHD pass](PARTY_ORIGINAL_DESIGN_V3.md) includes nine public editable static GLBs, their own 2K textures, exact prompts, reconstruction receipts and front/side/rear inspection boards. This corrects the earlier Shana dress description: the selected normal-form CG reference shows a white tunic and shorts. No new native replacement, field/battle fit, animation acceptance or physical Deck performance is claimed. The existing selected CharHD resources remain byte unchanged.
