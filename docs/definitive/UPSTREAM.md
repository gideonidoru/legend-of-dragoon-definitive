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

Put presentation/content adapters in separate mod JARs using the existing mod loader, with stable namespaced registry IDs, explicit compatibility ranges and optional settings. Keep artwork external with a versioned manifest (creator, source, permissions, checksum, supported engine SHA, mapping and memory budget). Do not assume an API annotated version is a stable release contract.

Use project-specific documentation under `docs/definitive/`. Future installation/package tooling and manifests should have separate directories and explicit inputs. Separate gameplay convenience mods from presentation mods so faithful mode can independently disable them. Prefer existing configuration and events over duplicating engine systems. If an engine seam is missing, propose one small upstream-compatible change with behavior tests, rather than maintaining a broad fork patch.

## Release boundaries needing resolution

GitHub Actions is disabled at repository level. Imported workflows publish to upstream metadata services, reference unavailable upstream secrets, alter version fields and automatically publish on main. They are retained for provenance, but must be replaced with source/build-only CI before re-enabling Actions. Future releases need a clean package allowlist, source/license/credit inclusion and a private-data audit.

`src/main/java/legend/core/Updater.java` still queries official Severed Chains releases. Do not ship a Definitive package until the update policy prevents it being replaced with an incompatible upstream binary. Options to evaluate: manage upstream and optional mods as separately versioned components, or create a scoped fork update channel. No updater behavior was changed in milestone 1.
