# Persistent current release: manager contract

The GitHub release `definitive-current` is a storage carrier. A logical build is identified by a positive generation, source revision, build run and SHA256 of its exact canonical current manifest. The carrier tag, package asset ID and GitHub publication date do not determine update ordering.

The release body starts with `DEFINITIVE_CURRENT_V1` followed by a newline, exact canonical ASCII JSON without a trailing newline, then a newline and `END_DEFINITIVE_CURRENT_V1` followed by a newline and human notes. JSON uses sorted keys and compact separators. The JSON is bounded to 8192 bytes, the complete body to 12000 characters. Duplicate keys, malformed framing, unknown fields and noncanonical JSON fail closed.

The closed format-1 schema has `format`, `generation`, `sourceRevision`, `buildRun`, `releaseTag`, `releaseId`, `publishedAt`, `platforms`, `installer`, `entry`, and `fmvSource`. Generation/build run and decimal-string release/asset IDs are bounded to 2^53-1; dates use UTC with seconds. Both `linux-x64` and `macos-arm64` have `package` and `contents` descriptors. Each descriptor contains exactly `name`, `id`, `sha256`, `bytes`. Names are checksum-addressed `package-<platform>-<sha>.zip`, `contents-<platform>-<sha>.zip`, `installer-<sha>.zip`, `entry-<sha>.sh`; FMV source retains `FMVHD-v0.1.0-videos.zip`.

The manager computes the exact JSON SHA256 and binds its immutable `manifest-<sha>.json` asset and every descriptor to uploaded-state API metadata, exact size, digest, ID and the same repository/carrier URL. Discovery downloads no manifest payload. Metadata compaction retains only declared current descriptors and the manifest. Installer/maintenance discovery revalidates metadata; background checks retain the existing bounded short-lived cache.

File delivery fetches the selected immutable contents inventory, verifies its checksum and exact size, and binds its source/tag and closed package identity. Each application file resolves to `file-<sha>` under the same carrier. Existing complete-inventory, size/hash verification and activation transactions remain in force.

State carries `currentGeneration`, `currentSourceRevision`, `currentManifestSha256`, `currentBuildRun`, corresponding previous-build fields for Restore, and `accepted*` fields for the highest successfully activated generation. The accepted floor advances only at verified activation. Merely viewing a build, cancellation or failed downloads do not advance it. A lower generation or equal-generation conflicting source/manifest is rejected before file downloads and again at activation. Restore preserves the accepted floor and can offer the exact latest generation again. Partial/malformed ordering state fails validation.

Repair selects the current manifest, including a newer logical build under the same carrier, and downloads only changed/missing application files. Repair of the same build preserves previous-version Restore references and disc extraction. Full Reinstall fetches every application file and retains saves/settings/custom mods/disc images, using the existing new-data-generation behavior. Verification and launch preparation do not require release discovery. A saved outdated online package still must match the current inventory or be downloaded again.

## Headless acceptance cases

| ID | Behavioral regression |
| --- | --- |
| CUR-01 | New generation is offered despite identical carrier tag, package asset ID and logical date |
| CUR-02 | Lower/conflicting generation fails before payload download; Restore retains accepted floor |
| CUR-03 | Repair under same carrier uses latest build, fetches one changed fixture file, preserves personal choices/saves |
| CUR-04 | Full Reinstall fetches every application file and preserves saves |
| CUR-05 | Contents source mismatch prevents activation |
| CUR-06 | Unknown fields, out-of-range generation, wrong carrier ID, non-uploaded asset and malformed framing fail closed |
| CUR-07 | Current discovery survives compaction of hundreds of unrelated blob assets |
| CUR-08 | Corrupt application payload cannot alter active build or advance floor |
| CUR-09 | Cancellation preserves installed build and ordering |
| CUR-10 | Same-build Repair preserves the previous logical revision for Restore |
| CUR-11 | Offline verification/launch preparation needs no release request |
| CUR-12 | Wrong API asset ID/origin/hash and missing/duplicate/unknown-version markers cannot fall back to legacy discovery |

These are synthetic manager/package fixtures. They do not establish hosted publication correctness, physical Steam Deck behavior, real gameplay or GUI acceptance. Publisher/retention/bootstrap/CI changes are a separately owned integration dependency; this manager change alone does not publish a release.
