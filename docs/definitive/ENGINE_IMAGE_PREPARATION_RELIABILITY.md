# Image preparation failures must settle loading

The rendering reliability audit requires accepted background image preparation
to reach a terminal result when its worker fails. Optional preparation must not
leave a field loading stage waiting for work that has already stopped.

At baseline `e5c1ebab3ac8c726c6423c34980280b220636421`, both image workers let
`Error` escape without settling their returned futures. `PngAssets` catches only
`Exception`; `NativeEnvironmentImages` catches only `RuntimeException`.
Minimal actual-worker feedback reproduces the pending result in both. PNG
optional hints wait until their existing five-second timeout; the actual retail
native preparation chain has no equivalent timeout and remains pending.

## Required behavior and change

- A failure inside an accepted image worker completes its future exceptionally,
  retaining the original throwable and suppressed resource-close failures.
- PNG streams and decode leases retire before failure completion is observed.
- The actual retail optional-preparation handler completes its existing fallback
  and leaves ordinary image preparation selected after a worker failure.
- Existing runtime-failure handling, healthy decoding, transparency/STP pixels,
  bounded memory ownership and subsequent worker operations remain intact.

Only the two worker exception boundaries change to `Throwable`. No caller,
timeout, thread pool, image/cache budget, source identity, decoded pixel, geometry,
shader, GPU upload or artwork selection is changed. This is asynchronous failure
settlement, not a claim that every failed resource can subsequently be decoded.

## Feedback and regression scope

Ranked hypotheses were an uncaught accepted worker error, queue rejection, and
a caller waiting on another signal. The minimal feedback submits directly to
the real worker and observes its returned future. Failure completion at the
worker boundary removes the pending result without changing queue handling or
the caller.

Run `deliveryTest --tests legend.definitive.rendering.ImagePrewarmFailureTest`.
Seven bounded isolated JVMs cover PNG provider, read and close errors, ordinary
PNG failures, native decode errors, ordinary native failures and actual
`RetailSubmap.prewarmEnvironment` fallback. Five final baseline cases fail with
pending preparation timeouts; two ordinary-failure controls pass. All seven
pass with the correction. The final fixtures are also replayed against the
unchanged baseline, and both original minimal worker reproducers pass fixed.

The retail fixture initializes the actual event/config/state registries and
uses the actual source-selection and worker-preparation handler. A controlled
TIM getter succeeds during source selection and throws on the worker. Two
initial fixture failures omitted the event and engine-state registry setup;
those are fixture errors, not product evidence. The final baseline retail
failure occurs at the expected preparation wait.

PNG cases verify close-before-completion, the original/suppressed failures,
native decoded-memory retirement and exact transparent RGB pixels on a following
healthy decode. Native cases verify a following operation still completes.
No application, SDL window, graphics context, audio output or original game
asset is required.

## Acceptance limits

These controlled Java errors exercise actual worker and fallback ownership.
They do not prove recovery from exhausted process memory, a fatal native crash,
or an actual malformed field scene. Ordinary decoding can still fail if the
resource remains invalid. Native/GPU upload, cold battle entrance frame time,
the reported flicker/missing props/head transitions and physical Steam Deck
or full campaign acceptance remain separate obligations.
