# Required battle scenery completion

The engine reliability audit requires battle scenery preparation to publish only
to its current battle, and required failures to reach the engine error handler.
Actual battle caller probes at accepted main
`291191bc4a47e70c44178e6b394d9d02ec364579` reproduced missing stage/model directories
and malformed TIM/model files being logged without reaching that handler. A
successful completion could also upload an old palette and change scene state
after the current engine had been replaced, while the preloaded owner still
matched. These are independently reproduced loading defects; they are not a
confirmed explanation for disappearing scenery during attacks on Steam Deck.

The existing paired model/texture preparation and next-frame texture-animation
delay remain. Both success and failure now check the current engine, stage load
generation and preloaded scene owner on the render thread. An active required
failure is queued to that owner and throws an error identifying its DRGN0 stage
directory with the original read or parser cause. Obsolete failures and successes
cannot affect a replaced engine, preloaded owner, superseding load or deallocated
stage. Optional EnvHD artwork retains its existing native fallback. The change
does not repair damaged assets or add automatic return-to-title recovery.

`BattleStageLoadingReliabilityTest` runs eleven bounded isolated JVM cases with
synthetic files, the actual Battle caller, Loader, native TIM/model parsers and
production render callback. Its unchanged-main replay fails five cases and passes
six controls. Forwarding failures alone fixes the four required-load cases; two
additional cases then show that both successful completion and failure need the
current-engine guard. The final correction passes all eleven and the existing
stage/submap completion checks.

The healthy TIM fixture verifies render-owned palette upload and all palette
backup endpoints; the obsolete-engine fixture verifies that its old palette is
not uploaded. Valid empty native stage records still complete on the render
owner. Other controls retain generation, preloaded-owner and deallocated-stage
rejection. No original game files, native window or audio output are used.

This increment establishes the required stage completion contract. It does not
qualify full battle entry timing, controller-script preparation, HD/native mesh
rendering under camera movement, Steam Deck drivers or the reported intermittent
attack scenery loss. Those remain in the wider engineering and device audit.

## Deterministic completion scheduling

Hosted macOS build `38187087378` exposed a timing assumption in the original
stale-engine fixture. Its first renderer draw started the two directory reads
while the battle was still current. If those reads failed before the completion
handler was attached, that handler queued its error on the drawing thread.
The renderer could drain that error during the same draw, before the fixture
replaced the engine. Reporting that active failure was correct; the test had
not yet established its intended obsolete-owner scenario. An unchanged-source
replay at `d65a7f1563a88fb7e2f782c49e51c8b87fd31ae3` reproduced this failure in
three of 48 isolated child JVMs.

The stage fixtures now hold every actual loader worker behind a bounded latch
until the initial draw returns. They then release the workers and await actual
loader retirement before checking publication or replacing the engine/owner.
This explicitly orders the intended worker completion and second renderer draw;
no production executor, renderer queue, ownership guard or error policy changes.
The child JVM uses two reported processors to bound its real worker pool.
Barriers release in `finally`, all waits are bounded, and asynchronous blocker
failures remain observable. Existing active read/parser failure, healthy palette,
empty stage, generation, owner and deallocation assertions remain in place.

A private negative control removes only the required-failure ownership guard
from a temporary compiled Battle class. The controlled stale-engine, stale-owner
and retired-stage fixtures must then fail, while their actual loader errors are
still reported. The altered class is never shipped. This distinguishes a
scheduled regression check from a waiver of obsolete-completion behavior.
