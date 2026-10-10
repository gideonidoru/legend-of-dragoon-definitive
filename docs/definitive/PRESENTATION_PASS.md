# Installer, launcher and updater refinement

The presentation milestone follows the owner's October 9 request for restrained, polished, readable screens and automatic Steam integration. It changes the delivery UI and Steam lifecycle only; engine rendering and gameplay are unchanged.

## Presentation

The shared palette uses warm paper, pine, dark ink and white cards over existing Skurfa artwork. Forms are centered in a consistent 520-pixel column; the install path and actions align. The default remains `$HOME/Games/Legend-of-Dragoon-Definitive`. Text smoothing is applied consistently even when desktop font settings are absent. Body copy is shortened, secondary actions are paired, and long paths wrap at useful separators instead of leaving an isolated final letter. Touch targets remain 48–60 pixels.

The launcher keeps Play primary. Updates have an explicit review screen, real phase/size/count progress, a verified completion screen and a return to the launcher. Restore, account selection, file selection and error details share the presentation. Errors keep the complete log while bounding the main-screen summary. Selecting disc files dismisses the picker before preparation starts. Both verified finish paths dispose the installer.

## Steam integration

Clicking Add to Steam authorizes the installer to request a graceful exit, wait for all owned Steam processes to stop, back up and atomically edit the selected account's shortcut file, reread and verify exactly one matching launcher, then restart Steam. Progress names each phase. The integration lock spans accounts in the same Steam userdata directory, preventing overlapping installers from closing or restarting each other's client. Existing library-write guards remain active. No client is force-killed.

Shutdown failure leaves the library untouched. Edit failure attempts to reopen a previously running client. Restart failure retains the verified shortcut, shows the actual error and permits an idempotent retry; it cannot report success. Client output is retained in the installation's `steam-integration.log`. A process-start check establishes that the client began running, not that its UI loaded or the shortcut appeared in Gaming Mode.

## Evidence and acceptance

Headless tests render the actual shipped components on macOS and Linux; they do not open the owner's desktop. Setup layouts are checked at widths 1024/1100/1280 and client heights 660/700/760, including every visible control, wrapped copy and ancestor clipping. Launcher/update/error/restore views have additional checks. Rendered PNGs are retained with hosted build artifacts. Linux also tests the actual disc picker and installer window under an isolated virtual display.

Steam lifecycle tests use separate synthetic libraries and client stand-ins. They cover close/write/start ordering, exactly-one verification, repeat addition, blocked or failed shutdown, corrupted libraries, restart failures/timeouts, concurrent integration and direct shutdown arguments/logging. They do not shut down or start the owner's Steam.

Physical Deck acceptance remains required: the graceful Steam command and restart on stock SteamOS, account choice, Gaming Mode visibility, both finish paths, touch/controller focus across the restart, reconnect, offline use and update recovery. Passing compilation, rendered fixtures or lifecycle tests does not establish commercial release readiness or a reliability guarantee. Follow [the Deck protocol](DECK_TEST_PLAN.md).

The initial full local Mac build used Corretto 25 and Gradle 9.1.0 with `build definitivePackage portableInstaller -PreleaseTag=definitive-alpha-2026-10-09-presentation`. Its delivery suite reported 62 cases: 61 passed, zero failures, one actual-window case deferred to Linux. The full supported build succeeded after both independent reviews and their recovery/wrapping corrections. Native archive imports, bootstrap download/failure handling, eight lock/interruption cases, sixteen publication-gate cases and workflow lint passed. Mac setup, launcher, update, progress, error and restore renders were inspected. Hosted source/package evidence and Linux visual acceptance are recorded after the exact committed build finishes.

The first hosted Linux render exposed unsmoothed text despite passing layout tests. Shared surfaces now explicitly apply grayscale text smoothing; a black-on-white text fixture requires intermediate edge coverage. This presentation correction is checked by a new exact-source hosted build before publication.

The smoothing correction passed all 63 local delivery cases: 62 passed, zero failures, one window case deferred. It rebuilt the portable installer without opening desktop windows. Both independent focused reviews found no remaining actionable defect in the correction.

The smooth roots are Swing painting origins, so selection, focus and changing progress labels retain smoothing during dirty-child repaints. Linux's actual-window suite now checks immediate opaque-child repaint routing through both roots, as well as picker disposal and Finish. These two tests remain skipped in local headless runs and execute only in the CI virtual display.
