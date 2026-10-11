# Installer and launcher follow-up audit

Direct owner request, October 11, 2026: deeply audit installer and launcher, correct edge cases, bugs, gaps and inefficiencies, and aim for professional commercial quality. This is a separate improvement patch. It must not delay or modify the previous clean-Reinstall release. Frozen audit baseline: `d73113341610af56f27a1c68e7f5172d157b62c0`.

Scope: bootstrap scripts and runtime selection; download discovery/resume/body limits; package validation and staging; private data snapshots/Restore; interrupted operation recovery; operation/game leases and supervised processes; launcher diagnostics; installer responsiveness and preservation. Work runs headlessly in isolated fixtures. No windows or live game are launched. Build/test proof does not establish physical Steam Deck, real game or long-term reliability acceptance.

Source findings being corrected:

- Restore verification skips every file named `snapshot-files.properties`, including personal files below a subdirectory, while snapshot creation inventories them. This prevents legitimate updates/Restore.
- Package hashing, copying and unpacking do not consistently observe interruption; large work can continue after cancellation. Tree copying also accepts linked destination parents.
- Download continuation validates range headers without bounding the body to the advertised range when Content-Length is absent. Unexpected content encoding is not rejected before copying.
- Installer construction performs disc inspection and potentially journal recovery on Swing's interface thread.
- Generated launcher logging ignores the logger pipeline's exit status and loses private startup output when publication fails under shell automatic cleanup.
- Repeated location recording performs redundant complete package verification in the same installer action.
- Native RAR/7z extraction stages outside the installation operation lease and uses an unsupervised helper; another maintenance action can race it, and a killed installer can leave extraction alive. Keep the lease from source inspection through verified import, and supervise separate bounded binary/stdout and diagnostic/stderr channels through the existing process supervisor.
- An empty/truncated/oversized runtime path receipt can prevent offline selection of an already cached verified Java runtime. Read receipts conservatively and fall back without downloading; reject a linked cache root.
- Portable installer Java startup failures are printed transiently after the progress window closes, without being saved in the bootstrap log or producing the normal error screen. Persist bounded startup output while preserving process and logging failure status.
- A download stream-close failure can replace an interruption with a retryable IOException. Preserve the original cancellation, attach cleanup errors, restore interrupt status and never retry a reader that did not stop.
- Unsupported launcher arguments silently open the interface. Reject malformed commands before filesystem discovery or UI startup and provide explicit help.

Acceptance: targeted regressions for each behavior, full existing delivery/ModelsHD/CharHD checks and package builds, real bootstrap stand-ins with no network/UI, independent Standards and Spec review against the frozen baseline. Record limitations and unresolved observations candidly; do not claim a numerical quality score from source checks alone. Publication and main integration remain with the project orchestrator after the prior release.

## Validation and operational boundaries

Six baseline regression cases failed before the first fixes; two more baseline download-cancellation controls also failed before their fixes. Focused corrected source tests pass. Tests use original shipped Java/shell paths with isolated fake downloads/JVM/dialogs where needed, not copied replacement implementations. Process controls include termination-resistant descendants, binary output above the diagnostic-log cap, separate bounded stderr, fast output overruns, explicit cancellation and killed-owner cleanup.

The existing immutable package activation, complete hashes, accepted-generation floor, current-release identity, saved-data separation, game-owned OS lease, pinned runtime/download checksums, strict source fallback and selected-only storage removal remain controlling contracts. No whole-fork merge, save-format change, publication or unrelated art/runtime promotion is part of this patch. Owner-customized launch scripts remain untouched. Source hashes and builds are evidence for this patch, not certification of commercial readiness or a 10/10 score. Physical Deck/GLES playback, interaction, Steam client and interrupted-power/disk-device acceptance remain external validation.
