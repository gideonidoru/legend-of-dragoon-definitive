# Unavailable-output shutdown regression

The engine reliability review requires bounded shutdown even when every audio
output is unavailable. Closing before gameplay installs its `stop()` listener
must not leave the non-daemon audio worker alive and retain the managed process.

At accepted baseline `ad1a234d94b4397df681c7fd196581cf3a303a88`, the real
`AudioThread` waits while paused without checking its running state. `destroy()`
clears that state and notifies, but the awakened worker re-enters the same wait.
The headless regression reaches the real unavailable-output wait and then calls
`destroy()` without calling the gameplay `stop()` handler. It fails at the
baseline with an alive worker after the bounded join. Its cleanup uses `stop()`
so the failing test cannot leave a worker behind.

The wait now also requires the worker to be running. A shutdown wake-up reaches
the existing termination check and cleanup. The source/device retry behavior
and the normal gameplay close handler remain unchanged.

Run `deliveryTest --tests legend.definitive.audio.AudioShutdownTest`. Five
tests cover paused destroy, twenty queued retry/shutdown interleavings, normal
stop, and twenty repetitions each of destroy/stop before the first worker turn.
The fixture captures uncaught worker failures as well as bounded termination.
The fixture installs an uninitialized XA source as normal initialization
does, but opens no audio device and emits no playback. This proves the worker
exit condition; physical Deck suspend, output routing and game close remain
device acceptance checks. Music intent, movie output-reset recovery and loading
failures have their own audit records.

## Shutdown before the first worker turn

At baseline `d0121c361da58f9b42907c682830339ecac61255`, a completed `destroy()`
is undone when a delayed `run()` unconditionally sets `running` back to true.
The real worker then waits indefinitely in its unavailable-output state. A
preceding `stop()` is also undone and reaches output handling after stop cleared
the paused flag. The new regressions reproduce both failures: an alive worker
after destroy and an uncaught capability-access failure after stop. The three
previous paused-worker controls still pass at that baseline.

The ranked hypotheses were an unconditional startup reset, shutdown visibility,
and a queued device retry. Deterministic shutdown-before-start feedback isolates
the first without a retry or concurrent native initialization. Shutdown and
startup now share the audio owner's monitor and a terminal shutdown request;
the outer loop's running state is volatile. `stop()` sets its state inside that
same monitor. A late worker skips the buffering loop and reaches existing source
cleanup. There is no new executor, native operation or audio processing change.

The audio owner is one-shot: its sole engine worker is started once, and destroy
already permanently shuts down its device-polling scheduler. This change does
not introduce restarting that stopped owner. Native initialization rollback,
device-poller retirement and physical shutdown timing remain separate checks.
