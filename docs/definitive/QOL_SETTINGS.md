# Integrated QoL and reward settings

Implementation scope approved October 10, 2026: integrate menu/inventory usability,
XP/gold controls and optional Addition training feedback into the engine's existing
settings. These features require no separate mod. This document is the implementation
and review contract; it does not claim the downloadable installer has been republished.

## Player controls

Under **User Interface**:

- **Contextual Action Hints**: global, on by default. Footers use actual keyboard/gamepad
  bindings, refresh after remapping or input-family changes, and can be hidden without
  disabling shortcuts. Use Items, Additions and shops gain contextual Select/Use/Back
  hints; existing equipment, inventory and settings footers share the visibility setting.
- **Equipment Sorting**: campaign setting, Slot/Name/Power. Sort cycles the choice in
  Equipment and the inventory list. Equipment's Filter action uses the existing Help
  binding (default Start/H), cycling All/Weapons/Helmets/Armour/Boots/Accessories.
  The current filter and sort are displayed above the list. Filtering is temporary;
  it never removes inventory entries. Power compares attack for weapons, defence for
  helmets/armour, and speed for boots/accessories; it is not an automatic best-equipment rule.
- **Shop Quantity Selection**: campaign setting, on by default. Up/down changes one,
  left/right changes five; Confirm accepts and Back cancels. Mouse controls remain available.
  Buy/sell 1–99 matching units, bounded by affordability and existing bag/stack rules.
  Multiple equipment purchases enter the bag; one retains character selection and the
  ordinary equip prompt. Equipped gear and protected items are not bulk-sale candidates.
  Data-bearing or differently worn item stacks are not combined. Totals are visible;
  a changed sale price requires another confirmation. Cancel transfers nothing.

Under **Gameplay**:

- **Enemy XP Multiplier** and **Enemy Gold Multiplier**: independent campaign values,
  0–10x in 0.25x steps, default 1x. Invalid serialized/runtime inputs fall back to 1x.
  A battle snapshots settings at initialization, including for later summoned enemies.
  Scaling follows all EnemyRewardsEvent listeners; each enemy rounds separately with
  positive halves upward. Drops, scripted rewards, prices and party/reserve/dead-character
  distribution rules are unchanged. Enemy rewards and accumulated battle totals saturate
  at 99,999,999; character XP also avoids integer wraparound on award.
- **Addition Timing Feedback**: campaign setting, off by default. Reports Early/Late/
  Wrong Button/Good/Perfect for resolved manual hits, including counters and timeout.
  Perfect denotes the centre success tick (earlier centre for even-sized windows).
  It gives no damage/mastery bonus and changes no success windows, hit results or rules.
  Automatic hits produce no feedback. Results expire after 24 script ticks and clear
  at the next Addition/battle, without being serialized. Text uses the protected UI path.

Both **Definitive** and **Faithful** explicitly select 1x XP/gold, normal timing, feedback
off, quantity selection on, and Slot sorting. Existing campaigns gain default values;
they are not silently assigned a new preset. The settings use new `lod_core` IDs and
four-byte float reward encoding; old Battle Rewards mod settings are not reinterpreted.
Changing settings/presets cannot undo earned XP, levels, mastery or gold. Preserve the
whole campaign when experimenting with progression.

## Integration and provenance

This is independently authored code using the current engine APIs, inspired by
FrancisDionne/Senerio's Quality of Life+ and DennytXVII's Battle Rewards concepts.
Upstream implementation code, controller images, collateral saves, binaries and runtime
files were not imported. Project code follows the existing AGPLv3 license.

References: [QoL+ assessment](QOL_FORK_RESEARCH.md), [Battle Rewards assessment](BATTLE_REWARDS_RESEARCH.md),
[QoL+ release](https://github.com/FrancisDionne/Severed-Chains/releases/tag/experimental),
[Battle Rewards source](https://github.com/DennytXVII/BattleRewardsMod).

Ordinary shop event hooks remain active for each unit. A denied transfer or insufficient
gold stops a batch, retaining completed transfers and charging only completed purchases.
Partial completion is reported. Custom shop extensions retain their own purchase workflow.
External mods may deliberately alter transfers/rewards; this is not proof of compatibility
with every mod. Reward stacking is intentionally final scaling after other listeners;
an installed external reward multiplier therefore compounds with these settings.

## Acceptance and evidence limits

Headless checks cover valid/corrupt configuration, campaign reload and separation,
0/0.25/1/10x rewards, per-enemy rounding, arithmetic bounds, Addition feedback outcomes
and expiry, sorting/filter semantics, quantity cancellation, affordability, denied
insertion, ordinary stack capacity and data-bearing sale grouping.

Source builds and these checks do not establish handheld readability, physical controller
navigation, counter animations, a full campaign, mod-overhaul compatibility or Steam Deck
performance. Device acceptance must include quantity confirmation/cancel, full inventories,
remapped/reconnected controls, all equipment filters, manual/automatic/counter Additions,
reward distribution and save/restart. No desktop game launch is needed for the headless suite.
