# Definitive file delivery

The requested delivery behavior is:

- Include FxHD, EnvHD, CharHD, ModelsHD, FMVHD and UIHD alongside Skurfa in normal downloads, with matching engine hooks and visible module status.
- Show overall progress and current-file progress when transferring individual files.
- Repair the installed release by checking SHA256 and downloading only missing or different files.
- Reinstall the complete application, even when healthy.
- Update only changed/new files and exclude removed files from the active version.
- Publish a new verified Linux x64 / Steam Deck download.

## Package and runtime

The complete platform ZIP contains all seven bundled JARs and their attribution. New campaigns default to all discovered mods. Existing campaign choices are retained. EnvHD, CharHD, FxHD, UIHD and FMVHD are always made available to the in-game mod manager; the existing backgrounds/model preference controls Skurfa and ModelsHD. Matching source-bound engine events handle their resources. Including CharHD supplies its adapter; character texture production remains incomplete. ModelsHD geometry and pilot artwork remain development quality pending gameplay/visual acceptance.

## Operations

First install and full Reinstall download the complete verified platform ZIP. Archive transfer has one overall bar; unpacking identifies the current module/file. Reinstall replaces the managed release and creates fresh application workspace/extraction state. Saves, preferences, custom mods, discs and recovery copies are retained.

Repair looks up the exact installed public tag and requires the inventory's package identity to equal the installed identity. Update chooses a newer compatible published release. Both checksum reusable files, copy them into independent staging, and download only different or absent files from the same tag. SHA256 and size checks precede activation. Staging contains the complete target inventory, so removed managed files disappear from the active version while previous versions remain available for rollback. Private data is outside that inventory.

A second progress bar shows the current individual file, with accessible names and the full path available in its description. Overall progress includes reused and fetched bytes. Failed transfers or checks cannot activate a partial set. Interrupted owned downloads have bounded retries/resume; activation retains the existing operation lock and durable recovery transactions. Legacy releases without per-file inventories still support full archive install/update; their Repair action explains that full Reinstall is required.

## Release format and gate

Each platform has a small `Definitive-Contents-<platform>.zip` containing the byte-identical complete-package metadata and hashes plus file lengths. Raw payload blobs are named `file-<SHA256>` and shared between platforms when identical. The inventory download is itself bound to GitHub's asset digest. File URLs are derived from the same repository and tag; bytes are checked before use. Full ZIPs remain the normal installation download.

CI generates blobs from its verified package. The publication gate binds inventories to those packages, checks every blob's SHA256/length, compares every upload with exact successful hosted CI artifacts, and checks the complete published release asset set and actual Git tag. A CI inventory cannot be omitted from publication. No discs, saves, original extraction trees, authoring runtimes or credentials are release inputs.

## Acceptance

Behavior tests cover additions/changes/removals, unchanged reuse, selective corrupt/missing-file repair including damaged metadata, preservation of extraction/private data during Repair, no-download healthy Repair, failed-transfer activation protection, and complete healthy Reinstall. Headless UI checks include both progress bars at handheld widths. Publication fixtures cover blob corruption/omission and substitution of hosted bytes. Linux/macOS CI and the final public download are recorded in the delivery results report. Physical Steam Deck gameplay, suspend/resume, performance and visual acceptance remain separate gates.

## Hosted retention

Only the current public release is retained. A replacement is published and verified before older releases and their assets are removed. Git source history, attribution and local installed rollback/private generations remain intact. Older entry-point downloads should be replaced with the current installer. Repair of a removed historical release requires a complete Reinstall; normal Update selects the current compatible release.

The current release also retains the checksum-pinned `FMVHD-v0.1.0-videos.zip` build input. Its stable latest-release URL is checked against the exact source lock, so clean builds and current source fallback do not depend on deleted mod releases. The old video URL is only a migration fallback for the first consolidated release. Publication binds this input to the successful CI artifact as well.

Routine CI does not retain multi-gigabyte delivery packages. Explicit release-build artifacts expire after one day. After public verification, `scripts/prune-release-history.py --keep-tag <tag> --source-sha <sha> --run <run> --inventory <verified-uploads.json> --execute` removes older releases and completed-run artifacts, preserving the current verification run and any active work. It rechecks the replacement and refuses a concurrent newer release.
