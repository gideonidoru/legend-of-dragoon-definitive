# Cinematic timing and audio refills

A Steam Deck user reported that the game's opening cinematic ran at roughly eight times normal speed in the published all-HD installer. The video payload was checked separately: OPENH is 1280×768 at 15 fps, with approximately 158 seconds of video and 48 kHz stereo audio. Its source binding and encoded hash match the reviewed payload.

Two engine paths could accelerate playback:

- A streaming source could start while its decoder queue was empty, entering OpenAL's STOPPED state. Appending fresh buffers to that state made them appear processed before they played. Retirement counted that unplayed audio in the movie clock. A real decoder/null-output regression observed a 320 ms media clock after only 48 ms of elapsed time; a three-second video with an audio tail finished in approximately 2.08 seconds.
- Original-video fallback consumed one video frame per render callback and multiplied its 15 fps cadence by the gameplay speed setting. At speed 8, the production scheduler advanced 119 frames in one elapsed second instead of approximately 15.

`AudioSource` now owns one refill transaction for byte, signed-16 and float packets. It pauses a playing native source while retiring the old queue and appending fresh audio, rewinds an empty stopped queue before append, and restores the previous playing state in `finally`. This preserves the current sample position and prevents the native mixer from reaching STOPPED inside the transaction. Empty queues never start. Restart drains a completed old tail rather than replaying it. The played-position accessor rechecks natural EOF after sampling the offset, so a tail finishing during that query cannot make the clock go backward.

Cinematics use their own cadence: 15 fps for original video and 60 render callbacks per second for streamed video. Streamed frame selection follows actually played audio. A scoped renderer mode also disables gameplay frame skipping and neutralizes the speed multiplier in vsync/FPS accounting during playback. Entering a movie clears any skipped-frame queue suppression immediately; cleanup restores the previous scope on completion, skip, fallback and initialization failure. The player's gameplay speed remains unchanged and normal rendering settings are restored afterward. Logs identify whether a cinematic selected FMVHD or original playback.

## Regression coverage

`StreamingAudioTest` uses real OpenAL with silent loopback/null output. It checks:

- Empty startup and repeated underrun refills cannot count audio before it plays.
- A final old buffer completing inside queue preparation cannot discard fresh audio.
- A final old buffer completing between retirement and restart cannot replay.
- EOF occurring inside a position query preserves the complete played tail.
- Incremental refills preserve the same waveform as a prequeued reference across byte, signed-16 and float packet formats. Each capture has a fresh native mixer context.
- The shipped launch logo and the first eight seconds of the pinned OPENH payload follow rendered audio samples and retain future video frames.
- Every one of the 18 shipped enhanced films runs with a primed empty native audio source at each of 30, 60 and 120 callback Hz. All 54 cases verify played-sample timing, due video frames and nonzero decoded mixer output.
- The synthetic three-second audio-tail fixture takes at least 95 percent of its media duration to complete and cannot run its clock more than 100 ms ahead of elapsed time.

`CinematicCadenceTest` runs the production cinematic scheduler through a virtual window at gameplay speed 8 and verifies approximately 15 original frames per elapsed second. Enhanced cadence is checked at gameplay speeds 1, 3, 8 and 16. `CinematicRenderCadenceTest` exercises the actual buffer scheduler at speeds 1, 3, 8 and 16: every cinematic callback presents, even when playback begins on a skipped gameplay frame, and gameplay skipping resumes after cleanup. It checks nested scope restoration, queue acceptance and preserved disabled frame skipping. The original scheduler failed this test before the fix. No SDL window, GPU, Steam client or game session is launched by these tests.

The native queue-state behavior follows the [OpenAL 1.1 specification](https://www.openal.org/documentation/openal-1.1-specification.pdf). These regressions establish engine behavior; physical Steam Deck playback remains a separate acceptance check.

The broader [engine pacing contract](ENGINE_PACING.md) covers gameplay, script/animation cadence, audio ownership and rate transitions. The shared scheduler now re-arms changed rates rather than retaining the previous state's deadline. All 18 shipped films participate in native decoder/played-clock checks across 30, 60 and 120 callback Hz, in addition to the longer opening and logo checks.

## Whole-pack differential and full-length checks

An isolated native loopback probe reproduced the same empty-source startup against the actually published `5c0783297` engine and the candidate `617b221f8` movie/audio classes, using the identical pinned 18-film archive. The published engine failed all 54 film/rate cases: its movie clock advanced with queued audio and callback frequency while native output remained silent. The candidate passed all 54 cases. A separate encoded-asset audit verified all 18 hashes, actual video packet timestamps at frame-index/15 (within timestamp rounding), 15 fps video and 48 kHz stereo audio; it found no accelerated encoding timeline.

Full-length candidate playback then exercised all 18 films through `StreamingMovie`, `MoviePlayback`, `GenericSource` and actual OpenAL loopback mixing at 60 Hz. All 31,184 enhanced frames became due and were polled, each film produced nonzero decoded mixer output, and every decoder/video/audio queue completed, including both endings. The movie clock never led mixed samples; the maximum absolute difference, at silent tails, was 16.001 ms of lag. Container durations totaled 2,079.256665 seconds, including normal audio tail offsets.

These full-length checks advance an injected monotonic clock from the actual native mixer's sample count so that silent tails can be checked without waiting the total film duration in wall time. They establish full-pack timing and completion, not human visual inspection or physical Steam Deck playback. The diagnostic fixtures and logs remain local and contain no exported original movie content.
