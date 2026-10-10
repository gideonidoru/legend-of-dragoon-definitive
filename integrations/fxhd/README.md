# FxHD

Independent effects artwork mod for Legend of Dragoon: Definitive, included in the normal installation and controlled by the existing Mods menu.

The full retail catalog contains 6,466 texture-source bindings and 2,160 unique 2× neural detail maps: common battle particles, Dragoon spells and transformations, item spells, enemy/boss attacks, battle cutscenes, field effects and animated save points, world clouds/snow/smoke, shadows and title flames. Every source is selected. Shared palette-live detail follows the original indices and resolves colors from the current palette, preserving original transparency, blend mode and animation. The six earlier explicit field RGB replacements retain priority.

Procedural lightning, lines, gradients and pixel particles render at the configured internal resolution; capture/distortion effects sample the live rendered frame. The public consumer ledger assigns every implementation and support class a route. UI lettering/reticles remain UIHD; characters/props and environment artwork retain their respective mods.

Custom detail PNGs total 42,080,098 bytes. One persistent GPU companion uses 16 MiB; CPU ownership uses 4 MiB plus a lazy 16 MiB detail map. Decoding uses the engine's bounded shared cache. Native writes, copies, row animation, world-menu backups and mod changes preserve ownership. No inference runs in game and no GPU texture is allocated per particle.

This is development artwork with full source coverage. Native all-campaign appearance and physical Steam Deck acceptance are still pending. See docs/definitive/FXHD_PRODUCTION.md and production/{full-coverage,consumer-coverage}.json for counts, hashes and validation. Code is AGPL v3; artwork retains the notices in ARTWORK-NOTICES.txt. Original game files, diagnostics, weights and inference tools are not installed.
