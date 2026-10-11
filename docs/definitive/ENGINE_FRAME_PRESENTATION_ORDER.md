# Present the current frame in the current window tick

The engine responsiveness audit requires the completed frame to reach native presentation in the same window tick. The SDL window previously swapped its buffers before dispatching `onDraw`. `RenderEngine` writes the current frame's final screen quad to the default framebuffer during that callback. Consequently, its new frame waited until the next window tick's swap.

## Required behavior

- Run the complete draw callback before calling `SDL_GL_SwapWindow`, on the existing platform thread.
- Keep exactly one presentation attempt per successful window draw.
- Preserve current input ownership: cinematic/legacy callbacks clear window-owned edges after presentation; simulation callbacks consume edges at their existing gameplay-tick boundary.
- Retained simulation frames and paused frames still draw and present each window tick without adding simulation steps.
- Queued callback handoffs use the current owner selected by the render callback; do not duplicate gameplay or movie steps.
- A draw exception propagates before any attempt to present its incomplete frame or clear its pending window input.

The implementation changes only the ordering in `SdlWindow.tick`. It does not change simulation rates, frame caps, swap intervals, renderer resources, lighting, artwork, or the platform scheduler. It follows [SDL's documented draw-then-swap sequence](https://wiki.libsdl.org/SDL3/SDL_GL_SwapWindow). `NoopWindow` remains a headless backend without a native presentation operation.

## Feedback loop and regression scope

`deliveryTest --tests legend.definitive.pacing.WindowPresentationTest` executes four isolated child-JVM scenarios: cinematic frames, zero-step/paused retained simulation, callback handoffs in both directions, and a controlled draw failure. All four fail with the unchanged baseline `cc3cf7cba479d1388591352ed503ebf35a33e663` and pass after the ordering change.

The fixture invokes the actual private `SdlWindow.tick`, the real window event dispatcher, and the complete production renderer callback. Its no-op graphics backend tracks framebuffer binding and only the final screen-quad draw; the same quad's intermediate SMAA passes are excluded. It checks draw counts, simulation/cinematic step counts, handoffs and input cleanup ownership.

No SDL window, graphics context, video subsystem, audio subsystem or application is initialized. The fixture constructs only the Java `Window` base using a Java25 reflection facility and bypasses every native SDL constructor. In the isolated child only, LWJGL pointer checks are disabled so the real SDL swap function can reject its absent window safely. That function's native error is a call-order marker. The error must be absent during the final screen draw and present after the window tick; SDL subsystem state remains zero throughout. Production pointer checks remain enabled and production constructors are unchanged. [SDL validates the window before native presentation](https://github.com/libsdl-org/SDL/blob/release-3.4.2/src/video/SDL_video.c#L5209).

Two initial fixture issues were corrected before freezing evidence: distinguishing SMAA's offscreen reuse of the screen quad, and advancing the deterministic clock to the first deadline after changing the simulation rate. The final fixture was replayed against the unchanged baseline and reproduced the expected order failure in all four scenarios. These corrections did not require production renderer changes.

## Acceptance boundaries

Source and actual-call-order evidence establish that the completed frame no longer waits for the following window tick inside this engine. One presentation interval is nominally 16.7ms at60Hz or 25ms at40Hz; these are interval calculations, not measured device latency improvements. The fixture's native swap deliberately fails because it has no window. It does not validate actual OpenGL presentation, GPU queues, compositor buffering, tearing, physical controller response, frame pacing or Steam Deck performance.

Physical Steam Deck presentation and responsiveness still need verification alongside the existing battle/field rendering, head consistency and campaign gates. This increment does not claim to resolve those reported visual problems.
