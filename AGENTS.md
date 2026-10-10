# Definitive release retention

The owner requested on 2026-10-10 that only the current published release be retained. Do not publish a new downloadable release for every code iteration. Build and test normally; publish when a new download is requested or a release is ready.

After the replacement passes exact-source CI and the complete publication gate, remove superseded GitHub releases and their downloadable assets. Preserve Git source history and attribution. Keep required checksum-pinned build inputs (including FMVHD video resources) in the current release; clean builds and the current installer fallback must not require deleted historical releases.

Large CI delivery artifacts are retained only for explicit release builds, for one day. Remove artifacts from completed superseded runs after replacement verification. Preserve active runs and the current verification run during publication. This hosted retention policy does not delete player saves, imported discs, local installed rollback versions or recovery data.

Refresh `delivery/Install-Definitive.sh` and `delivery/Install-Definitive.desktop` together from the verified release outputs before pruning, so their paired checksums target the retained download. Recording that entrypoint update does not require another published release.

Use `scripts/prune-release-history.py` with the verified complete upload inventory after publication. It refuses an unpublished replacement, wrong source/build/inventory or a newer concurrent release. Never prune first and verify later.
