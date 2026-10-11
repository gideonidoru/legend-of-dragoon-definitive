# Controller disconnect recovery

A controller disconnected while a button is held must stop owning that action.
The previous removal path cleared axes but retained button counts and action repeats,
which could also keep the Steam Input keyboard suppression workaround active.

The recovery contract is:

- Track held buttons by SDL device instance and physical button, so duplicate events
  cannot inflate a global count and removing one device cannot clear another device.
- Track digital action sources by binding and device. Keyboard bindings remain independent.
- Remove the disconnected device's digital and axis sources before synchronous release
  callbacks. Cancel its unconsumed presses and repeats when no other source owns the action.
- Preserve another controller, keyboard binding, or axis that still owns the same action.
- A normal short press/release continues to deliver its initial tap. Duplicate removal
  and an unmatched button-up must not release another device's action.
- Focus cancellation clears physical and logical source bookkeeping. Binding resets
  clear old logical owners with the existing deferred action-state reset.

`ControllerDisconnectTest` exercises production state methods with synthetic device IDs
and a no-op window. Existing input/pacing tests cover focus cancellation and tick edges;
QoL and preset tests cover campaign settings and inventory/reward behavior. These checks
require no SDL window, physical controller input, game assets or desktop automation.
They do not establish Steam Input event ordering, handheld readability or physical
reconnection. Device acceptance must still include disconnect while navigating a quantity
prompt, reconnect, simultaneous keyboard/controller input, and separate native-gamepad
and keyboard-emulation Steam Input layouts. The recovery does not identify virtual
keyboard sources or promise suppression of every Steam Input duplicate.
