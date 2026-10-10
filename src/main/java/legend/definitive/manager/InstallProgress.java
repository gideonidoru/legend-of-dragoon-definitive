// Definitive installation progress (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

/** Completed work within named phases; unknown totals never invent a completion percentage. */
@FunctionalInterface
public interface InstallProgress {
  InstallProgress NONE = update -> { };
  record Update(String phase, String detail, int startPercent, int endPercent, long completed, long total) {
    public int percent() {
      return this.total <= 0 ? this.startPercent : this.startPercent + (int)((this.endPercent - this.startPercent) * Math.min(1.0, (double)this.completed / this.total));
    }
  }
  void report(Update update);
  default void phase(final String phase, final String detail, final int percent) { this.report(new Update(phase, detail, percent, percent, 0, 0)); }
  default void bytes(final String phase, final int start, final int end, final long completed, final long total) {
    final String detail = String.format(java.util.Locale.ROOT, "%.1f MB%s", completed / 1048576.0, total > 0 ? String.format(java.util.Locale.ROOT, " of %.1f MB", total / 1048576.0) : " downloaded");
    this.report(new Update(phase, detail, start, end, completed, total));
  }
}
