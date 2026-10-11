# Next FMV source manifest and delivery plan

This work starts from `cb6168ba043991ad0b313111af02c4cba0f57f2d` in the isolated `codex/fmv-current-manifest` branch. The current manager/publication checkpoint remains frozen. The publication owner coordinates schema approval, combined review, exact-source hosted checks and release promotion.

## Agreed ownership and managed layout

The FMV implementation owner controls the small `bundled-mods/FMVHD-v0.1.0.jar`, strict `gfx/fmvhd/manifest.properties`, `gfx/fmvhd/videos/<sha256>.mp4`, provenance and NOTICE, the installed-JAR migration helper, package closure helper, playback lookup and Gradle inputs. Existing gfx workspace linking and all seven mod IDs/switches are preserved. Movie byte hashes, native source identity, first-owner replacement behavior and playback/clock/decoder behavior remain explicit controls.

This branch controls `CurrentRelease` schema validation/metadata compaction, the source-map fields carried in `ReleaseUpdates.CurrentRevision`, current-manifest behavior tests and this contract. Root expanded this branch’s ownership to generic FileDelivery archive assembly, its package-validation call seams, and progress labels. The FMV owner froze the narrow import prerequisite at 43167158d and bb4dee03c; this branch adopted those exact prerequisites as 4aba07436 and 44b44f127. Further FMV runtime/packaging changes remain with that owner; publication scripts remain with the publication owner. The publication owner controls the next publisher/source-seeding/retention integration.

## Approved format 2

Retain the current body envelope and exact canonical ASCII JSON rules. JSON `format:2` replaces the single `fmvSource` field with `fmvSources`; other top-level fields, source/run/generation/carrier binding, immutable manifest checksum asset, both platform descriptors, installer and entry are unchanged. Reject missing/extra top-level fields, incomplete film sets, invalid descriptors, duplicate JSON keys and mismatched API metadata.

`fmvSources` has exactly these 18 keys: OPENH, DEMOH, DEMO2, WAR1H, TVRH, GOAST, ROZEH, TREEH, WAR2H, BLACKH, DRAGON1, DENIN, DENIN2, DRAGON2, DEIASH, MOONH, ENDING1H, ENDING2H. Each value has `name`, decimal-string `id`, `sha256`, integer `bytes`. Its name is `file-<sha256>` under the same current carrier. Each movie is bounded to 512 MiB. Source input and managed playback bytes share one immutable physical asset and ID; there is no second source alias or aggregate source ZIP in the next publication closure.

The worst-case canonical JSON with maximum allowed IDs/generation/run and all 18 movie descriptors is 5372 bytes, leaving 2820 bytes under the existing 8192-byte bound. The complete body remains bounded to 12000 characters. This is a schema-size calculation, not validation of real films.

The candidate revision carries an immutable movie-name to `(sha256, bytes)` map. The FMV owner binds that map to the strict external manifest and every expected `gfx/fmvhd/videos/<sha>.mp4` entry before staging/import. The publication owner proves both platform closed inventories agree with all 18 source descriptors and validates every same-carrier immutable object before body promotion or pruning. Do not invent a second authoritative film list or infer native source hashes from enhanced payload hashes.

## Ordering and activation

Keep the installed/accepted generation and source/manifest-hash floor semantics unchanged. Successful activation alone advances the accepted floor. Same-carrier revisions are detected independently of tag, asset ID and GitHub release date; stale queued candidates and conflicting equal generations fail before staging and again before activation. Restore preserves the accepted floor. Discovery, failed import, corruption and cancellation must not advance it.

The current format-1 manifest remains supported in this branch's parser while coordinating the format-2 release. No historical payload release, older-client bridge or external storage account is introduced.

## Required behavioral proof

1. Complete format-2 discovery binds all 18 source object hashes, sizes, IDs, uploaded states and exact carrier URLs; compaction retains declared source objects and discards unrelated historical blobs.
2. Missing, extra, renamed, oversized or corrupt descriptors fail closed; format-1 floor behavior remains intact and format-2 revisions preserve the same ordering.
3. The initial split imports all matching movies from the hash-pinned installed legacy JAR into owned staging, with zero movie network downloads; invalid import falls back only to the verified matching object.
4. A code-only update reuses all 18 movies. A one-film change fetches only that film. Repair of one corrupt/missing film fetches one film. Full Reinstall fetches all 18 films and all application files.
5. Package/source cross-closure rejects disagreement before activation. Saves, settings, seven switches, disc images and offline launch preparation remain intact. Cancellation, bad payloads, unsafe links/paths, duplicate/truncated archive entries and resource failures cannot activate a partial candidate.
6. Real enhanced-film hashes and source/provenance controls are verified separately from synthetic tests. No physical Steam Deck/GLES, live game or campaign acceptance is inferred from headless fixtures.

Publisher/schema confirmation and the FMV owner's exact source-map/package closure seam are dependencies before this branch freezes implementation. Current release publication continues independently.

## Generic incremental archive delivery — approved shared contract

This expansion applies to all large mod ZIP/JAR files. Preserve the approved final archive bytes, ordering, size and SHA-256. Reuse raw compressed spans; do not recompress entries or infer reproducibility. The public carrier’s small pointer describes a separate per-platform, checksum-bound assembly recipe so thousands of spans stay outside the 8192-byte body. The publication owner approved the schema and bounds on October 11, 2026.

Proposed format-2 platform adds an `assembly` descriptor with `name`, decimal-string `id`, `sha256`, and integer `bytes`; name `assembly-<platform>-<sha>.json`, maximum 16 MiB. Recipe has exactly `format`, `sourceRevision`, `platform`, `releaseTag`, `sources`, and `files`. `sources` maps full asset SHA to exact decimal-string asset ID and size, with a same-carrier `file-<sha>` URL derived rather than supplied. `files` maps allowed managed paths to ordered span records with `sha256`, `bytes`, `source`, and `offset`. The recipe is bound to verified contents metadata; every assembled file must agree with its whole-file inventory digest/size. Recipe bounds are 30,000 files, 100,000 spans/range requests, 1,000 unique sources within the carrier’s total 1,000-asset budget, 16 MiB per span/coalesced request, and strictly less than 2 GiB per source. Optional owned cache receipts live at `.archive-assembly/<package-identity>.json`, bound to recipe SHA/source/platform. Missing or corrupt cache falls back; write after successful activation/cleanup. In the next combined branch CleanReinstall must classify this root cache as application-owned and remove obsolete receipts.

ZIP/JAR primary boundaries are exact raw local-entry spans including headers, compressed data and data descriptors. Central directory and end records have their own span. Oversized spans and non-archive files use a documented deterministic bounded content-defined fallback. A publisher may place changed spans into one immutable upload bundle, enabling changed-byte-only upload without one new asset per span. Recipe offsets refer to that exact bundle; unchanged spans may reference a retained baseline whole asset. Coalesce adjacent source ranges within a bounded read while independently verifying each target span.

Local reuse never trusts a filename, CRC or archive entry name. Index raw local spans, verify expected target chunk size/SHA, and copy into owned staging. Unreadable or corrupt local data falls back to the matching remote span; unsafe links are never followed. Generic chunks can use the checksum-bound owned recipe cache described above. Full Reinstall bypasses all local reuse. A missing/corrupt single span requires only that span’s download.

Remote partial requests require exact HTTP 206, exact requested start/end and source total in Content-Range, no unexpected content encoding, bounded exact body bytes and SHA verification. HTTP 200 is rejected for a partial request. Request/header/body deadlines, bounded retries, interruption, storage reservation and staged cleanup remain mandatory. No failure advances the accepted generation or replaces the active installation. Source references become transitive current-release retention closure; do not delete a baseline object that the current recipe references.

### Measured proof before production changes

Four real old/new Env archives total 2,368,751,813 updated bytes. A deterministic Gear CDC probe at 1 MiB target (256 KiB minimum, 4 MiB maximum) needs 57,260,854 changed bytes in 29 spans; at 4 MiB target, 213,786,255 changed bytes in 29 spans. Raw entry spans need only 2,418,280 changed bytes in 54 spans. They contain 9,846 total spans, with maximum span 8,316,867 bytes. Combining adjacent old/new byte reads produces 8, 14, 14 and 22 read runs across the four archives. All three experiments reconstructed streams with SHA-256 exactly equal to the four approved new whole archives. This is a local byte-reuse experiment, not an implemented installer or published changed-byte update.

Private reproducible experiment/evidence: `/Users/markmurray/Downloads/Definitive-Delivery-Review-2026-10-10/Archive-Assembly-Probe-20261011`. The publication owner independently proved an initial unauthenticated 1024-byte range from one old public Env archive returns 206 and matches local bytes; The publication owner also verified nonzero/end ranges against real public archive bytes.

Required controls add malformed/incomplete/extra recipe records, wrong source/run/platform/carrier, offset overflow, oversized/inconsistent chunks, incorrect Content-Range/200/encoding/short/extra bodies, chunk corruption, corrupt local span fallback, cancellation and disk/write failures. Behavioral fixtures must prove initial bootstrap, small archive change, one-span repair, full reinstall, exact final SHA, saves/settings/mod-switch preservation, and transitive retention. No game, GPU or physical Steam Deck quality acceptance is inferred from delivery fixtures.

Implementation checkpoint: imported final owner movie packaging/helper commits 577bb29d and 7b455624 as 2025abd2e and 6a870cfa2. Added format-2 discovery/metadata compaction, immutable source-map preflight and full package/name-to-film semantic verification seams. Range assembly and its cache/cleanup integration are still pending; this branch is not ready for publication.
