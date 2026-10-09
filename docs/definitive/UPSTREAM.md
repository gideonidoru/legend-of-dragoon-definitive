# Upstream synchronization and modularity

## Repository topology

- `origin`: https://github.com/gideonidoru/legend-of-dragoon-definitive.git (public, owner authenticated as gideonidoru).
- `upstream`: https://github.com/Legend-of-Dragoon-Modding/Severed-Chains.git (official engine source).
- `main`: upstream history plus small project commits. Never squash or rewrite the imported upstream ancestry.
- `upstream-baseline`: unchanged initialization commit `fba1543543865e29ee572f479003d9b47158eeb3`. Keep this initial evidence branch fixed; record newer tested SHAs separately.

The complete ancestry of upstream main was cloned. Push main and the fixed baseline branch, rather than mirroring all upstream tags (which could misleadingly advertise official releases here).

## Update procedure

Start from a clean worktree. Replace the example branch suffix with the update date.

```sh
git fetch upstream --prune
git switch main
git pull --ff-only origin main
git switch -c sync/upstream-YYYY-MM-DD
git merge --no-ff upstream/main
# resolve conflicts; inspect source, API, configuration, launcher and updater changes
./gradlew --no-daemon --console=plain clean build
./gradlew --no-daemon --console=plain clean build -Pos=linux -Parch=x86_64 -Psteamdeck=true
# update validation record with upstream SHA, JDK, logs and mod compatibility results
git push -u origin sync/upstream-YYYY-MM-DD
# review a pull request before merging into main
```

No automatic upstream merges. Preserve upstream licensing/credits at every sync. Build before and after integrating project changes; record failures rather than labeling a package as Deck-tested. Future mod tests should pin the engine commit/API version and cover missing packs, disabled mods, faithful configuration, and saves with missing mod IDs.

## Change boundaries

Put presentation/content adapters in separate mod JARs using the existing mod loader, with stable namespaced registry IDs, explicit compatibility ranges and optional settings. Keep artwork in separately pinned integrations with a versioned manifest (creator, source, permissions, checksum, supported engine SHA, mapping and memory budget). Do not assume an API annotated version is a stable release contract.

Use project-specific documentation under `docs/definitive/`. The root README describes Definitive; the unmodified initial upstream README is retained at `docs/upstream/README.md`. During sync, review newer upstream documentation for relevant changes without replacing the project overview or silently rewriting the historical baseline copy. Future installation/package tooling and manifests should have separate directories and explicit inputs. Separate gameplay convenience mods from presentation mods so faithful mode can independently disable them. Prefer existing configuration and events over duplicating engine systems. If an engine seam is missing, propose one small upstream-compatible change with behavior tests, rather than maintaining a broad fork patch.

## Release boundaries needing resolution

The imported publishing workflows were removed from the active tree (preserved in Git history) and replaced with `.github/workflows/build.yml`. Only build/packaging and synthetic test-control checks run; there are no release, package publishing or upstream metadata steps. Actions is enabled with an allowlist limited to the exact two official action commits in build.yml; update that allowlist when intentionally updating the pins. During upstream sync, retain the build-only workflow and do not restore upstream publishing jobs automatically. Future releases need a clean package allowlist, source/license/credit inclusion and a private-data audit. See [BUILD_CONTROLS.md](BUILD_CONTROLS.md) for activation and verification status.

As of 2026-10-09, `src/main/java/legend/core/Updater.java` queries `https://api.github.com/repos/gideonidoru/legend-of-dragoon-definitive/releases`. The project release feed replaces the official upstream feed. Preserve this small project change during upstream merges. Milestone 1's original build evidence remains unchanged; this adjustment is recorded in [UPDATER_TARGET.md](UPDATER_TARGET.md).

Existing release selection still requires a stamped `Version.TIMESTAMP`, a tag beginning with `Version.CHANNEL`, and a release timestamp newer than the build. Manually compiled snapshots skip update checks. Platform asset selection and application behavior are unchanged. Before publishing binaries, define compatible engine/mod version pairs and package assets; retaining the project feed alone does not establish safe upgrades or rollback.

## Skurfa submodule updates

`integrations/skurfa` is a public submodule pinned to `3c9e4b3ecefc31cb32fd1281a7857f3a08f56081`. Recursive clone includes its source and artwork without copying those files into the engine history. CI checks out submodules. After pulling project updates, run `git submodule update --init --recursive`; do not use `--remote` for routine builds.

Review a new Skurfa commit on an integration branch. Check source/API changes, mapping and notices; update the gitlink, delivery manifest and build metadata together. Build both platform packages, verify the mod JAR/resources, and compare representative scenes and original-background fallback before accepting it. Keep engine and artwork updates in separate reviewable commits. See [INTEGRATION_ALPHA.md](INTEGRATION_ALPHA.md).
