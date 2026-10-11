# EnvHD integration status

This thread owns EnvHD only. The owner requested publication to main and the normal
installation on October 10, 2026. Custom code and selected development artwork are
public and versioned. CharHD, UIHD, FxHD and ModelsHD remain separately owned.

EnvHD is incomplete. The controlling worklist is
`integrations/envhd/production/full-coverage.json`; generation, runtime selection,
native gameplay acceptance and final visual quality remain separate states.

## Selected for the default installation

- 70 battle panorama masters cover 73 source/header variants. One uniform-black
  source remains native; immutable custom history contains 123 revisions.
- 38 world location landscapes and one parchment backdrop retain their original
  placement, fades, brightness and blending.
- Eight static continent atlases select 292 reviewed masters for 364 material
  bindings. One uniform source and one wrapped Tiberoa binding remain native.
- New static battle atlases select 506 reviewed masters for 513 bindings across
  72 stage sources. Together with the original stage 0/6 pilot, 537 of 900 material
  bindings have selections. Twelve revision holds, 350 animation/context bindings
  and one incomplete-palette binding retain native sampling.
- 2,723 reviewed field images are selected independently: 603 background-bound
  and 2,120 foreground-bound masters. They reach 605 source configurations with at
  least one selected image, which does **not** mean complete scene coverage.
  Empty/missing slots, held and unreviewed images keep their native content.

Field foregrounds keep their script slot, placement, movement, visibility,
translucency and depth. No backdrop is copied into foreground masks. Each optional
image is matched to decoded native pixels and a pinned output checksum before
allocation. Failure falls back to the original layer.

EnvHD is one independently switchable mod (`envhd`) in the game's internal Mods
menu. Normal packages include its JAR plus four resource-only
`Env1HD-v0.1.0.zip` through `Env4HD-v0.1.0.zip` files. These parts introduce no mod IDs or installer
choices. They keep each downloadable asset below the release size limit. The
checksum-addressed file delivery path supports fresh installation, updates,
repair and complete reinstall without requiring an oversized monolithic download.

## Skurfa protection

Skurfa's code, artwork and notices remain unchanged. The ownership ledger protects
38 packs, 363 unique source images and 87 source-identical cut/period mappings;
its existing adapter has 43 bindings. A conservative 44-cut runtime exclusion
keeps EnvHD from superseding its scenes regardless of listener order. None of the
605 selected EnvHD configurations intersects that cut set. Protected aliases are
reuse work, not permission to redraw or rebrand upstream art.

## Validation and remaining work

Independent Java/native CPU decoding agrees with all 650 field configurations,
5,528 unique visible images and 5,560 visible bindings. All 2,723 selected PNGs
pass source dimensions, visibility, visible-black and output-checksum checks.
All 72 new battle atlases pass source upload/CLUT/STP/black/padding validation and
UV checks against 36,584 original textured faces. The battle decoder reproduces
image-then-palette upload order, including overlapping rectangles.

Synthetic regressions cover withdrawn selection removal, atomic field rollback,
empty/clipped foreground slots, mixed native/HD ownership, split bundle loading,
fresh file delivery, save-preserving reinstall and failed publication checks.
Packaging and exact-source hosted release checks remain the delivery gate; these
headless checks are not native gameplay or Steam Deck visual acceptance.

All 5,162 unowned nonuniform field images were generated and repaired privately.
Individual reviews cover 2,840 images; 117 need bespoke repair and 2,322 foregrounds
still require review. Only the 2,723 reviewed development outputs are selected and
published as field candidates. Immutable generation/repair/review receipts are
historical checkpoints and retain their original zero-selection counters; current
selection is controlled by `field-runtime-artwork.json` and
`battle-material-runtime-artwork.json`.

Field props/overlays still need consumer ownership classification. Environment
animation needs complete frame/palette families. Wrapped terrain, held motifs,
source reachability, script states, native rendering and final Skurfa-equivalent
quality remain open. All native/final-quality acceptance counts remain zero.
