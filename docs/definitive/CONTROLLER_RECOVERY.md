# Controller disconnect recovery

A controller disconnected while a button is held must stop owning that action.
The previous removal path cleared axes but retained button counts and action repeats,
which could also keep the Steam Input keyboard suppression workaround active.

The recovery contract is:

- Track held buttons by SDL device instance and physical button, so duplicate events
  cannot inflate a global count and removing one device cannot clear another device.
- Track digital action sources by binding and device. Keyboard bindings remain independent.
- When removal leaves no held controller button or axis, clear remembered suppressed
  keyboard downs before release callbacks so a later real key-up is accepted. Preserve
  that suppression while another controller still has a held button or axis.
- Remove the disconnected device's digital and axis sources before synchronous release
  callbacks. Cancel its still-owned unconsumed presses and repeats when no other source owns the action.
- Preserve another controller, keyboard binding, or axis that still owns the same action.
- A normal short press/release continues to deliver its initial tap. Duplicate removal
  and an unmatched button-up must not release another device's action.
- A completed ordinary tap remains latched even if the device disconnects before its
  next simulation tick; removal cancels inputs the device still owns.
- Focus cancellation clears physical and logical source bookkeeping. Replacing an
  action's bindings immediately cancels its old logical owners before new bindings are
  installed; unrelated actions are preserved. Full binding resets retain the existing
  deferred action-state reset.

`ControllerDisconnectTest` exercises production state methods with synthetic device IDs
and a no-op window. Existing input/pacing tests cover focus cancellation and tick edges;
QoL and preset tests cover campaign settings and inventory/reward behavior. These checks
require no SDL window, physical controller input, game assets or desktop automation.
They do not establish Steam Input event ordering, handheld readability or physical
reconnection. Device acceptance must still include disconnect while navigating a quantity
prompt, reconnect, simultaneous keyboard/controller input, and separate native-gamepad
and keyboard-emulation Steam Input layouts. The recovery does not identify virtual
keyboard sources or promise suppression of every Steam Input duplicate.

## Keyboard release cases

CTRL-KBD-01: a keyboard down forwarded before controller use must receive its up while
controller buttons or axes are held. CTRL-KBD-02: suppressed repeat downs must not replace
that accepted pair. CTRL-KBD-03: enabling the workaround during a held key preserves its
release. CTRL-KBD-04: a new accepted down replaces an older suppressed-down marker after
ordinary controller release. CTRL-KBD-05: focus cancellation clears accepted-key pairing
along with other window-owned input.

The SDL branch invokes the tested policy for both workaround modes. Pairing describes
forwarded logical scancodes, not authenticated physical keyboards: this workaround cannot
distinguish every Steam Input virtual event from a real key with the same scancode. Physical
ordering and mixed-device behavior remain device acceptance, not headless certification.
