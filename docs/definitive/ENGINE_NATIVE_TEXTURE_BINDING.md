# Native texture selection for mixed HD draws

Each queued draw must select its native image and CLUT owner independently of
preceding draws. When texture unit 1 is not explicitly overridden, bind the global
native VRAM texture even if the draw supplies an HD albedo or another texture unit.
Explicit unit-1 overrides remain authoritative. This applies to Standard, TMD and
Battle TMD draws, including UI and pooled queue reuse.

## Actual scenery reproduction

Unchanged main `11ad865002bc78639aeca594c850ebb2e257761c` was replayed privately
using the actual Battle stage loader and render queue, original `DRGN0/2498`
geometry/TIM, its shipping static HD atlas, and the shipping shaders in a windowless
Mac CGL context. Native-to-HD-to-native coverage matched at five orbit angles.
Changing only the preceding native texture binding to an empty private VRAM texture
made the HD draw lose 1,007, 1,646, 1,707, 1,085 and 761 visible pixels respectively.
The native return restored coverage. All draw and cleanup GL checks remained clear.

The selected atlas retains some native faces. Previously, supplying any explicit
texture disabled the native fallback binding; an HD albedo on unit 0 consequently
left unit 1 owned by a preceding draw. Native faces could then sample unrelated
image and palette data. The correction selects global unit 1 whenever that unit
has no explicit override, while preserving all explicit bindings.

The same five-angle original-data replay passes with zero coverage loss after the
correction. Assets, UV mapping, camera calculations and palette data are unchanged.
The private reproduction, original files and frame dumps remain outside Git.

## Regression contract

Recording textures exercise the actual acquire and texture-selection methods of
all three queued model types. The 27 cases cover default native selection, albedo
only, another unit only, explicit private/global native owners, combined albedo and
private owner, reuse after a private draw, and UI albedo. On unchanged production,
12 cases failed their owner assertion and 15 controls passed. After correction,
all 27 pass, alongside the three HD texture contracts. These recording cases
supplement the actual GPU scenery reproduction; they do not substitute for it.

Full integration and independent review are bound to the increment's exact source.
This establishes the reproduced draw-order defect and its correction. It does not
establish every Steam Deck attack/camera sequence, full-scene flicker or field-head
consistency, battle-entry performance, or campaign/device acceptance. Those remain
open requirements. Source game data, saves and private diagnostics are not shipped.
