# Tiberoa scenery mapping investigation

The remaining terrain hold is one page/CLUT material in static Tiberoa scenery,
not a missing texture file. It remains native and is not counted as completed.

The ordered continent texture bank supplies every word in the literal unsigned
UV bounding rectangle and the complete referenced palette. The rectangle decodes
to 248x32 pixels; its final image words come from five uploads. Independent scalar
palette/index sampling agrees with all 7,744 pixel coordinates used between the
minimum and maximum referenced U values. Original pixels and geometry diagnostics
remain private.

Three quads in part 10 use the same U coordinates, 248 and 7, and V coordinates, 0
and 31. The source TMD loader reads unsigned coordinates. The current world-map
draw path queues this static part without a UV offset or palette override; ocean
animation and its palette override apply to part 0. The current indexed TMD shader
interpolates the literal coordinates and samples the complete uploaded bank. No
short-wrap reconstruction is present in this path. These are source-code and CPU
findings, not a rendered gameplay comparison or proof of the original author's
intended appearance.

The existing terrain census and native validator require one matching uploaded
image rectangle, so they correctly retain this material rather than accepting an
unverified interpretation. Painting the whole strip now would include unrelated
uploaded regions, while silently treating 248 as negative would change geometry
sampling. Neither is justified by this investigation alone.

The next repair must classify the three visible surfaces in their actual scenery
context, compare literal sampling with any proposed per-face seam interpretation,
and bind the chosen mapping to the model and full ordered bank. If a new mapping
contract is needed, it must be explicit and preserve the current schema 1 atlases,
the other 365 material bindings, part 0 animation, STP/discard classes and source
verification. The pinned batch decoder is also a dependency of painted battle
repair recipes and must not be changed incidentally.

This investigation changes no engine, shader, installer, terrain atlas or selected
asset. The terrain hold remains 1; native and final-quality acceptance remain 0.

## Stair source and pending artwork

Four private CPU orthographic comparisons identify the three held faces as stair
entrances beside grassy-roofed town buildings. Signed short-wrap sampling pulls
in another upload. A separate contiguous-upload hypothesis instead lifts the low
native corner U=7 to U=263, before interpolation, while keeping U=248 unchanged.
This fits the complete 16x32 image uploaded by bank file 14 at word rectangle
`[382,256,4,32]`, including its extension past the texture-page boundary. Every
image word and all 16 referenced palette colors are still owned by upload 14.
Independent scalar sampling agrees on all 512 pixels. Source model, all 49 ordered
bank files and each of the three face packets are checksum-bound in the
[pending source contract](../../integrations/envhd/production/tiberoa-cross-page-source-contract.json).

The [separate audit](../../scripts/audit-envhd-tiberoa-source.py) refuses changed
source identities, ambiguous rectangles, overwritten image words or incomplete
palette/image uploads. It leaves the pinned batch decoder unchanged. The proposed
mapping is an authoring hypothesis; original hardware intent and native gameplay
appearance have not been verified.

A [64x128 custom painting](../../integrations/envhd/production/tiberoa-stairs-pending-artwork.json)
now preserves the source's six uneven bright bands and their native row peaks
`[2,6,11,16,21,27]`. The built-in image generator supplied a flat source-referenced
stone texture. The [reproducible repair](../../scripts/repair-envhd-tiberoa-candidate.py)
resizes it to 4x and restores exact source STP, discard and visible-black classes.
All source alpha values are zero because they encode STP, not conventional
transparency; the separate preview uses conventional alpha. Neither original
pixels nor source-model comparison images are published.

The repaired painting was inspected in four source-model CPU views. Fine grain,
brightness against adjoining masonry and the corner interpretation still require
independent review. These views omit native lighting, blending, clipping,
perspective and gameplay state; they do not establish native or Steam Deck
acceptance. Fourteen focused synthetic guards and seventeen existing terrain
checks pass. The current-source repair reproduces the recorded PNG exactly.

Runtime integration must apply the lift to original source corners before
ModelsHD refinement/barycentric interpolation. Applying a threshold to already
interpolated unsigned UV values would corrupt intermediate coordinates. Preserve
existing schema 1 atlas behavior, the other 365 bindings, original geometry and
part 0 animation. Mapping review and an explicit compatible source contract must
precede selection. This authoring checkpoint adds one pending candidate within
the existing terrain hold: known static work remains 238, including 237 fields.
