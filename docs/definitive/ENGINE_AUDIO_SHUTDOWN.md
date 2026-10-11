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

Run `deliveryTest --tests legend.definitive.audio.AudioShutdownTest`. Three
tests cover paused destroy, twenty queued retry/shutdown interleavings and normal
stop. The fixture installs an uninitialized XA source as normal initialization
does, but opens no audio device and emits no playback. This proves the worker
exit condition; physical Deck suspend, output routing and game close remain
device acceptance checks. Startup before the worker begins, lost music commands,
movie output-reset recovery and loading failures are separate audit items.
