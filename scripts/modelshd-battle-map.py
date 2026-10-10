#!/usr/bin/env python3
"""Source-verified battle asset contexts; never reads or exports model bytes.

The parent catalogue intersects these contexts with its supported-TMD inventory.
DEFF context does not imply that a file is TMD geometry or a supported format.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import re


def _strings(text: str, variable: str) -> list[str]:
    match = re.search(r"\b" + re.escape(variable) + r"\s*=\s*\{(.*?)\};", text, re.S)
    if not match:
        raise ValueError(f"Missing source table {variable}")
    return [json.loads('"' + s + '"') for s in re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(1))]


def _record(kind: str, label: str, evidence: list[str], contexts: list[str],
            entity: str | None = None, confidence: str = "context-verified", **extra) -> dict:
    out = {"kind": kind, "label": label, "confidence": confidence,
           "evidence": evidence, "contexts": contexts, **extra}
    if entity:
        out["entityName"] = entity
    return out


def _boss_role_evidence(files: Path, repo: Path) -> dict[int, dict]:
    """Verify a metadata-only decoded-role audit before consuming its labels."""
    path = repo / "docs/definitive/model-catalog/combatant-role-evidence.json"
    if not path.is_file():
        return {}
    audit = json.loads(path.read_text())
    def verify(path: Path, expected: str):
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError("Battle-role evidence hash mismatch: " + path.name)
    verify(repo / "scripts/modelshd/BattleRoleScan.java", audit["toolSha256"])
    verify(repo / "src/main/java/legend/game/combat/Battle.java", audit["engineSourceSha256"])
    for relative, digest in audit["metadataSha256"].items():
        if not relative.startswith("patches/meta/") or ".." in Path(relative).parts:
            raise ValueError("Invalid battle-role metadata identifier")
        verify(repo / relative, digest)
    result = {}
    for row in audit["bossKillScripts"]:
        identity = row["monsterId"]
        if not isinstance(identity, int) or not 0 <= identity < 512 or row["script"] != f"SECT/DRGN1.BIN/{identity + 1}":
            raise ValueError("Invalid combatant role identity")
        verify(files / row["script"], row["scriptSha256"])
        if row["disassemblerWarningCount"] != 0:
            continue
        calls = row["bossKillCalls"]
        if not calls or any(c["opcode"] != "CALL" or not (c["functionIndex"] == 172 or
                            (c["functionIndex"] == 173 and c.get("legacyAction") == 3)) for c in calls):
            raise ValueError("Unproved boss-action role")
        result[identity] = row
    return result


def build_source_map(files: Path, repo: Path) -> dict[str, list[dict]]:
    """Map existing extraction-relative paths to engine-evidenced battle roles."""
    battle = "src/main/java/legend/game/combat/Battle.java"
    monsters = "src/main/java/legend/game/combat/Monsters.java"
    encounter_path = "src/main/java/legend/lodmod/LodEncounters.java"
    names = _strings((repo / monsters).read_text(), "monsterNames_80112068")
    boss_roles = _boss_role_evidence(files, repo)
    encounters: dict[int, list[str]] = defaultdict(list)
    arena: set[int] = set()
    for line in (repo / encounter_path).read_text().splitlines():
        reg = re.search(r'ENCOUNTER_REGISTRAR\.register\("([^"]+)"', line)
        if reg:
            for raw in re.findall(r"new Encounter\.Monster\((\d+),", line):
                identity = int(raw) & 0x1ff
                if reg.group(1) not in encounters[identity]:
                    encounters[identity].append(reg.group(1))
                if "new ArenaEncounter(" in line:
                    arena.add(identity)
    melbu_path = "src/main/java/legend/game/combat/encounters/MelbuEncounter.java"
    for raw in re.findall(r"new Encounter\.Monster\((\d+),", (repo / melbu_path).read_text()):
        encounters[int(raw) & 0x1ff].append("melbu_frahma")

    result: dict[str, list[dict]] = defaultdict(list)

    def add(path: Path, record: dict):
        if path.is_file():
            result[path.relative_to(files).as_posix()].append(record)

    drgn = files / "SECT/DRGN0.BIN"
    for identity, source_name in enumerate(names):
        path = drgn / str(3137 + identity) / "32"
        if not path.is_file():
            continue
        name = source_name.strip()
        is_named = bool(name) and name not in ("Missing", "Monster")
        # This boundary describes the engine's named special combatant bank;
        # it is not a claim that every combatant here is a boss.
        kind = "enemy" if identity < 253 else "story-combatant"
        if identity in boss_roles and identity not in arena:
            kind = "boss"
        contexts = ["monster:%d" % identity, f"script:SECT/DRGN1.BIN/{identity + 1}", *["encounter:" + x for x in sorted(set(encounters[identity]))]]
        evidence = [battle + "#loadCombatantTmdAndAnims", battle + "#loadCombatantModelAndAnimation", monsters + "#monsterNames_80112068"]
        if encounters[identity]:
            evidence.append(encounter_path + "#ENCOUNTER_REGISTRAR")
        if "melbu_frahma" in encounters[identity]:
            evidence.append(melbu_path + "#MelbuEncounter")
        if identity in boss_roles:
            contexts.append("decoded-script:boss-kill-action")
            evidence += ["docs/definitive/model-catalog/combatant-role-evidence.json#monster:" + str(identity),
                         battle + "#scriptSetPostBattleActionBossKill", battle + "#loadEnemyDropsAndScript"]
        extra = {"monsterId": identity, "nameTableValue": source_name,
                 "encounterRole": "arena" if identity in arena else ("named-special-combatant-bank" if identity >= 253 else "ordinary-enemy-bank")}
        if identity in boss_roles:
            extra["bossKillScriptContext"] = True
            extra["bossClassificationBasis"] = "Decoded documented boss-kill action; arena classification takes precedence"
        # Textual actor-family suggestions are not anatomical equivalence.
        if is_named:
            family = re.sub(r"\s*\((?:head|body|arm)\)$", "", name)
            extra["semanticFamilyCandidate"] = family
            extra["semanticFamilyBasis"] = "exact actor label; multipart suffix removed only when explicit"
        add(path, _record(kind, name if is_named else f"Unnamed combatant {identity}", evidence, contexts,
                          name if is_named else None, "source-verified" if is_named else "context-verified", **extra))

    # Existing /0/0 files in the stage bank are the CContainer passed to
    # loadStageTmdAndAnim. There is no general stage-name table here.
    ambiance_source = "src/main/java/legend/game/combat/environment/Ambiance.java"
    stage_count = (repo / ambiance_source).read_text().count("new StageAmbiance4c(")
    if stage_count == 0:
        raise ValueError("Missing battle stage ambiance table")
    for path in drgn.glob("*/0/0"):
        directory = path.parts[-3]
        if directory.isdigit() and 2497 <= int(directory) < 2497 + stage_count:
            stage = int(directory) - 2497
            labels = {93: "Melbu 2-1", 94: "Melbu 3-1", 95: "Melbu 3-2", 25: "Melbu 3", 52: "Melbu 4"}
            label = labels.get(stage, f"Battle stage {stage}")
            evidence = [battle + "#loadStage", battle + "#loadStageTmdAndAnim", ambiance_source + "#stageAmbiance_801134fc"]
            if stage in labels:
                evidence.append(battle + "#melbuStageIndices_800fb064")
            add(path, _record("battle-stage", label, evidence, [f"battle-stage:{stage}"], battleStageId=stage))

    # Parse registered spells: RetailSpell ends with its DEFF index;
    # DragonSpell additionally ends with the explicitly selected stage ID.
    spell_source = "src/main/java/legend/lodmod/LodSpells.java"
    spell_contexts: dict[int, list[str]] = defaultdict(list)
    for line in (repo / spell_source).read_text().splitlines():
        match = re.search(r'REGISTRAR\.register\("([^"]+)".*new (RetailSpell|DragonSpell)\((.*)\)\);', line)
        if not match:
            continue
        # Constructor argument lists here have no nested calls; registrations
        # that change that convention fail to match rather than inventing IDs.
        args = [x.strip() for x in match.group(3).split(",")]
        index = int(args[-2] if match.group(2) == "DragonSpell" else args[-1], 0)
        spell_contexts[index].append(match.group(1))
        if match.group(2) == "DragonSpell":
            stage = int(args[-1], 0)
            stage_path = drgn / str(2497 + stage) / "0/0"
            add(stage_path, _record("battle-stage", "Dragon summon stage: " + match.group(1),
                                   [spell_source, "src/main/java/legend/lodmod/spells/DragonSpell.java#getBattleStage", battle + "#loadStage"],
                                   ["spell:" + match.group(1), f"battle-stage:{stage}"], spellRegistryId=match.group(1), battleStageId=stage))

    item_source = "src/main/java/legend/lodmod/LodDeffs.java"
    item_contexts: dict[int, list[str]] = defaultdict(list)
    for name, n in re.findall(r'REGISTRAR\.register\("([^"]+)".*?new RetailDeffPackage\((\d+)\)', (repo / item_source).read_text()):
        item_contexts[int(n) + 1].append(name)

    # A DEFF package's /0 contains its parts. These contexts intentionally do
    # not call the parts characters, or claim the current TMD scanner covers
    # DEFF wrappers, sprites, animations, or scripts.
    for directory in drgn.iterdir():
        if not directory.name.isdigit() or not directory.is_dir():
            continue
        n = int(directory.name)
        kind = None
        label = None
        contexts: list[str] = []
        evidence: list[str] = []
        extra = {}
        if n == 4114:
            kind, label = "effect-unresolved", "Battle HUD DEFF"
            contexts = ["battle-hud"]
            evidence = [battle + "#loadBattleHudDeff"]
            parts = directory / "2"
        else:
            parts = directory / "0"
            if 4140 <= n <= 4306 and n % 2 == 0:
                index = (n - 4140) // 2
                kind = "dragoon-effect"
                label = " / ".join(spell_contexts[index]) or f"Dragoon DEFF {index}"
                contexts = [f"dragoon-deff:{index}", *["spell:" + s for s in spell_contexts[index]]]
                evidence = [battle + "#loadDragoonDeff", "src/main/java/legend/lodmod/spells/RetailSpell.java#loadDeff"]
                if spell_contexts[index]:
                    evidence.append(spell_source)
                extra["deffIndex"] = index
            elif 4308 <= n <= 4432 and n % 2 == 0:
                item_id = 192 + (n - 4308) // 2
                kind = "item-effect"
                label = " / ".join(item_contexts[n]) or f"Item DEFF {item_id}"
                contexts = [f"item-deff:{item_id}", *["deff-registry:" + s for s in item_contexts[n]]]
                evidence = [battle + "#loadSpellItemDeff"]
                if item_contexts[n]:
                    evidence += [item_source, "src/main/java/legend/lodmod/RetailDeffPackage.java#load"]
                extra["legacyItemId"] = item_id
            elif 4434 <= n <= 4944 and n % 2 == 0:
                index = (n - 4434) // 2
                kind, label = "enemy-effect", f"Enemy DEFF {index}"
                contexts = [f"enemy-deff:{index}"]
                evidence = [battle + "#loadEnemyOrBossDeff"]
                extra["deffIndex"] = index
            elif 4946 <= n <= 5510 and n % 2 == 0:
                index = (n - 4946) // 2 + 1
                kind, label = "effect-unresolved", f"Boss DEFF archive slot {index} (actor unresolved)"
                contexts = [f"boss-deff-archive-slot:{index}"]
                evidence = [battle + "#loadEnemyOrBossDeff", battle + "#enemyDeffFileIndices_800faec4"]
                extra["archiveSlot"] = index
            elif 5512 <= n <= 5712 and n % 2 == 0:
                index = (n - 5512) // 2
                kind, label = "cutscene-effect", f"Cutscene DEFF {index}"
                contexts = [f"cutscene-deff:{index}"]
                evidence = [battle + "#loadCutsceneDeff"]
                extra["deffIndex"] = index
        if kind and parts.is_dir():
            for path in parts.rglob("*"):
                if path.is_file() and path.name not in ("mrg",):
                    add(path, _record(kind, label, evidence, contexts, **extra))
    return dict(result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--files", type=Path, required=True)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    result = build_source_map(args.files, args.repo)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, indent=2) + "\n")
    print(f"Mapped {len(result)} extraction-relative battle asset references; no model bytes exported.")


if __name__ == "__main__":
    main()
