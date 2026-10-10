# Battle model mapping evidence

This mapping was inspected on October 10, 2026 against the current Definitive source. It labels extraction-relative references using the engine's loading routes and name/encounter tables. It does not publish geometry, original scripts, textures, or extraction bytes.

## Exact combatant identity

`Battle.loadCombatantTmdAndAnims` selects enemy model directory `SECT/DRGN0.BIN/(3137 + charIndex)`. `Battle.loadCombatantModelAndAnimation` selects member `32` as the `CContainer`. `Monsters.monsterNames_80112068[monsterId]` supplies the displayed actor name. `LodEncounters` supplies registry encounter names and their explicit `Encounter.Monster` IDs. These are exact actor/name mappings; no appearance recognition is used. Sources: [Battle loading methods](../../src/main/java/legend/game/combat/Battle.java), [Monsters name table](../../src/main/java/legend/game/combat/Monsters.java), [encounter registration](../../src/main/java/legend/lodmod/LodEncounters.java).

The `253+` name bank includes Merchant, Master Tasman, Rose, guards, boss parts, summoned/temporary objects and unnamed slots. Therefore `253+` is labeled `story-combatant`, not automatically `boss`. An `ArenaEncounter` is marked as arena context. `MelbuEncounter` explicitly supplies Melbu's ordinary encounter combatants. `PhasedEncounter` names the phase-sound directory but does not itself prove a combatant's anatomical identity or boss status. Sources: [ArenaEncounter](../../src/main/java/legend/game/combat/encounters/ArenaEncounter.java), [PhasedEncounter](../../src/main/java/legend/game/combat/encounters/PhasedEncounter.java), [MelbuEncounter](../../src/main/java/legend/game/combat/encounters/MelbuEncounter.java).

An additional **decoded script audit** provides positive boss-action evidence. `Battle.loadEnemyDropsAndScript` loads `SECT/DRGN1.BIN/(monsterId + 1)`. The checked-in metadata and official `org.legendofdragoon:script-recompiler:0.7.11` disassembler parsed 402 nonempty combatant scripts with zero failures. Sixteen IDs contain a decoded `CALL 172`, which the engine explicitly routes to `scriptSetPostBattleActionBossKill`: `262, 265, 268, 269, 270, 283, 294, 299, 301, 308, 311, 332, 336, 357, 363, 387`. These sixteen scripts have zero disassembler warnings. The scanner also checks a statically resolved legacy action `3` in `CALL 173` (`scriptSetPostBattleAction`); it added no additional IDs in this extraction. Sources: [Battle function table and boss-action methods](../../src/main/java/legend/game/combat/Battle.java), [checked-in interpreter metadata](../../patches/meta), [metadata-only audit](model-catalog/combatant-role-evidence.json).

The mapper promotes those positively evidenced combatants to `boss`, except ID 269 (Lloyd) because an explicit arena encounter takes precedence. Other special-bank combatants remain `story-combatant`; absence of a boss-action call does **not** imply a character is not a boss. One positively evidenced script belongs to unnamed ID 357, whose actor label remains unresolved. This role describes a documented boss-kill workflow, not an exhaustive narrative taxonomy.

Display labels with explicit `(head)`, `(body)` or `(arm)` suffixes supply a **candidate** multipart actor family, e.g. Virage. Identical labels can also supply a candidate family. This is separate from measured geometry/component reuse. A name match does not prove two models have compatible topology, animation order, UVs or materials; names such as `Tentacle`, `Missing` and blank labels remain ambiguous. Enemy taxonomy or recolor ancestry must not be invented from neighboring IDs.

## Exact battle-stage context

`Battle.loadStage(stage)` selects directory `2497 + stage`. Its nested `/0` directory is sent to `loadStageTmdAndAnim`, which selects member `0` as its model. Thus supported inventory path `SECT/DRGN0.BIN/(2497 + stage)/0/0` is stage geometry, not a character. The 96-entry `Ambiance.stageAmbiance_801134fc` table limits the retail stage namespace used by the mapper to IDs 0–95. There is no complete descriptive stage-name table in this loader. Five Melbu stage labels are explicitly documented in `melbuStageIndices_800fb064`; they are included as context. `LodSpells` and `DragonSpell.getBattleStage` associate some stages with specific registered dragon summons. Sources: [Battle stage loader and Melbu indices](../../src/main/java/legend/game/combat/Battle.java), [Ambiance](../../src/main/java/legend/game/combat/environment/Ambiance.java), [registered spells](../../src/main/java/legend/lodmod/LodSpells.java), [DragonSpell](../../src/main/java/legend/lodmod/spells/DragonSpell.java).

## DEFF effects are a separate format scope

`Battle.loadDeff` loads the package's `/0` directory as DEFF parts and `/1` as its script. The engine routes these groups:

| Group | Package directory | Verified meaning |
| --- | --- | --- |
| Dragoon/spell | `4140 + index * 2` | Numeric DEFF index; named spell registrations where present |
| Item | `4308 + (legacyItemId - 192) * 2` | Item ID; named `LodDeffs` registrations where present |
| Ordinary enemy | `4434 + index * 2` | Enemy DEFF index; actor ownership is not asserted from the index alone |
| Boss | `4946 + (archiveSlot - 1) * 2` | Archive slot selected by `enemyDeffFileIndices`; encoded caller/owner may need script analysis |
| Cutscene | `5512 + index * 2` | Numeric cutscene DEFF index |
| Battle HUD | `4114/2` | HUD DEFF parts |

Sources: [Battle DEFF loaders](../../src/main/java/legend/game/combat/Battle.java), [RetailSpell](../../src/main/java/legend/lodmod/spells/RetailSpell.java), [LodDeffs](../../src/main/java/legend/lodmod/LodDeffs.java), [RetailDeffPackage](../../src/main/java/legend/lodmod/RetailDeffPackage.java). The source mapper attaches these group contexts to existing part references, but does not treat animation/script/sprite references as TMDs. `Unpacker.undeff` marks extracted DEFF parts, and `DeffPart` has its own typed wrappers: [Unpacker](../../src/main/java/legend/game/unpacker/Unpacker.java), [DeffPart](../../src/main/java/legend/game/combat/deff/DeffPart.java).

The existing header-selected TMD inventory has **zero supported references inside these DEFF part banks**. It is not an exhaustive effect-model inventory. A separate DEFF wrapper/inner-model format audit is needed before effects are represented as complete geometry authoring jobs. Keep that task distinct from remastering the 1,324 already identified containers.

## Measured inventory coverage

The mapper was run against the local extraction and intersected with `remaining-inventory.json`; only hashes/relative reference metadata were used. For the **1,324 additional unique supported containers**, excluding the 19 core party battle containers:

| Context | Supported source references | Unique geometry containers with this context |
| --- | ---: | ---: |
| Ordinary enemy bank | 155 | 128 |
| Story/special combatant bank (including proven boss-action roles) | 121 | 101 |
| … proven boss-action subset | 14 | 13 |
| … other story/special context | 107 | 88 |
| Battle stage | 88 | 79 |

The union is **305** unique remaining geometry containers and **364** references. Context counts overlap when the same canonical geometry is reused across banks, so `128 + 101 + 79` must not be presented as a disjoint total. The named remaining combatant contexts contain **211 distinct non-placeholder display labels**, including explicitly named multipart pieces; that count is not 211 complete characters.

Supported remaining combatants with blank, `Missing`, or generic `Monster` labels retain numeric identity and unresolved names: IDs `140, 156, 159, 160, 161, 179, 191, 276, 330, 331, 355, 356, 367, 372, 386, 394, 397, 398`.

Including the core party aliases, the same battle mapper intersects **307** unique supported containers with **366** references: enemy `155`, story `123`, stage `88`. One additional context can overlap another. Across existing extraction files beyond the supported inventory, it maps 5,715 battle asset references; this larger number includes DEFF scripts/animations/sprites and unsupported or empty references and must not be called a model count.

## Reuse and implementation

[`scripts/modelshd-battle-map.py`](../../scripts/modelshd-battle-map.py) exposes `build_source_map(files: Path, repo: Path)`, returning extraction-relative source path → context records. The combined catalogue should preserve all contexts on each canonical container, use exact whole-geometry and component fingerprints for confirmed reuse, and keep name/family suggestions separate. It should retain unknown actor names rather than promote context labels to actor identities.

The module makes no engine changes, does not open game windows, and never reads model bytes. Its standalone JSON output is intended for local diagnosis; the parent catalogue publishes only its whitelisted metadata. Gameplay, animation suitability, texture compatibility and Steam Deck acceptance require later per-family validation.

[`scripts/modelshd/BattleRoleScan.java`](../../scripts/modelshd/BattleRoleScan.java) is the independent headless role auditor. It reads original scripts only locally and exports numeric IDs/call offsets, script hashes and tool/metadata hashes. It never exports original scripts or disassembled source. With the project's staged dependency JARs and Java 25, reproduce it from the repository root:

```sh
mkdir -p /tmp/definitive-battle-role-scan
"$JAVA_HOME/bin/javac" -d /tmp/definitive-battle-role-scan -cp 'build/definitive/package/libs/*' scripts/modelshd/BattleRoleScan.java
"$JAVA_HOME/bin/java" -cp '/tmp/definitive-battle-role-scan:build/definitive/package/libs/*' BattleRoleScan "$PWD" "$EXTRACTED_FILES" docs/definitive/model-catalog/combatant-role-evidence.json
```

`EXTRACTED_FILES` points at the owner's prepared local game data. The Python mapper verifies the audit's Java source, engine source, checked-in metadata and local script hashes before applying positive roles. Missing audit evidence leaves the conservative story labels; mismatched evidence fails closed. The catalogue needs no additional Java runtime or library beyond the existing authoring/build toolchain to consume valid evidence.

The combined routing pass additionally found 30 supported containers whose only references are flat `SECT/DRGN0.BIN/<index>` resources in the early archive bank. No current primary source loader/table was proved to identify their actors in this research. They must retain unresolved caller/actor labels; neither "unused" nor "legacy character" is proved. Whole-shape or component matches may offer authoring candidates, but must remain explicitly distinct from source-verified identities.
