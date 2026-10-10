# Installer, updater and launcher reliability

Latest delivery recovery: [Deck installation discovery, maintenance, discs, shared input and direct Steam launch](DECK_RECOVERY.md).

## Report and confirmed defects

On 2026-10-09 the owner reported that the guided installer on Steam Deck did not create the expected folder, install files or add a Steam shortcut. Preparation used a bouncing bar with generic text, the disc picker obscured progress, and Finish remained open. The bootstrap log contained Java Unsafe warnings, while the preparation log was empty. Those warnings do not identify the installation failure.

The exact cause of the missing Deck installation remains unconfirmed because the original worker had no persistent exception log. The actual release download and installation succeeded in an isolated Mac test; that does not establish that the same operation succeeded on the Deck.

Confirmed source defects were reproduced by driving the real setup buttons: progress stayed indeterminate and Finish changed to the launcher instead of closing. Background exceptions were shown only in a small footer, without a stack trace or a dedicated failure state. Relative destinations could resolve inside temporary bootstrap storage. These are corrected independently of the still-unconfirmed device-specific trigger.

## Required behavior

- Installation advances only after the active package inventory, artwork/dependencies, data folder and executable launchers are verified. Launcher checks happen before publishing new active state.
- A complete screen follows successful disc preparation, with the installation folder and optional Steam integration. Adding the shortcut successfully or finishing without Steam verifies readiness and closes setup.
- Errors replace the body with an explicit stopped screen, the cause, a retry action and readable log details. A failed operation cannot be presented as completed.
- The file picker is disposed before its callback starts preparation. Cancel closes the picker and re-enables the installer. A failed Steam addition keeps setup available for retry or finishing without Steam.
- Progress describes the current task: downloaded/copied MB, package files being unpacked, disc checks, and upstream conversion/writing counts. The steady bar represents completed phases, not a time estimate. Work with unknown totals does not fabricate a percentage or ETA. Elapsed time continues while waiting.
- Only complete, platform-matched, checksum-verified release packages activate. Interrupted, oversized, HTTP-error and corrupt downloads leave the active version/data unchanged and remove the temporary download.
- Updates retain pre-update data and the previous verified package; rollback reactivates the previous engine with copied prior data, retaining newer data separately.
- The launch scripts retain errors and exit nonzero on startup failure. The managed game process writes a per-workspace log. Failed starts release the operation lock. Known original launch scripts can be upgraded atomically; owner-customized scripts are preserved.

The UI copy describes actions directly. Paths wrap on handheld screens. Relative destinations are rejected and `~/` is expanded before use. Remote downloads have connection, body and inactivity limits; source cloning/building and disc preparation have wall-time limits. Initial network setup checks free space; disc copying checks space against the selected images. These checks improve failure handling; they cannot guarantee hardware, storage, network or Steam behavior under every condition.

## Logs and recovery

- `~/.cache/legend-of-dragoon-definitive/desktop-bootstrap.log`: starting the downloaded entry point.
- `~/.cache/legend-of-dragoon-definitive/installer-bootstrap.log`: portable installer and Java preparation.
- `~/.cache/legend-of-dragoon-definitive/installer.log`: operation starts, phases, completions and full exceptions. **Show error details** reads a bounded tail of this file.
- `<installation>/launcher.log`: Java/bootstrap/manager startup, including failures before a window exists.
- `<installation>/workspaces/<version>-<data>/preparation.log`: private upstream disc extraction status/errors.
- The same workspace's `launcher.log`: managed game-process output.
- A source fallback retains its `.source-build-*/setup-build.log` and checkout for diagnosis/corresponding source access.

Use **Back to setup** after resolving the displayed cause. If import succeeded and extraction failed, return to the disc step and choose **Use installed discs**; existing images are retained. Add to Steam now requests a graceful client exit, verifies the backed-up shortcut edit, and restarts Steam before reporting success. A failed shortcut operation does not declare Steam integration successful or overwrite an unexpectedly changed library. Existing shortcuts are backed up.

Download a new `.desktop` from the corrected release. Previously downloaded entries are pinned to their original script/checksum and cannot silently become a different installer.

## Verification

Development host: Apple Silicon Mac Studio, macOS 27.0.1, Corretto Java 25, Gradle wrapper 9.1.0. Local checks use headless Swing rendering and isolated synthetic packages/Steam data. No test edits the owner's Steam library or starts retail gameplay.

The first regressions reproduced two failures before correction. The corrected actual install button creates a verified active package and `Play Game.sh`; corrupt-package tests keep activation absent and show a visible retry/error screen. Finish has a close callback after readiness verification. Layout tests check every action at 1024/1100 by 700 and render install, disc, success, working and failure screens for inspection.

A private real-disc pipeline installed the corrected Mac package, checked its inventory/HD mod, copied and SHA256-verified all four owner-supplied images, completed real upstream extraction, verified the launchers and wrote a binary shortcut into a separate fixture library. It completed in about 14 seconds on this Mac's local storage with the runtime/dependencies already present. That is not a Deck performance estimate. The source revision for this first local run was the working tree based on `f263cfe0d32face4933ca8f140fdfaedce3baf59`.

Reproduce public checks:

```sh
./gradlew --no-daemon --console=plain build definitivePackage portableInstaller \
  -PreleaseTag=definitive-alpha-2026-10-09-recovery
python3 scripts/assemble-installer.py
python3 scripts/verify-desktop-entry.py
python3 scripts/verify-portable-bootstrap.py
python3 scripts/verify-bootstrap-locks.py
python3 scripts/test-release-publication.py
# Linux CI: real Swing window lifecycle without a physical desktop
xvfb-run -a ./gradlew --no-daemon --console=plain managerWindowTest \
  -Pos=linux -Parch=x86_64 -Psteamdeck=true
```

`InstallerFlowTest` drives real buttons/workers, verifies actual installed outputs and failure UI, checks destinations and screen fit. `InstallerWindowTest` uses a Linux virtual display to verify the picker is closed before its callback and Finish disposes the actual window. `UpdateFailureTest` exercises the actual transfer/checksum/activation path with isolated HTTP transport; it covers errors, corruption, interruption, oversized metadata, successful activation/data retention and rollback. `LauncherFailureTest` uses non-game fixtures to check persistent startup/game-process errors, lock release, linked-log rejection and launcher repair/customization retention. Existing package, archive, restore, data and Steam fixtures remain required.

Hosted Linux/macOS builds, the virtual window checks, source/assets and their digests are recorded below. Physical Deck installation, touch/controller operation, Gaming Mode library visibility, interrupted real-device updates and long-term gameplay remain acceptance checks. Compilation, fixture success and a Mac extraction pass do not establish those results.

Reinstallation also repairs the managed root manager and Java bootstrap from the verified package. Readiness checks their contents against a matching installed inventory, rather than only checking that the files exist. The format-1 root manager must remain compatible with retained format-1 engines so rollback can still route to them; custom launcher scripts are retained. A regression damages both managed files, rejects readiness, then requires repair with saved data unchanged.

Both startup scripts register cleanup immediately after acquiring their locks. Isolated fixtures force temporary-directory creation to fail, then retry; no owned lock remains behind (current regular lock files persist without ownership). These deterministic repairs do not establish the original Deck failure's cause.

The final local build passed 45 delivery checks, with one actual-window test intentionally skipped on the Mac. The corrected real-disc extraction was repeated successfully in about 11 seconds, including named loading/conversion/writing tasks. The sandbox prevented the default home-cache log during that private probe; isolated logging regressions use an explicit writable test log and pass. Bootstrap download, checksum, runtime, startup and temporary-storage failure fixtures pass; native Desktop Entry parsing and actual-window checks are delegated to Linux CI.

Managed bootstrap replacement stages and verifies the complete pair and prior recovery copies before publication. If publication fails, the old pair is restored; a forced failure on the second file verifies the old launcher inventory, active state, saves and operation lock remain usable. If recovery itself encounters a storage error, its copies are retained and their paths are included in the exception rather than discarded.

## Corrected release evidence

Build source: [`ee282fd962e1f91c5a84b6e97c7773d0b6f46783`](https://github.com/gideonidoru/legend-of-dragoon-definitive/commit/ee282fd962e1f91c5a84b6e97c7773d0b6f46783). [Hosted run 38012634240](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/38012634240) completed successfully on Linux x64 and macOS ARM64 using Temurin 25 and the checked-in Gradle 9.1.0 wrapper. Both delivery suites report 46 tests: 45 passed, zero failures, one intentionally skipped window test. Linux separately ran that actual-window case under Xvfb: one passed, zero skipped/failures. Native Desktop Entry parsing, portable bootstrap failure/retry fixtures, native archive fixtures, gameplay opt-in controls and cross-language material fixtures passed. No retail gameplay was executed.

Both downloaded engine ZIPs were independently checked against their complete closed inventories and source/platform identities before release assembly. The Mac CI package (`alpha-35fb33b796893fda`) then installed over the isolated QA installation, reused its privately imported discs, completed real upstream conversion/extraction and readiness checks, and retained the idempotent fixture Steam shortcut. This took about 10 seconds on the Mac, with a writable QA log override. The Linux package is `alpha-57d390de1cb66f29`; it has not been run on a physical Deck.

Corrected tag: [`definitive-alpha-2026-10-09-installer-fix`](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/tag/definitive-alpha-2026-10-09-installer-fix). The tiny entry point was stamped from the hosted Linux portable ZIP; it matched the local portable ZIP byte-for-byte. These assets contain no disc images, extracted retail assets, saves, credentials or downloaded Java runtime. The release retains AGPL/source and Skurfa notices.

| Asset | Bytes | SHA256 |
| --- | ---: | --- |
| `Install-Definitive.desktop` | 1,383 | `14aae65340d593486b1edbf200459b44e46141308cb7451f0fcb75f3adc573b8` |
| `Install-Definitive.sh` | 2,758 | `b4a24873e3528cbe75e37f9f50e390aa7353f37f05e1fd315fb9cc128bd9bea3` |
| `Definitive-Installer.zip` | 9,359,069 | `02bc7c24b6a10c54d6a4da8fa0a6430e4180c24ea6f7e366129a070d6edb4338` |
| `Legend-of-Dragoon-Definitive-linux-x64.zip` | 433,321,163 | `d6a9f6c466945078fe68f701ae01f2f25fbb09c2234dc5c1c07ded4f19fa0a7d` |
| `Legend-of-Dragoon-Definitive-macos-arm64.zip` | 423,764,450 | `96fbb17d2c2af1de50615acd70baf1dc5a8528a17940e2344cc4d6fb21604ac3` |

Commercial release acceptance still requires a clean physical Deck install, both finish paths, actual Gaming Mode visibility, controller/touch and offline launches, real-device update interruption/retry/restore, suspend/resume and sustained gameplay. Use the [Deck protocol](DECK_TEST_PLAN.md) and record failures at this exact release. Passing fixture counts are evidence, not a 100 percent reliability guarantee.

After publication, all six GitHub asset digests/sizes matched the local verified release inputs. The public `.desktop` and `.sh` downloads were fetched independently and matched their repository copies byte-for-byte. A fresh isolated Mac setup then used the live public release API/download, verified its SHA256 and complete inventory, installed the HD mod/data folders/launchers and retained the public asset identity. This network check did not import discs, launch gameplay or edit a real Steam library. The separate private CI-package probe above exercised actual disc preparation and an isolated shortcut fixture.

Release-process note: the first draft-tag API lookup returned 404 and the shell sequence continued to publication before that validation completed. The published assets were subsequently verified independently as described above. Future publication must run asset verification as a separate successful step before the publish action, using a draft-aware release lookup; a failed lookup or validation must stop publication.

## Forced interruption recovery

A subsequent exact-script fixture killed the entire download process group and proved the directory-lock design permanently blocked retry in both startup scripts. Ordinary error cleanup cannot run after SIGKILL. The scripts now hold operating-system file locks: `flock` on Linux and the macOS `lockf` file-descriptor mode. The regular lock file remains in place; it is never unlinked during cleanup. Ownership ends when the last inheriting process closes its descriptor. This keeps an orphaned but still-running download serialized while allowing retry after all owners end or a restart releases their locks.

`python3 scripts/verify-bootstrap-locks.py` exercises both actual scripts with isolated download/window stand-ins: three concurrent retries are rejected, killing only the parent still rejects another installer while the downloader is alive, killing the full group allows retry, and unknown legacy directories/linked lock paths are preserved and rejected. The same fixture against commit `54c5c5dbe` fails all four forced-kill recovery cases. It passes the eight current scenarios on both Mac and Linux in hosted run 38014073457. Logs/processes are private fixtures, without a game or desktop window.

Legacy directory locks cannot be safely distinguished from a live earlier installer because older scripts recorded no owner. The new error explains how to close earlier installers and remove the empty legacy lock directory; it does not delete an unknown directory automatically. Current regular file locks need no manual removal. Abandoned private staging folders are retained after an untrappable kill; no game data is deleted to recover a lock.

Publication now has an executable gate: `scripts/publish-verified-release.py` uses draft-aware `gh release view`, requires the approved account and a successful hosted build for the exact source, checks platform/source/tag correlation, both entry-point checksum chains, the complete six-file upload set, local sums and every uploaded size/digest. It invokes publication only after every prior call/check succeeds. It never creates, replaces or uploads an asset. Repeating it against an identical already-public release verifies it without changes. Thirteen isolated CLI scenarios prove successful publication, annotated-tag resolution, idempotence, and no publication after failed lookup, build, account, digest, size, completeness, actual-tag, wrong-workflow or missing-build-job checks. The CI package verifier and physical device tests remain separate requirements.

The publication gate binds evidence to `.github/workflows/build.yml` and its required Linux, Mac and visual-fixture jobs. It checks the actual Git tag's commit (peeling annotated tags), allowing an absent draft tag but requiring the correct tag after publication. Release `targetCommitish` metadata alone is insufficient for an existing tag, as [GitHub documents](https://docs.github.com/en/rest/releases/releases#create-a-release). A read-only gate run against the earlier public repair passed using real workflow/tag metadata; it made no release changes.

## Recovery build evidence

Build source: [`d1598b705a6fe37716a4ad603d0ddfacb4b09e92`](https://github.com/gideonidoru/legend-of-dragoon-definitive/commit/d1598b705a6fe37716a4ad603d0ddfacb4b09e92). [Hosted run 38014073457](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/38014073457) completed successfully with all three required jobs. Both Linux and Mac delivery suites passed 45 tests, with zero failures and one deferred window test; Linux separately passed that actual-window test under Xvfb. Both platforms passed eight forced-interruption/concurrency scenarios, thirteen publication-gate scenarios, desktop/bootstrap/archive checks and the existing build/visual checks.

Downloaded packages were independently verified against their complete inventories and platform/source identities: Linux `alpha-adefb1fccf7582ca`, Mac `alpha-404f022d1c34dd34`. The Mac package installed over the isolated private QA installation, prepared all four previously imported discs using the real upstream extractor, verified launchers and retained the idempotent fixture Steam shortcut. This run took approximately 11 seconds with runtime and disc images already present. It did not launch retail gameplay or touch a real Steam library.

The entry points were stamped from the actual hosted Linux portable archive, whose checksum matched the local build. The earlier repair release remains unchanged. Recovery release inputs:

| Asset | Bytes | SHA256 |
| --- | ---: | --- |
| `Install-Definitive.desktop` | 1,378 | `fd44bee8b860a881ec32c3e3472937ed4d1a14396202921497209346462f11e9` |
| `Install-Definitive.sh` | 3,434 | `649b192eff62606d4a3e560101f9a0c09f96349538ccbc8fa4a4d330d1ebe549` |
| `Definitive-Installer.zip` | 9,359,332 | `1dbce9d562a04db1713e679f7d7eb7506f0a01dc55034398ed545beba4d39a0b` |
| `Legend-of-Dragoon-Definitive-linux-x64.zip` | 433,321,408 | `38bf504e496b8a82262e5c24986b8bc96359d3c4f4f36a07ed84fc6a828fde71` |
| `Legend-of-Dragoon-Definitive-macos-arm64.zip` | 423,764,693 | `d5a690893a3b64df4dd9ba02784f4a622e11774a0a29935119dd8936b95436ef` |
| `SHA256SUMS` | 492 | `725141e5c6a99a2b5107319316d5509d0595e87b6cdd3f07a7a50dabda371580` |

Live draft verification initially stopped safely because GitHub assigned the unpublished draft an `untagged-…` HTML/download URL, while its requested `tagName` remained the recovery tag. The correction at `81de2dad82828c6049559f831a99ad84e7a0081f` accepts that temporary token only for a draft and only when it matches the exact approved-repository draft URL. Post-publication verification still requires the intended public tag. The observed transition is now a regression fixture, alongside mismatched draft tokens and temporary public URLs; all sixteen local cases passed and both focused reviews found no remaining actionable issue. This change affects standalone publication tooling, not the already verified engine/installer package inputs.

The standalone publication correction passed [hosted run 38014629317](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/38014629317), including all sixteen gate cases on Linux and Mac, before publication. The [recovery release](https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/tag/definitive-alpha-2026-10-09-recovery) was then published using the executable gate. Post-publication verification confirmed the actual Git tag, complete upload set, sizes/checksums and intended public URLs. Independently fetched public `.desktop` and `.sh` files matched their repository copies byte-for-byte. A fresh isolated Mac installation selected this exact public source/tag, downloaded and verified the full package, created the HD mod/data/launchers and retained the public asset identity. That public-path probe did not import discs, launch gameplay or modify a real Steam library. Physical Deck acceptance remains pending.
