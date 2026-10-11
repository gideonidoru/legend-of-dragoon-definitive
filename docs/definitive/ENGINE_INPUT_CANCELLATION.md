# Cancellation must reach legacy gameplay input

The engine reliability review requires cancelled controls to disappear from
every gameplay consumer before another script tick. Ordinary short taps must
remain deliverable. Controller removal must preserve keyboard, axis and other
controller ownership of the same action. Rebinding must cancel only its action;
a deferred global reset must clear all actions when the input pump applies it.

## Confirmed failure

At accepted baseline `ad1a234d94b4397df681c7fd196581cf3a303a88`, real platform
bookkeeping cancels focus/disconnect edges but emits only ordinary release.
Battle and SMap deliberately retain pressed/repeat masks after ordinary release,
so a cancelled action remains available to `GameVarParam` script consumers.
Binding replacement clears platform ownership without any gameplay notification,
leaving held masks too. The actual-source audit harness fails 14 of 40 assertions;
ordinary tap and platform state controls pass. This is an executable source
defect, not a measured physical input latency result.

## Contract and implementation

Platform cancellation is an additive synchronous per-action notification.
Registration/removal uses a snapshot-safe observer collection. It belongs to
platform action ownership and works without an active native window. Gameplay
registers its current-engine-state forwarding alongside existing press/release
forwarding. Battle and SMap cancel all three masks using the same action mapping
as normal press/release; their ordinary release still clears only held input.

Focus loss snapshots every known action, including completed taps and edges
already consumed by the platform but still held in the VM. All platform owners
are cleared before cancellation notification; existing held-action releases
follow after all cancellation notifications. The native focus-loss handler also
cancels immediately for the last active window when inactive input is disabled,
so a subsequent focus gain in the same event pump cannot undo the cancellation.
Losing focus on another window does not cancel the active owner's input.

Disconnect notifies only affected actions that lost their final live source;
completed taps and surviving sources keep their previous behavior. Per-action
binding replacement clears its sources and notifies immediately. Global reset
retains its deferred boundary, clears the pressed set as well as action/source
tables, and notifies from an immutable action snapshot. Focus cancellation keeps
known action entries; a global reset discards them as before.

## Regression checks and limits

`deliveryTest --tests legend.definitive.input.LegacyInputCancellationTest`
uses the real platform, actual Battle/SMap consumers and actual VM parameters
3, 4 and 26. Its fixture forwards the same callbacks as gameplay. It covers both
battle masks and all seven field masks, ordinary taps, run-by-default interpretation, completed/consumed taps
on focus loss, completed taps on disconnect, remaining controller/keyboard/axis
owners, real `InputBindings.overwriteBindings`, deferred global reset, release
callback ordering and reentrant listener removal/binding reset.

The manual field quick-save handler retains its original guarded branch. An
additional control reaches the actual eligibility gate for a non-repeat save
press, and excludes repeats, cancellation and loading scenes, without writing a
player save. It fails on the initial refactor that accidentally omitted this
special action and passes after restoration. Independent review caught that
omission before integration.

The first permanent run before backend cancellation dispatch fails four of six
cases at the real VM pressed assertion. Adding dispatch makes those cases pass;
the expanded ten-case suite includes the additional ownership/reset/save controls.
`ControllerDisconnectTest` and `EnginePacingTest` retain the existing contracts.
These tests call production cancellation/reset helpers without initializing SDL.
Each case runs in a bounded isolated JVM because the game's registries are process
singletons; the fixtures cannot overwrite or retain another delivery test's entries.
The native event-pump focus-loss/gain ordering, real controller removal and Deck
Addition responsiveness remain device acceptance checks. Movie clocks, frame
presentation order and rendering quality are separate review items.
