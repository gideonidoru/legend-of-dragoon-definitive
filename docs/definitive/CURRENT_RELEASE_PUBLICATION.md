# One current download

The user requested uploading only files that changed, keeping one current release,
and explicitly waived support for older clients. The public carrier is
`definitive-current`. Its Git tag is a stable locator; each manifest's exact
`sourceRevision`, `buildRun`, and monotonic `generation` identify the running build.

CI builds and hashes all files on both platforms. Its small receipts bind actual
source, run, attempt, repository, platform, every managed file and every asset.
Linux supplies a small same-run receipt so macOS avoids uploading shared assets
again. A matching uploaded API asset requires the exact checksum, size, ID and
release identity. Existing mismatched hash names stop publication. Both platform
and fixture gates remain mandatory. Explicit release artifacts contain only
missing payloads plus receipts and verification reports, retained for one day.

The publisher reruns CI staging and verification, then uploads missing immutable
files. All seven mod JARs and four environment packs remain in the normal install.
Environment packs are delivered once through their file hash names. The FMV
source ZIP is retained under `source-fmvhd-<sha>.zip`; the initial migration renames
the existing identical source asset and preserves its ID without uploading it.
No separate mod release or historical payload carrier is introduced.

Both platform package/contents descriptors, installer, entry script, and FMV
source have hash names and exact asset IDs, sizes and checksums in the canonical
manifest. The exact ASCII JSON is stored as `manifest-<sha>.json` and embedded at
the start of the release body between `DEFINITIVE_CURRENT_V1` and
`END_DEFINITIVE_CURRENT_V1`. JSON is at most 8192 bytes; the body at most 12000.
All fields and descriptor names follow the manager's strict version-one schema.

Publication replaces the body only after verifying every referenced immutable
asset and the small bootstrap aliases. An interrupted upload leaves the previous
committed runtime manifest intact; retries reuse already uploaded bytes. Alias
replacement can temporarily fail the bootstrap checksum gate, which retries and
fails closed. A saved Desktop shortcut always fetches the latest script and sums.
A publication of the exact same source/run/descriptors performs no uploads and
does not advance the generation. Only one orchestrator publishes this carrier;
local exclusive locking and fresh baseline checks reject concurrent revisions.

Repair checks the current manifest and hashes installed files; Update downloads
only changed/new files and removes obsolete managed files; Reinstall downloads
the complete selected revision. Verified activation advances the accepted
generation floor. Cancellation or corrupted downloads do not advance that floor.
Restore retains the floor, and offline preparation continues to use verified
installed files.

After anonymous public bootstrap and managed Update/Repair checks, retention
verifies the full committed closure again. It deletes only recognized obsolete
assets older than the current manifest, preserving unknown and newer staged
files, active drafts and candidate builds. Git source history and notices remain.
Never delete before the replacement is verified.

These headless controls establish publication and manager behavior. They do not
establish physical Steam Deck rendering, movie performance, or final artwork
acceptance.
