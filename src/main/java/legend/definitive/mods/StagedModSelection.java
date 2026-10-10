package legend.definitive.mods;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** A menu's local selection; displaying managed defaults never edits the campaign selection. */
public final class StagedModSelection {
  private final Set<String> requested;
  private final Set<String> original;
  private final Set<String> displayed;
  private final Map<String, Boolean> choices = new HashMap<>();

  public StagedModSelection(final Set<String> requested, final Set<String> installed) {
    this.requested = requested;
    this.original = Set.copyOf(requested);
    this.displayed = ManagedModProfile.effective(requested, installed);
  }

  public boolean isEnabled(final String id) {
    return ManagedModProfile.isSelectable(id) && this.choices.getOrDefault(id, this.displayed.contains(id));
  }

  public void setEnabled(final String id, final boolean enabled) {
    if(!ManagedModProfile.isSelectable(id)) return;
    if(enabled == this.displayed.contains(id)) {
      this.choices.remove(id);
      // Returning to the displayed default restores the exact original stored membership.
      if(this.original.contains(id)) this.requested.add(id);
      else this.requested.remove(id);
    } else {
      this.choices.put(id, enabled);
      if(enabled) this.requested.add(id);
      else this.requested.remove(id);
    }
  }

  public Map<String, Boolean> choices() {
    return Map.copyOf(this.choices);
  }

  public boolean hasChanges() {
    return !this.choices.isEmpty();
  }

  /** A failed write leaves the menu staged, so it can be retried or cancelled. */
  public void accept() throws IOException {
    ManagedModProfile.recordSelections(this.choices);
  }
}
