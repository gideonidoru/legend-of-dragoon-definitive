# Required battle controller preparation

The battle controller (`DRGN1/401`) must publish its script, camera choices and
readiness flag on the render owner. A required read or parser failure must reach
the engine's existing render error handler instead of leaving battle entry waiting.
Only the current battle, preloaded owner and controller request may publish or fail.
Retiring a battle invalidates its controller request. Controller requests have a
separate generation from stage artwork loads, which can change during combat.

## Reproduced defect and correction

Against unchanged main `abadb46a8c767336a83198928b44a9206c2280b0`, the actual
`Battle.loadStageDataAndControllerScripts` caller produced eight failing cases in
the expanded 24-case isolated fixture. Missing and malformed controllers failed to
reach the render error handler. Successful callbacks published script state,
camera choices, the random-number seed and readiness on the loader worker. Replaced
engine/owner, destroyed battle and superseded request cases could still publish.
The sixteen passing controls include the eleven existing scenery-loading cases.

Forwarding errors alone corrected two cases but left six failures. Moving the
successful callback to the render queue and guarding both success and failure by
engine identity, owner identity and request generation corrects those boundaries.
The same required-load failure helper serves scenery and controller preparation.
The controller's existing script and camera calculations remain unchanged, and
its readiness flag remains the final successful publication.

## Verification contract

The fixture invokes the actual private battle methods with synthetic files and
the production render callback through a headless window. Each scenario runs in
an isolated child JVM with bounded execution and worker retirement. It covers:

- Missing and malformed required controller files reaching the render handler.
- Successful loads making no script, readiness or random-state changes before rendering.
- Existing camera selections and random-state advancement occurring exactly once.
- Current controller readiness advancing the existing viewport gate to stage 5,
  preserving vsync mode 3 and the expected readiness flags.
- Replaced engine/owner, destroyed battle and superseded requests being ignored.
- A newer missing controller failing even if an older successful request is queued.
- The existing synchronous player-script failure and all scenery-loading controls.

The focused controller/scenery/submap run passes 48 cases. The original minimal
caller probe also passes: missing data causes a render failure with no readiness
publication, while healthy data publishes readiness only after rendering.
Private baseline logs, intermediate failure-only results, XML and probe outputs
are retained outside the public repository. Full integration and source bindings
are recorded with the reviewed increment.

## Remaining acceptance

This establishes controller preparation ownership and required-load failure
delivery. It does not establish Steam Deck battle-entry performance, attack-camera
ground/prop visibility, field-head consistency, or full-scene rendering quality.
Those remain separate open requirements, as do full campaign and device checks.
No original game data, save files or private diagnostic payloads are included.
