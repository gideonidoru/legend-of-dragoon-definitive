# Audio cleanup continues after a source failure

The engine reliability review requires shutdown to retire remaining resources
after a recoverable Java cleanup failure. At accepted main
`0729ff3f4b8f5a4d78c938638a54ac13d8ed8529`, the real audio worker stops source
cleanup at the first thrown error or runtime exception. Later sources, the
native context/device and scratch buffer are skipped. The worker exits, but
the normal close caller continues waiting for the device handle to become zero.
Direct cleanup before worker startup also stops at that first failure.

A private minimal probe reproduced the actual worker/caller interleaving with
an inaudible OpenAL loopback device: the worker exited with the controlled original
error, the later native source was not retired, the device remained owned and the
close caller was still alive at the bounded deadline. The identical healthy
control completed. No game, speaker output, graphics context or original game
assets were used.

The ranked hypotheses were early abort at the first source failure, cross-thread
visibility of a completed cleanup, and a loopback-fixture lifecycle mismatch.
The healthy control and observed unretired source/device isolate the early abort.
The device field is now volatile because the existing close caller reads it
outside the owner's monitor; this makes the completion publication explicit.

## Correction

Cleanup attempts every initialized source, then context/device and scratch-buffer
retirement. The first thrown failure is retained and subsequent failures are
attached in order as suppressed failures. Error and runtime-exception identities
are preserved when the completed cleanup rethrows. The scratch-buffer reference
is cleared and device retirement is published after native cleanup attempts and
buffer retirement, so the existing close wait observes the completed native phase.

Terminal cleanup separately clears pending XA state and attempts retained-decoder
release even after source cleanup fails, preserving the first failure if that
release also throws. A running close caller always notifies the paused worker,
including when its initial decoder release throws, waits for native retirement,
then rethrows that original caller failure. Worker cleanup failures retain their
existing worker-thread observer. The generic cleanup used by output recreation does not unload XA
state, preserving its existing recovery behavior. Queueing, polling, clocks,
sample processing, volume, source selection and normal playback are unchanged.

## Evidence and limits

Twelve isolated child-JVM regressions exercise actual `AudioThread.destroy` and
the worker's final cleanup. They cover error/runtime/multiple-failure cases both
before worker startup and after the real unavailable-output wait, plus healthy
direct/worker controls. Four additional cases cover a throwing decoder release
(error/runtime on the paused-worker path), direct decoder-error cleanup and source
plus decoder failure precedence. They check bounded caller and worker retirement, deletion of a later
real native source, owner context/device retirement, retained decoder release,
original failure identity, suppression order and repeated destroy. A fixture
source binds only its own loopback context for native deletion on the worker.

The first eight fixtures reproduce six failures with two passing controls on
unchanged main. Independent Spec review then identified the early decoder-release
call before worker notification. The expanded suite reproduced both paused-worker
decoder failures against the first correction, with its ten other cases passing:
the caller had received its original decoder failure, but the worker remained
asleep and the later source/device remained owned. This isolates skipped worker
notification from a swallowed error or native source failure.

Replaying all twelve final fixtures against unchanged main gives ten failures and
two passing healthy controls. The direct decoder-only fixture already retires
native resources there; its failing check is the uncleared scratch-buffer reference,
not a worker hang. It passes on the first correction as a control for the later fix.

Corrected source passes all twelve. The original minimal probe was rerun corrected
and completes, preserving the worker's original error. All 38 focused audio cases pass,
including existing source recreation, unavailable-output shutdown and XA controls.
Detailed source-bound logs and native diagnostic fixtures remain private.

This proves controlled Java source-cleanup failure handling and native retirement
in the inaudible loopback fixtures. It does not establish recovery from exhausted
memory, fatal native crashes, rejected native close operations, a decoder that fails
before releasing its own resources, exceptions during
the buffering loop, partial native initialization or device-poller retirement.
Physical Deck output, suspend/resume, game close and the broader rendering,
frame pacing, responsiveness and campaign acceptance obligations remain open.
