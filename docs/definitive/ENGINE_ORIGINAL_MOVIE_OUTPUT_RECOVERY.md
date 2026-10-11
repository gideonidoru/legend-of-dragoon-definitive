# Original movie output recovery

Original story movies must keep playing when their audio source is recreated. Previously `OriginalMoviePlayback` threw on an output-generation change and the actual `Fmv` render callback stopped the movie, resuming gameplay before its remaining frames. Removing that exception alone left the movie clock stalled: the native queue and its clock were reset, while queued PCM had already left the decoder.

## Required behavior and ownership

- Recover only unplayed PCM, in order, without restarting the movie or scaling samples a second time.
- Retain at most 15 audio blocks, matching the original movie's 16-buffer source with one free buffer reserved for the audio tick. At the decoder's maximum block size this adds at most 483,840 bytes of raw sample arrays; it is independent of movie length. The decoder's existing bounded queue and separate video/audio workers retain backpressure.
- Preserve the last observed movie position across recreation. Trim the first recovered block at its unplayed stereo sample frame and translate the reset native clock onto the movie timeline. The position may only advance.
- During an unavailable-output interval, continue video on active time. Discard only PCM already passed on that timeline; retain future PCM for a returning device. Pause excludes elapsed time and defers recovered audio submission until resume.
- If connection loss is detected during upload, retain that block for recovery rather than treating a silent native rejection as delivery.
- Corrupt movie data still fails. Normal stop/skip still releases the movie workers, audio source and presentation ownership.

The movie clock/recovery state stays on the render owner. The `GenericSource` overload retains its source monitor across inspection/submission and does not acquire the audio-owner monitor. PCM retained for recovery remains unmodified; each native submission receives its own scaled copy. Completed blocks are retired, and movie cleanup releases the playback owner.

## Evidence

The unchanged baseline fails a deterministic source-recreation regression with `IOException: Original movie audio output was reinitialized`. A private variant bypassing only the exception fails the next assertion: expected 200,000 microseconds, observed 100,000. Replaying the updated owner probe against the unchanged baseline also fails the actual render callback's remaining-movie assertion.

The focused tests cover partial-buffer reset, repeated resets with exact XA predictor-ordered PCM comparison, single volume scaling, silent-outage alignment, pause exclusion, connection loss during upload, bounded history and completion through an entire silent movie. A real `GenericSource` destroy/init cycle is tested using an inaudible OpenAL loopback device. Existing presentation-rate, starvation, decoder-error and silent-tail tests remain applicable.

The private original-disc owner probe exercises real `Fmv.play` and the complete `NoopWindow` render callback. A controlled source-generation transition must preserve the same movie and must not resume gameplay. It also checks existing paused stop/action skip/mouse skip cleanup at all 48 combinations of 20/30/60 base rates and 1–16 game-speed settings. No original movie is committed or saved by the probe.

Run the focused tests with `deliveryTest --tests 'legend.definitive.fmv.OriginalMovie*Test'`. Run the original-disc owner probe separately with `originalFmvHandoffProbe -PfmvOriginalFile=<private-original-movie-path>`.

## Acceptance limits

This increment covers original movie recovery, including the original fallback after an enhanced movie fails. Enhanced playback still follows its existing fallback policy. Output recreation can recover from the last position the render owner observed; audio played after that observation and before loss cannot be reconstructed from a discarded native clock. During an outage, audio belonging to elapsed silent movie time is intentionally not replayed.

Headless callbacks and OpenAL loopback verify ownership, timing and source lifecycle behavior. They do not establish physical Steam Deck device reconnection, visual quality, controller latency, battle rendering, model-head consistency or campaign acceptance. The user's corrected FMV-speed report remains separate from this engineering-audit recovery defect.
