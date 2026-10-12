# Installer storage and transfer maintenance increment

Controlling requirements: the owner reported the current public Steam Deck installer still offers maintenance after uninstall, rejects an install with 9 GB free, and may retain temporary files. The owner requested file size and network speed in a restrained installer/updater interface.

Acceptance requirements:

- Reopening the downloaded installer after intentional uninstall offers Install, while finding and preserving retained discs, saves, settings and custom mods. Recovery requiring acknowledgment remains reviewable.
- Download progress shows current-file bytes and a smoothed network rate beneath the current-file bar. Checksum verification, local copies and reused files must not inflate the network rate. Whole-archive and unknown-size downloads remain understandable.
- Reclaim interrupted temporary downloads under the correct exclusive operation lock, using ownership evidence. Preserve unrecognized files, linked external targets, personal data and recovery records. Live and orphaned download processes must exclude cleanup.
- Eliminate the second complete file-delivery staging copy through verified, recoverable activation. Space checks must cover real simultaneous allocations and subsequent disc preparation; failures state required and available space. Do not promise that 9 GB can complete every installation.
- After a timeout, Retry/Repair reuses completed checksum-verified payload files and valid archive chunks from the same pinned inventory. Partially downloaded or changed files must never activate without complete verification. A healthy long installation must not exhaust a single aggregate archive-transfer timer.
- Reinstall remains a clean application replacement that preserves personal files and discs; Repair and Update retain checksum reuse.

Evidence boundaries: headless Swing rendering, deterministic filesystem/transport fixtures and cross-target packages are development checks. Physical Steam Deck acceptance is separate. This document is not a publication receipt.

Measured current public generation 13 Linux inventory: 571 managed files, 4,473,783,807 bytes (4.1665 GiB). The previous file-delivery minimum is 15.3331 GiB before private-data copies (two payloads, six GiB preparation reserve and one GiB safety reserve).

Implementation: failed file delivery retains an ownership-receipted stage bound to the exact inventory. Completed blobs are reverified before reuse; raw archive recipes keep only checksum-verified chunk prefixes and fetch the missing ranges. Partial ordinary blobs restart across separate attempts. Transfer deadlines apply to individual requests, with separate header and idle limits, rather than the lifetime of the archive assembler. The pause screen retries the actual operation.

A successful same-build repair removes its unused staging payload. Local/offline package staging also carries ownership receipts. Clean reinstall checks the space for each distinct personal-data allocation before creating or copying it, preserving the disc-preparation and safety reserve throughout preservation. Unrecognized folders and recoveries remain protected.

Focused verification passed 111 controls with no failures, errors or skips, including actual FileDelivery first-install and full-reinstall-to-repair interruption routes, four archive resume controls, nine storage/recovery controls and 29 clean-reinstall controls. The publisher-produced archive fixture retains 116 verified bytes and fetches only the missing 255 bytes, without refetching completed ordinary files, before exact final inventory verification. Separate shell fixtures killed interrupted bootstrap processes and verified ownership-based cleanup and unknown-file preservation. Independent Standards and Spec reviews found no remaining actionable findings.

The revised baseline fresh-install reserve is 11.1665 GiB before personal-data copies: one 4.1665 GiB payload, six GiB for disc preparation and one GiB safety. Repair and resumed-download preflight account for already retained, verified bytes. These figures describe the measured generation 13 payload; future payload sizes and device free space can differ.

Final local `gradle check --offline` passed. The full delivery suite contains 865 tests, zero failures/errors and eight conditional skips; the existing presentation guard reproduced an empty-label layout failure and passed after hiding labels without values. This includes the new transfer, pause/retry, uninstall-reopen, archive resume and storage controls. The ordinary engine checks also passed; none launched gameplay or established physical Deck acceptance.
