# Campaign presets — 2026-10-09

Choose **Definitive** or **Faithful** on the New Campaign screen. Definitive is the initial selection for a new installation. A remembered selection still takes precedence. Existing campaigns are never silently rewritten; using an existing campaign's preset editor is an explicit change. Artwork, device controls, audio and rendering remain independent.

Both presets explicitly keep normal Additions, 1.0 timing windows, manual Dragoon Additions, normal button mashing, 32 inventory slots, status effects, locked story party, retail encounters, 50% reserve-character XP, level caps 60/5, item stacks of one, ordinary Dragoon actions and no equipment-effects extension in Dragoon. Neither installs a reward multiplier or difficulty overhaul. Reward/level history cannot be undone by switching presets.

| Setting | Definitive | Faithful |
| --- | --- | --- |
| Save anywhere / autosave after battle | On | Off |
| Run by default | On | Off |
| Quick text | Always | Hold |
| Battle transition / transformation | Fast / Short | Normal / Normal |
| Enemy HP bars / element icons / turn order | On | Off |
| Inventory icons / group sorting | Enhanced / Alphabetical | Retail / Retail |
| Automatic dialogue / delay | Off / 1 second | Off / 1 second |

The complete 27-setting contract is `src/main/java/legend/definitive/presets/PresetDefinition.java`. `DefinitivePresets` adapts it to registered campaign/save settings and rejects unknown entries, invalid types and global settings. Detached preset collections suppress callbacks while enumerating choices. Other upstream presets and mod-provided presets remain available.

**Faithful is an audited settings target, not bit-perfect retail emulation.** Severed Chains fixes, QoL equipment hints and engine behavior remain present. External gameplay mods can override the contract and are outside this preset's guarantees; Dragoon Modifier and Battle Rewards are not bundled. HD artwork is independently removable in the launcher.

## Evidence

On the development Mac Studio, JDK 25 / Gradle 9.1.0, `./gradlew --no-daemon --console=plain macGameplayTest -PrunTests` passed four native gameplay checks on 2026-10-09: engine startup, battle load, Guard retaining enemy HP, and detached preset creation plus campaign configuration save/load round trips. Headless tests also check the explicit shared mechanics, deliberate differences and immutable maps. This establishes initialization and persistence behavior, not a complete campaign, retail equivalence, menu readability or Steam Deck behavior.
