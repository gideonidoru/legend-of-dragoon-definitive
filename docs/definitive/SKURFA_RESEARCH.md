# Skurfa's HD backgrounds — reuse assessment

Historical assessment: later source/build integration and the first QoL slice are recorded in [INTEGRATION_ALPHA.md](INTEGRATION_ALPHA.md). Skurfa is now public and source-linked by owner request.

Inspected 2026-10-09. First concrete external-artwork integration candidate; prefer evaluating the existing mod before writing a duplicate adapter. No artwork or release binaries downloaded, no mod installed, no engine launched, no creator contacted.

## Pinned source and release

Repository: [IntiArtHub/skurfas-upscaled-hdr-backgrounds](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds). Inspected main at `3c9e4b3ecefc31cb32fd1281a7857f3a08f56081` (2026-09-15). [Release v1.1.0](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds/releases/tag/v1.1.0), published 2026-09-15, offers a 303,089,424-byte ZIP and SHA256SUMS.txt. These are API inventory facts; archive contents/checksums were not independently verified.

[Java source](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds/blob/3c9e4b3ecefc31cb32fd1281a7857f3a08f56081/src/main/java/scbackgroundhd/BackgroundHdMod.java) maps 43 disk/cut scene branches onto 38 distinct asset sets, with five aliases. This is limited coverage, not a game-wide remaster. [Build configuration](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds/blob/3c9e4b3ecefc31cb32fd1281a7857f3a08f56081/build.gradle) targets Java 25, uses engine/mod-loader JARs as compile-only dependencies, and embeds runtime artwork in the mod JAR.

[Installation instructions](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds/blob/3c9e4b3ecefc31cb32fd1281a7857f3a08f56081/HOW_TO_INSTALL.txt) describe replacing the previous version's JAR in mods/, without overwriting extracted retail files. The README still contains v1.0.0 install examples; use version-specific release instructions. The project declares engine compatibility 3.0.0; its author reports in-game verification on 3.0.0-1779-devbuild. Those are author claims, not our runtime results.

## Licensing boundary

[LICENSE](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds/blob/3c9e4b3ecefc31cb32fd1281a7857f3a08f56081/LICENSE) grants MIT terms for original project code/tools, with Skurfa's copyright/notice retention. [THIRD_PARTY_NOTICES.md](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds/blob/3c9e4b3ecefc31cb32fd1281a7857f3a08f56081/THIRD_PARTY_NOTICES.md) explicitly excludes underlying game IP and derivative remastered visual resources from that grant. Do not describe the artwork as MIT-licensed or mirror it into Definitive's source repository. Keep artwork user-installed from the author's release while recording pack provenance and clarifying any proposed redistribution terms. This report records the published terms, not a legal clearance for bundling.

## Compatibility evidence

The mod uses the existing SubmapEnvironmentTextureEvent to replace background/foreground textures. It preserves engine-managed placement/occlusion, skips a scene if another handler already supplied a background, and throws when the expected foreground count/dimensions do not match. It therefore needs real scene validation on our exact engine SHA, even if loading succeeds. [Technical overview](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds/blob/3c9e4b3ecefc31cb32fd1281a7857f3a08f56081/docs/technical-overview.md).

Our headless source check compiled BackgroundHdMod.java successfully with Java 25 against the locally built engine/dependency JARs based on upstream `fba1543543865e29ee572f479003d9b47158eeb3`. Source and classes remained in temporary storage; the MIT notice was retained there. [Compilation evidence](evidence/skurfa-sourcecheck.txt). This proves referenced API signatures compile, not that the published binary's contents, loader discovery, all scene mappings, native rendering or performance work.

## Steam Deck concerns

HDR here is artistic grading of conventional PNGs, as the author explicitly states; it is not proof of an HDR output pipeline. Our Texture.png/TextureBuilder path uploads RGBA8. Valve lists [Deck OLED's HDR display](https://www.steamdeck.com/en/tech), but display support alone does not establish correct engine/SteamOS HDR output. Validate SDR readability/brightness and intended HDR appearance separately; do not require an HDR-only experience.

Foregrounds use full-background-sized textures. For cut 21, the source specifies 3200x1920 with 21 foregrounds: `(1 + 21) * 3200 * 1920 * 4 = 540,672,000 bytes`, or **515.625 MiB**, assuming base-level uncompressed RGBA8 allocation. Cut 5's equivalent estimate is 37.5 MiB. These are derived storage estimates, not measured VRAM/residency or total memory; staging, allocator/driver overhead and transition overlap may add cost. [Derived inventory](evidence/skurfa-scene-inventory.json), generated from the pinned source. This makes small-scene and high-layer-count comparisons essential before choosing Deck texture limits.

The [known-issues file](https://github.com/IntiArtHub/skurfas-upscaled-hdr-backgrounds/blob/3c9e4b3ecefc31cb32fd1281a7857f3a08f56081/docs/known-issues.md) still labels cut-9 masks, cut-694 lamp alignment and foliage occlusion as v1.0 problems deferred to v1.1. Do not assume they are resolved: their current status requires author clarification or actual scene tests.

## Recommended next slice

1. Record release artifact hashes/terms in an external-pack manifest; avoid copying artwork into Git.
2. After private game files and execution permission are available, install the author's mod in an isolated test package and exercise cut 5 plus a transition/mask case and high-layer-count cut 21.
3. Compare original-art vs pack frame times, loading, memory, occlusion and SDR appearance on Deck. Confirm disable/removal and campaign behavior.
4. Reuse the existing adapter if it passes. Only add a narrow compatibility/settings shim or Deck-size option if evidence requires it, preserving source notices and original-art fallback.
