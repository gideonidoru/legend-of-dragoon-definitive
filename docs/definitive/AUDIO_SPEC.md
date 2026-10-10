# Faithful audio playback and lossless XA import

Preserve the original score, soundbanks, stereo balance, resampling and XA gain. Do not add AI enhancement, widening or new effects, and leave the existing enhanced music defaults unchanged.

- Import prerecorded XA audio as exact-length 48 kHz, signed 16-bit PCM WAV after the existing decoder/resampler; omit the 128 kbps Opus encoding step. This avoids additional lossy compression, not source restoration.
- Refresh only incomplete legacy XA archives when preparing existing extracted data. Preserve the unpacker version and other game files, saves and settings. Keep old Opus files usable, including when rolling back an engine version. Prefer WAV at playback when present.
- Decode the actual returned sample count, queue a short final buffer at its exact length, drain it before stopping, handle malformed input safely and release native decoder memory on replacement/stop.
- Flush old queued sound when starting another recording. On audio-context recreation, resume at the played position rather than discarding queued but unplayed samples. Retain existing audio-device polling and music synthesis.
- Prove behavior with synthetic PCM/Opus inputs, actual OpenAL null-output buffer checks and an isolated real-disc preparation check. Private original recordings remain local and are not bundled or uploaded.
- Deliver reviewed code to main and the normal public installer. Physical Deck sleep/resume, Bluetooth/headphone routing and loaded-scene listening remain device acceptance, not headless test claims.
