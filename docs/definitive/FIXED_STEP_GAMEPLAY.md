# Gameplay independent of presentation

## Scope and acceptance contract

The main game callback currently advances only when the platform draws a frame. A forced external display cap below a state's required 20, 30 or 60 Hz therefore slows gameplay. The isolated fixed-step implementation must preserve those gameplay rates at speeds 1 through 16 independently of presentation callbacks at 15, 30, 40, 60, 120 Hz or irregular uncapped intervals, within an explicit bounded recovery policy. It must not change audio pitch/sample clocks or reinterpret cinematic, loading and intro callbacks as gameplay updates.

Required boundaries: same-rate assignments preserve phase; changed rates and callbacks discard the previous domain's backlog; pauses and suspend recovery cannot create an unbounded burst; callback changes during catch-up stop it immediately; zero-step presentations retain a complete frame and its objects; intermediate ticks replace queues rather than accumulating them; each tick keeps native GPU start/end, CLUT state, scripts and animation processing; pressed/repeat edges are consumed once while held/axis state remains available. Existing custom artwork and mod event callbacks remain under their original owners.

## Implementation

`Scus94491BpeSegment.bindRendererEvents` explicitly registers the main game loop with `RenderEngine.setSimulationCallback`. The returned callback carries that ownership when films save and restore it. Other uses of `setRenderCallback` keep their existing presentation cadence. Domain/cadence handoffs reset the gameplay clock; unchanged rate assignments do not. The graphics frame-skip choice keeps base 20/30/60 Hz presentation during fast-forward while gameplay and input retain the multiplied rate. Disabling frame skipping requests the multiplied presentation rate as before.

The monotonic clock accumulates elapsed gameplay debt independently of display callbacks. Each simulation tick still invokes the entire authoritative game loop, including engine state updates, menus, script VM/tickers/renderers, models, sound triggers, textboxes, transitions and native GPU commands. This is a clock/lifetime separation, not a rewrite of individual game-state animation logic.

Before a new tick, the renderer discards previous queued draws and retires objects/textures marked for deletion. CLUT animation collection starts afresh for every tick. Only the final queue survives a catch-up group, and framebuffer history advances once for that presentation, not for discarded intermediate ticks. A presentation with no due tick reuses the retained queue and image-history position; it does not retire its objects or consume input. Native legacy display/VRAM modes redraw their retained display without executing another GPU simulation tick. Existing source-bound UI preparation and shader/material paths remain in place.

Gameplay vblank accounting advances per simulation tick, preserving scripted fades and RNG seeding when presentation is capped. Presentation FPS remains a separate measured value. Polling and input events remain platform-owned; gameplay consumes pressed/repeat edges after each tick and leaves held buttons/axes intact. Cinematic and loading callbacks retain their original window-level input clearing.

## Bounded recovery and diagnostics

At most 250 ms of gameplay debt is retained. Longer stalls discard excess elapsed time rather than replaying seconds of actions on resume. One presentation may execute at most 256 ticks and spends at most 50 ms on catch-up before starting another tick; a single authoritative tick is never interrupted, so a tick that itself exceeds 50 ms can exceed that work budget. Unprocessed bounded debt carries to the next presentation. Sustained CPU overload can still fall behind; the limits deliberately protect responsiveness instead of promising impossible throughput.

`RenderEngine.simulationTiming()` exposes rate, pending debt, completed ticks, cumulative discarded elapsed time and whether the last catch-up group exhausted its work budget. Tests and diagnostics can therefore distinguish a display cap, an explicit fast-forward multiplier, suspend recovery and CPU overload. Intentional pause/callback/rate resets do not count as overload loss.

## Evidence and remaining gates

The deterministic clock matrix covers all 48 state/speed combinations at all six presentation schedules; separate tests cover repeated assignments, irregular intervals, callback/rate handoffs, negative elapsed time, monotonic wrap, long suspend and measured work-budget exhaustion. Production renderer component tests cover retained queues/object retirement, final-only CLUT state, one history advance per presentation, callback marker restoration, pause/manual stepping and one-shot press/repeat consumption with held-axis preservation. Existing actual script/model and audio-clock regressions remain required.

The implementation is isolated development work until independent source reviews and all repository/native checks pass. These fixtures do not prove physical Steam Deck behavior, a full campaign playthrough, every effect's feedback appearance or gameplay input feel. Enhanced films retain their sample-driven owner. Original fallback films still consume one frame per native 15 Hz presentation callback; an external cap below 15 Hz is a separate remaining legacy-film boundary, rather than a result established by the gameplay clock tests. No engine-wide final quality claim follows from this component work.
