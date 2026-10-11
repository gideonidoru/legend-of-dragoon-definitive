# Settle work discarded by forced executor shutdown

The engine reliability audit requires accepted asynchronous operations to reach
a terminal result when their actual queued work is discarded during forced
shutdown. A retired executor must not leave dependent loading state permanently
pending. The updater uses this forced shutdown after its graceful retirement
deadline; the engine's ordinary close still uses graceful shutdown.

At baseline `bcb612f5ad0c3df5cfbe5b08593596ecae20d062`, the real executor can
retire successfully while queued runnable and supplier futures remain pending.
The same failure affects all six asynchronous Loader APIs: queued file, batch
and directory reads retain their completion and loading counts. Cancelling the
consumer alone does not mean the queued I/O has settled.

## Required behavior

- Forced shutdown interrupts started work using the existing executor policy.
- Each discarded queue entry reaches cancellation once and notifies dependent
  completion handlers, without executing its operation.
- Started operations retain responsibility for their actual completion; this
  change must not claim interrupted work has finished before it really has.
- Graceful shutdown runs accepted queued operations normally.
- Preserve runnable/supplier results, async failure wrapping, null argument
  validation, submission rejection and existing consumer cancellation behavior.
- Loader counters retire when actual queued work is discarded, independently of
  whether its consumer already cancelled.

The private executor now receives entries that retain their own completion
future. `shutdownNow()` settles only entries returned by that executor as
discarded. No separate pending-work registry, executor, worker count, queue
policy, timeout or loading callback changes are introduced. Failure completion
preserves `CompletableFuture` async `CompletionException` wrapping.

## Feedback and acceptance

The minimal real-pool harness occupies two workers, queues both operation types,
then forces shutdown. Its baseline confirms executor retirement and no queued
execution before failing on the two pending futures. Ranked alternatives were
discarded entries without settlement, a queued operation starting and stalling,
and workers failing to retire. The execution marker and retirement check rule
out the latter two.

Run `deliveryTest --tests legend.definitive.loading.AsyncShutdownTest`. Six
isolated child-JVM scenarios exercise the actual executor and Loader without
game data, windows or audible output. A child-only processor-count setting
makes its two-worker barrier deterministic. Two baseline scenarios fail on
forced queue/loader settlement; four controls cover graceful drain, normal
results and failures, consumer cancellation, and rejection/null arguments.
The final fixtures are replayed against the unchanged baseline. Children have
bounded deadlines and retire their real executor before exit.

This establishes discarded-work settlement. It does not impose a termination
guarantee on started operations that ignore interrupts, bound the existing queue,
measure frame time or prove physical Steam Deck close/update/suspend behavior.
Native I/O and full campaign/rendering acceptance remain separate obligations.
