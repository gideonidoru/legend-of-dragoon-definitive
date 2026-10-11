# EnvHD battle animation family discovery

The [family ledger](../../integrations/envhd/production/battle-stage-family-ledger.json)
explains the complete **isolated stage-local source cycle for all 350 held
animation/context bindings** across 67 stages. It clears no hold and selects no
artwork. The separate stage 67 incomplete-palette binding remains outside this
replay. Field selection stays at 4,917 images/31 paintings; the public installer
stays at 4,892 field images.

The useful ROI finding is narrower than a static-material count. All 197
padding-only bindings keep their exact sampled source pixels constant, but **190
have changing decoded padding** during their cycle. Only seven keep both sampled
pixels and the complete decoded context constant. They are candidates for further
static-reuse analysis, not approved replacements:

| Stage | Page | CLUT |
| --- | --- | --- |
| 3 | 7 | 15580 |
| 4 | 7 | 15708 |
| 9 | 7 | 15516 |
| 9 | 7 | 15708 |
| 29 | 7 | 15516 |
| 82 | 7 | 15516 |
| 86 | 7 | 15964 |

## What the source replay establishes

[Discovery](../../scripts/discover-envhd-battle-families.py) rereads each original
container and TIM, checks identities against the previously reviewed native
worklist, enumerates actual textured face consumers and preserves the existing
eight-texel padding. The stage upload order is image then CLUT. Every binding's
sampled image, conservative padding and **entire** 16/256-word referenced palette
is initialized by those uploads; no additional unknown source region was found
among these 350 bindings. Every active animation rectangle is initialized too.
This does not fill or explain the other binding's 192 missing palette words.

There are 145 active stage-slot references and 96 distinct raw sequence hashes.
The replay preserves initial counters, low-nibble delays, instruction durations,
directions, row-copy amounts, zero-row/no-op copies, the first `-1` sentinel and
reset to instruction one. State identity includes counters, instruction index
and source row provenance, not just equal-looking pixels. All these sequences
repeat within the bounded discovery limit. Three slots have a transient before
the loop: stage 26 slot 1 (5 invocations), stage 35 slot 0 (73), and stage 86
slot 0 (41). The ledger records those separately from the repeating cycle.

The exact native ordering matters. `Battle.renderBattleStage` enqueues slots in
ascending order. `Gpu.tick` traverses z queues descending and each queue backwards,
so these slots execute descending and each three-copy sequence executes in reverse
enqueue order. Both pieces of the source image fill `[960,256]` scratch before
the final scratch-to-image copy. Replaying enqueue order would read inherited
scratch incorrectly. The normal `Graphics.endFrame` path resets mask writing at
z=3 before these z=1 copies. The source copier walks forward by row then column;
overlapping copies must not be replaced with a snapshot/memmove assumption.

All 145 source image rectangles are disjoint within their own stage and disjoint
from every slot's scratch rectangle. No sampled/padded/palette consumer in this
worklist reads scratch. Each copying slot initializes its entire scratch rectangle
before its copy back; later slots can reuse the scratch safely for this isolated
stage replay. These checks justify factoring each consumer cycle into its actual
dependent slots. Consumer/controller periods use the least common multiple after
any initial transient; the complete initial and looping schedules are preserved.

Decoded frames preserve indexed page/CLUT coordinates, exact texel crop edges,
native `/31` RGB conversion, STP and zero/visible-black distinctions. Each frame
has sampled/context RGBA hashes, a conservative padded-word hash and a full
palette-word hash. Face indices, the full face-contract hash, original UV bounds,
per-face blend-mode summary, sequence identities, read coverage, timing, transient
and loop contracts stay attached to each source material.

The resulting **341 isolated consumer families and 104,532 distinct combined
context-image hashes are diagnostic counts, not artwork jobs or generation
requests**. Multiple independently scrolling patches can create many combinations
inside one material's bounding rectangle. Repeated source patches, static content
and native scrolling need to be factored before choosing an authoring unit. A
matching sequence hash alone cannot establish identical source/palette artwork.

## Verification and public evidence

[CPU differential verification](../../scripts/verify-envhd-battle-native-replay.py)
compiles the current source-extracted Java initializer, stage animation method,
render-loop ordering, GPU queue/flush/copy methods and copy-command class inside
a minimal CPU shell. It compares counter states, execution-order commands, native
word values and source-word provenance to the Python replay after every invocation.
All **145 actual sequence cases / 33,508 invocations match**, with zero unknown
source reads. The Python ordered-copy replay also checks the optimized row-rotation
model on every invocation. This runs headlessly without initializing the game,
GPU renderer, desktop window or complete encounter scripts.

Fourteen behavioral tests cover duplicate/missing consumer accounting without
reading originals, initial waits/directions, transients,
zero duration, zero-row copies, malformed/budgeted sequences, STP/black/partial-word
sampling, dynamic padding with constant visible samples, missing unused palette
words, missing padding, changed dependencies, same sequence/different source,
forward overlapping copies, unknown reads, checkpoint preservation, metadata
round trips and multiple slots sharing scratch. The source-method differential is
also wired into the existing hosted synthetic-fixture gate; a local pass does not
claim a hosted pass.

The readable ledger points to 67 deterministic compressed JSON metadata shards
under `integrations/envhd/production/battle-stage-family-details/`. Together they
preserve every frame hash and complete run-length timing schedule. The primary
ledger records compressed and expanded sizes/hashes plus each binding's frame-table
and schedule hashes. Decode them with Python `gzip` and `json`, then verify those
hashes before relying on the contents. The metadata contains **no original image
pixels, VRAM words, sequence payloads or original script bytes**. Private original
inputs used by the differential exist only in its temporary private directory.
The public source controls can reproduce this ledger from a lawful local extraction.

## Contracts that remain open

An isolated source cycle is not the full reachable encounter state. Before any
hold clears or generation/runtime adapter work starts, independently reconcile:

- Encounter/stage scripts, `scriptCopyVram`, other ordered TIM/effect uploads and
  queue interleaving against these consumers. Dynamic script parameters and
  overlapping/inherited uploads cannot be filled from a convenient prior snapshot.
- Counterattack/transformation darkening. `SEffe.applyScreenDarkening` writes a
  parameterized 64x16-word region at `[448,240]` from the stage backup;
  `ScreenDarkeningEffect` advances to script-provided targets and restores its
  target on destruction. The current artwork adapter falls back while darkened.
  Exact parameter reachability and transitions have not been enumerated here.
- Renderer presentation and lifetime. `gameLoop` uploads VRAM in `startFrame`,
  queues stage copies during simulation, then executes them in `GPU.endFrame`.
  Ledger timing is eligible stage-render invocations, not wall-clock FPS or proof
  of the renderer's presented animation phase. Stage changes disable old slots
  and defer loading; preserve that behavior and independent native fallback.
- Independent source/intent review, seams, wrapping, filtering, translucency,
  camera changes, gameplay and physical Steam Deck acceptance.

Until those contracts close, all 350 animation/context bindings remain native.
The 246 known static repairs, the separate incomplete-palette binding, world
animation and the independently assigned prop/overlay ownership census remain
outstanding. No generation, runtime adapter, selected resource, package or release
was changed by this discovery checkpoint.
