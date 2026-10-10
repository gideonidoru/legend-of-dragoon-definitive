# FMVHD

Restored cinematic pack for Legend of Dragoon: Definitive, independently enabled through Severed Chains' existing mod manager. Bundled by default. Existing campaigns use their normal mod-selection prompt to enable newly installed mods. Original videos remain the fallback when the pack is disabled, unavailable or incompatible with the disc source hash.

Code is AGPL-3.0 under the repository LICENSE. Enhanced videos are derived from original Legend of Dragoon footage and remain distinct from the code license. The user explicitly approved public distribution of these derived FMVHD assets as a project asset-policy exception on October 10, 2026; that policy exception is not a grant of rights by the original footage's owner.

Large enhanced MP4s are published as a pinned GitHub release ZIP, not committed as Git objects. No discs, extracted IKI files, decoded AVI masters, private saves or production cache are published. RealESRGAN and jPSXdec are external offline production tools, not engine runtime dependencies. Their notice links and pinned tool hashes are recorded in the production manifest.

First pack: 18 videos, logical 5:3 proportions, 1280x768 H.264, original 15 fps, 48 kHz stereo AAC. Original FMV playback halves XA volume; the enhanced audio encodes that same 0.5 gain. The normal FMV and master volume controls apply at playback.

For reproduction, run `scripts/restore-fmvhd.py` with paths to extracted original STR files, output folder, JDK, jPSXdec v2.1, RealESRGAN ncnn executable and model directory. The executable needs headless GPU access. Use a fresh output directory when changing the recipe. Package finished records and videos with `scripts/package-fmvhd.py --production <output-folder> --output FMVHD-v0.1.0-videos.zip`. Work is resumable per validated asset and source hash and each video emits a completion record. The recipe uses Lanczos3 chroma, gentle temporal denoise and 75% framewise neural restoration blended with 25% conventional enlargement. It preserves timing and does not generate new motion. Offline format checks and rendered samples do not establish physical Deck performance or whole-campaign acceptance.
