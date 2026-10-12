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

## Member-level activation

The reviewed Python/Java foundation can be selected for a source-bound release
build by dispatching `build.yml` with `releaseTag=definitive-current` and
`deliveryFormat=2`. The release workflow's selection defaults to `1`, so the
serialized format-1 publisher continues independently. Ordinary CI and local
Gradle builds default to split, checksum-pinned per-film staging; an explicit
`-PcurrentDeliveryFormat=1` retains the aggregate ZIP build/export path. The same
selection reaches every workflow Gradle invocation. Default film staging binds the 18 logical source contracts to the current
carrier's fresh uploaded API IDs, hashes, sizes and exact origin, verifies the
17 physical byte objects, and refuses a changed carrier before promoting its
owned build output. Both format-1 and format-2 carrier states are supported; a
format-2 carrier must additionally agree with every committed film descriptor.
No aggregate source ZIP is needed by a clean format-2 build or included in its publication closure.

ROOT selects the matching stager/publisher with `--delivery-format 2`; a different
CI receipt format fails closed. CI member plans are canonical, checksum-bound,
ID-free `assembly-plan-<platform>-<sha>.json` inputs in the one-day delivery
artifact. They are never published as runtime recipes. The publisher proves the
planned concatenation again, binds each source to its fresh exact same-carrier
asset ID, uploads `assembly-<platform>-<sha>.json`, verifies both platforms' complete
managed inventories and all 18 logical movie descriptors, then commits the
format-2 pointer last. Shared enhanced film hashes retain one physical object and
ID. The source movie contract and the verified managed manifest must agree before
any publication uploads.

All printable-ASCII ZIP/JAR managed paths of at least 1 MiB use bounded exact raw
entry spans; smaller archives and non-ASCII paths retain ordinary whole-file
objects. Changed spans share immutable bundles of at most 16 MiB. Both the target
whole-file SHA/size and each source span are proved against the build's complete
inventory. Existing committed format-1 whole archives seed the initial catalog;
later committed format-2 recipes seed bundle and baseline-source catalogs. Every
catalog source is checksum/size/ID bound and every candidate range is rehashed.
Publisher/CI download volume can include complete retained sources to prove the
catalog. Public upload volume contains only missing bundles and ordinary blobs,
plus small descriptors/bootstrap files. The publisher reserves its final recipe
and pointer asset slots before uploads within the total 1,000-asset budget.

Retention reopens both immutable recipes, validates their descriptor/source/
platform/carrier bindings against the publication receipt, reconstructs the
package/source closure and checks every transitive source's exact server ID,
checksum, size, uploaded state and origin. A baseline whole archive remains in
the current release for as long as any current recipe span references it.
Missing recipe/source references stop deletion. Unknown assets and newer staged
files remain protected. Active drafts or newer/active candidate builds still
require an explicit complete reference audit; this activation does not bypass
that guard or claim that ongoing candidate builds have been audited.

Local focused tests cover exact-byte mixed-source publication, retry and pointer
failure behavior, later-generation bundle reuse, corrupt/unbounded plans,
18-logical/17-physical film sharing and transitive GC. Production acceptance still
requires independently reviewed exact-source two-platform CI, a serialized ROOT
publication, anonymous public recipe/source and range verification, and managed
Update/Repair/Reinstall verification against the actual carrier before cleanup.
No game, artwork, GPU or physical Steam Deck acceptance is inferred.
