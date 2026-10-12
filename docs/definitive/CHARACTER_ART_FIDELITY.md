# Character art fidelity

On October 10, 2026, the owner clarified that the reviewed studies are close but do not exactly honor the original game art. Fidelity is a blocking visual requirement for new character work. A polished image, successful model load or working animation does not satisfy it.

## Controlling references

Use the original published character illustrations to establish the character design. Use the original game models and textures to verify in-game details, material placement, asymmetry, weapon construction and field/battle differences. Record each reference's identity and provenance before authoring. Where those original sources differ or a detail is unreadable, record the ambiguity and resolve it explicitly; do not let a generated image fill the gap silently.

Generated studies are experiments, not canonical references. Previous approval of an image's beauty or finish does not establish every costume detail as faithful to the original. Preserve existing accepted runtime selections while correcting development candidates; do not silently replace the currently accepted Dart head or promote new work to main/release.

## What must match

- Face shape, expression, hair silhouette, hairline, headbands and ornaments.
- Costume silhouette and each garment or armor piece: outline, count, placement, layering and left/right asymmetry.
- Exposed skin boundaries, straps, belts, closures, cuffs and accessories.
- Original color relationships and the exact identifiable decorative motifs, including their placement, orientation and spacing.
- Weapon silhouette, proportions, ornamentation and the grip used by the native animation.
- Character proportions established by the controlling art, fitted to the native skeleton without treating generative proportions as authoritative.

Higher density, smooth curves, cleaner edges, readable faces and richer material response remain goals. Add surface detail only where it supports an established material without changing the design. Do not invent armor plates, contemporary clothing construction, replacement ornament patterns or generic fantasy accessories.

## Findings from the current studies

Haschel's `body-source-v1.prompt.txt` explicitly specifies “dark reddish-brown trousers,” whereas the original in-game control in the Haschel material study has purple trousers. That authored description contradicts the supplied control and must not guide a new body. The nearest-neighbor study remains a useful original-pixel control; it is not a detailed reconstruction of the published character art.

Meru's illustration and stronger-texture studies simplify or redraw the blue/gold ornament shapes and their line spacing on the hanging costume panels, leg wraps and hammer. The reviewed original/candidate views retain recognizable color blocks, but resemblance is insufficient. Reconstruct the actual motif layout from the original references rather than allowing neural inference to substitute a similar pattern.

These observations concern inspected previews and authoring instructions. They are not a completed audit of every party character or a claim that every generated asset has the same defect. All nine new character candidates still need an individual reference check.

## Next authoring gate

Start with one character and build a reference sheet containing the original illustration, original in-game front/side/rear views and an annotated detail list. Preserve original reference pixels and full original controls outside Git; publish custom work and reference metadata under the project asset policy.

Before another full-body inference or material pass, compare the new design against that sheet. Mark each listed feature matched, mismatched or unresolved. Use aligned close views for motifs and attachments, not just a flattering full-body pose. Lock the design first, then solve topology, joint continuity and native animation. Review the finished result again against the originals at gameplay size and close range before owner approval and main/release promotion.

The first acceptance question is: **Is this the original character design, rendered with more clarity?** Beauty and technical validation follow that requirement.

## Source audit checkpoint

See [Dart original-art audit](DART_ORIGINAL_ART_AUDIT.md) and [reference catalog](../../integrations/charhd/production/party-reconstruction/original-art-reference-catalog.json) for the first collected archive references, exact hashes, observed Dart design differences and remaining source gaps. All nine characters now have a located normal-form reference and a new overview comparison. The [October 11 pass](PARTY_ORIGINAL_DESIGN_V3.md) records remaining motif, provenance and native-fit checks; no complete nine-character fidelity acceptance is claimed.
