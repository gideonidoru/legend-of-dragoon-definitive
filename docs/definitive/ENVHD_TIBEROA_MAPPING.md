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
