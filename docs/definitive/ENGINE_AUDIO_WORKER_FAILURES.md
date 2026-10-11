# Active audio worker failures retire native resources

The engine reliability review requires a failed audio worker to retire its owned
resources and allow later game close to complete. At accepted main
`6d5c0d1a671e4c93bbd41d5efd6dd17d6a236b53`, terminal cleanup follows the processing
loop. An error or runtime exception in a source callback bypasses that cleanup.
The worker exits with its original failure while its running flag remains set,
later native sources and output remain owned, and the normal close caller waits
for a worker that has already exited.

A private actual-worker probe reproduced this twice with an inaudible OpenAL
loopback device and a controlled `canBuffer` error. In both runs, the worker had
exited with that same error, the later native source/device remained unretired,
and the close caller was still alive at its bounded deadline. An identical healthy
stop control completed. No game, speaker output, graphics context or original
game assets were used.

The ranked hypotheses were cleanup after the loop being unreachable on failure,
the running flag remaining set after worker exit, and a native-fixture mismatch.
The healthy control excludes the fixture mismatch. The actual worker failure and
unretired resources establish the skipped cleanup; the stale flag then routes
later close into its wait for native retirement.

## Correction

The existing processing loop moves into a private method without changing its
source iteration, buffering calls, device recovery, clock or sleep decisions.
The worker wraps it in terminal cleanup. On every loop exit, the owner monitor
marks the worker stopped and shutdown requested, then attempts the existing
source/native/XA cleanup. A later invocation cannot restart the retired owner.

The original processing failure remains the primary error at the existing worker
exception observer. A cleanup failure is attached to it as suppressed, retaining
the cleanup accumulator's own ordered source/decoder failures. A normal exit
with a cleanup failure still reports that cleanup failure. The existing close
caller observes native retirement instead of waiting indefinitely.

This is terminal failure handling, not automatic playback recovery after an
arbitrary source error. No source is silently skipped or retried. Music intent,
volume, sample generation, source ordering and healthy playback are unchanged.

## Evidence and limits

Eleven isolated child-JVM cases exercise the actual processing worker and close
caller with native loopback resources. They cover error and runtime-exception
failures in `canBuffer`, `tick` and processed-buffer handling; two combinations
with later source and decoder cleanup errors; close contending for the processing
monitor before the injected error; and healthy stop/destroy controls.

Against unchanged main, all nine failure cases reproduce a waiting close caller
and unretired native source/device, while both healthy controls pass. Corrected
source passes all eleven and all 49 focused audio cases, including the previous
shutdown, source-recreation, XA and cleanup-failure regressions. The original
minimal failure probe and its healthy control are rerun after correction.

The fixtures check bounded worker/caller retirement, later real native-source
deletion, context/device and scratch-buffer retirement, attempted retained-decoder
release, terminal owner state, original error identity, suppression hierarchy,
and repeated destruction or worker invocation after retirement. The source that
deletes native buffers binds only the fixture's own loopback context.

These results establish controlled Java processing-failure retirement. They do
not establish recovery from exhausted memory, fatal native crashes, native close
rejection, a decoder failing before its own release, partial native initialization,
device-poller retirement, physical Deck audio or suspend/resume. The wider visual,
frame-pacing, responsiveness and campaign acceptance obligations remain open.
Detailed source-bound native diagnostics remain private.
