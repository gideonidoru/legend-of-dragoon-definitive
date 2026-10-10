# CharHD

Character models and materials for Legend of Dragoon: Definitive, development version 0.3.0.

Dart's owner-approved reconstructed head, sculpted hair and 2K painted material are now bundled in CharHD, alongside the preferred custom body shapes and the existing armor atlas. Field and normal battle sources are bound by exact geometry identities. Enable or disable `charhd` in the game's Mods menu; the custom model and its texture switch together. No separate experimental JAR, download or model-pack copy is required. ModelsHD may remain enabled for the rest of the game, but Dart's curated CharHD reconstruction also works without it.

The complete bespoke body remains unfinished. Armor/clothing, hands, boots and weapons are now in scope for all nine normal party characters, with faces/hair for the other eight first. See the [party reconstruction plan](../../docs/definitive/PARTY_RECONSTRUCTION.md), [source audit](production/party-reconstruction/source-audit.json) and [promotion evidence](production/party-reconstruction/DART_PROMOTION.md).

The existing source-bound battle atlas adapter retains the Dart armor development pilot. Another 678 restoration candidates remain under art review; their publication does not select them for runtime delivery. The field texture adapter remains available. This is not a claim of complete bespoke character, field/NPC/enemy artwork, animation or Steam Deck validation.

CharHD is included by the normal Definitive build and package. It requires this matching engine's geometry/appearance hooks; unmodified Severed Chains and older Definitive binaries are not supported. Unknown source geometry, other geometry owners, existing appearance replacements and native vertex-index consumers keep their established route. Invalid custom resources retain existing models. Disabling CharHD restores the original or selected ModelsHD presentation.

Custom authoring inputs, editable meshes and receipts remain versioned in `experiments/dart-modelshd-charhd/reconstruction`; that directory records the original experimental checkpoint. Only the accepted selected assets in `runtime-assets/charhd/characters` ship. No neural model, inference runtime, original extracted controls, discs or saves are bundled.

Code: AGPL v3. Preserve [ARTWORK-NOTICES.txt](ARTWORK-NOTICES.txt), upstream notices and the TripoSR supplier license. This unofficial fan project does not relicense the underlying game designs.
