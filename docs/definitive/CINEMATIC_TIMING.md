# Cinematic timing and audio refills

A Steam Deck user reported that the game's opening cinematic ran at roughly eight times normal speed in the published all-HD installer. The video payload was checked separately: OPENH is 1280×768 at 15 fps, with approximately 158 seconds of video and 48 kHz stereo audio. Its source binding and encoded hash match the reviewed payload.

Two engine paths could accelerate playback:

- A streaming source could start while its decoder queue was empty, entering OpenAL's STOPPED state. Appending fresh buffers to that state made them appear processed before they played. Retirement counted that unplayed audio in the movie clock. A real decoder/null-output regression observed a 320 ms media clock after only 48 ms of elapsed time; a three-second video with an audio tail finished in approximately 2.08 seconds.
- Original-video fallback consumed one video frame per render callback and multiplied its 15 fps cadence by the gameplay speed setting. At speed 8, the production scheduler advanced 119 frames in one elapsed second instead of approximately 15.

`AudioSource` now owns one refill transaction for byte, signed-16 and float packets. It pauses a playing native source while retiring the old queue and appending fresh audio, rewinds an empty stopped queue before append, and restores the previous playing state in `finally`. This preserves the current sample position and prevents the native mixer from reaching STOPPED inside the transaction. Empty queues never start. Restart drains a completed old tail rather than replaying it. The played-position accessor rechecks natural EOF after sampling the offset, so a tail finishing during that query cannot make the clock go backward.

Cinematics use their own cadence: 15 fps for original video and 60 render callbacks per second for streamed video. Streamed frame selection follows actually played audio. The player's gameplay speed remains unchanged and normal rendering settings are restored afterward. Logs identify whether a cinematic selected FMVHD or original playback.

## Regression coverage

`StreamingAudioTest` uses real OpenAL with silent loopback/null output. It checks:

- Empty startup and repeated underrun refills cannot count audio before it plays.
- A final old buffer completing inside queue preparation cannot discard fresh audio.
- A final old buffer completing between retirement and restart cannot replay.
- EOF occurring inside a position query preserves the complete played tail.
- Incremental refills preserve the same waveform as a prequeued reference across byte, signed-16 and float packet formats. Each capture has a fresh native mixer context.
- The shipped launch logo and the first eight seconds of the pinned OPENH payload follow rendered audio samples and retain future video frames.
- The synthetic three-second audio-tail fixture takes at least 95 percent of its media duration to complete and cannot run its clock more than 100 ms ahead of elapsed time.

`CinematicCadenceTest` runs the production cinematic scheduler through a virtual window at gameplay speed 8 and verifies approximately 15 original frames per elapsed second. Enhanced cadence is checked at gameplay speeds 1, 3, 8 and 16. No SDL window, GPU, Steam client or game session is launched by these tests.

The native queue-state behavior follows the [OpenAL 1.1 specification](https://www.openal.org/documentation/openal-1.1-specification.pdf). These regressions establish engine behavior; physical Steam Deck playback remains a separate acceptance check.
