# Repair after a managed game exits

The current public Steam Deck installer could report “Cannot resolve an interrupted game launch safely” after normal quit. A stale launch receipt made recovery enumerate every process; unrelated live processes with unavailable identity/argv were treated as potential game children. The regression reproduces that exact message before the fix.

Repair and Reinstall now stop this installation's verified game/launcher/workers before acquiring maintenance ownership. Process termination requires the same OS user, exact installation-root and launch-token argv, or the recorded PID/start identity of this root's `ManagerMain --play` launcher. Descendant identities are captured while ownership is verified. Every signal rechecks process start time to avoid PID reuse. TERM has a bounded grace period, then remaining owned processes receive KILL. Missing or ambiguous ownership never authorizes terminating another application. Ordinary Update, Restore and unrelated installation operations retain their existing concurrency protection.

Linux's Java process API can omit argument vectors longer than one page. A bounded read of NUL-separated `/proc/PID/cmdline` supplies exact argv for those children. Flattened command text never authorizes termination: spaces, prefix paths and marker text inside another argument must not identify another installation as ours.

The exclusive operation lock and OS game lease remain held throughout maintenance, including download, verification and activation. Pending tokens are revoked while the lease is held; the new game entry validates its token only after taking its lifetime game lease. A rejected child leaves parent-owned receipts alone. The launcher cleans receipts while holding operation ownership, and subsequent maintenance reclaims stale receipts. Busy handoffs are retried for a bounded interval without re-running any maintenance work. Play releases only its temporary idle game lease before spawn, retaining its operation ownership; the actual game JVM then owns its own lifetime lease.

Repair retains saves, settings, module choices, imported discs and prepared workspace. Reinstall retains personal data/choices and retrieves the complete package with fresh preparation generations. The progress phase identifies when the managed game is being closed.

## Verification

- The exact opaque-process classifier regression fails with the user's error on the old guard and passes after the change.
- A real headless process exits normally leaving both durable launch receipts; maintenance clears them automatically and preserves personal data.
- The actual installed root router starts a headless fixture game; explicit maintenance stops its game/manager workers, keeps an unrelated process alive, and preserves data/workspace.
- Both leases are retained during maintenance and released after an injected failure.
- Exact-root/token cases include paths with spaces, sibling/prefix roots and markers embedded in unrelated arguments.
- A captured published `5c0783297` game-entry source is compiled with one gate immediately after its old pre-lease token validation. Repair terminates that real paused legacy process before maintenance. Its 16K command exercises Linux's exact `/proc` argv fallback in hosted Linux tests.
- Existing interrupted parent/child, native lifetime lease, returned non-daemon worker, PID reuse, damaged-state, symlink and transaction-recovery checks remain required.

These are source and headless OS-process checks. Physical Steam Deck exit/Steam Stop/suspend behavior remains device acceptance. A legacy child whose owner/argv cannot be read at all cannot be safely terminated; the retained lease protects maintenance writes. New entries enforce revocation after acquiring the lease. This is not a claim of proven behavior for every opaque older binary or sandbox.
