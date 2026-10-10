// Definitive asynchronous file selection model, AGPL v3; see LICENSE.
package legend.definitive.manager;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Cached filesystem facts and one selection action for touch, keys and accessibility. */
final class PickerFiles {
  record Entry(Path path, boolean directory) {
    String name() { final Path name = this.path.getFileName(); return name == null ? this.path.toString() : name.toString(); }
  }
  @FunctionalInterface interface Loader { List<Entry> load(Path folder, boolean directories) throws Exception; }
  private PickerFiles() { }
  static List<Entry> entries(final Path folder, final boolean directories) throws java.io.IOException {
    try(final var files = Files.list(folder)) {
      final var entries = new ArrayList<Entry>();
      for(final Path path : files.toList()) {
        final boolean directory = Files.isDirectory(path);
        if(directory || !directories && path.getFileName().toString().matches("(?i).*\\.(bin|iso|zip|rar|7z)")) entries.add(new Entry(path.toAbsolutePath().normalize(), directory));
      }
      entries.sort(Comparator.comparing(Entry::directory).reversed().thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
      return List.copyOf(entries);
    }
  }

  /** Cancellation and generation checks prevent late results from replacing a newer folder. */
  static final class Requests implements AutoCloseable {
    private final Loader loader;
    private long generation;
    private boolean closed;
    private SwingWorker<List<Entry>, Void> worker;
    Requests(final Loader loader) { this.loader = loader; }
    void open(final Path path, final boolean directories, final Consumer<List<Entry>> loaded, final Consumer<Throwable> failed) {
      if(!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Request folders on the UI thread");
      final long request = ++this.generation;
      if(this.worker != null) this.worker.cancel(true);
      this.worker = new SwingWorker<>() {
        @Override protected List<Entry> doInBackground() throws Exception { return Requests.this.loader.load(path, directories); }
        @Override protected void done() {
          if(Requests.this.closed || request != Requests.this.generation || this.isCancelled()) return;
          try { loaded.accept(this.get()); }
          catch(final Exception failure) { failed.accept(failure.getCause() == null ? failure : failure.getCause()); }
        }
      };
      this.worker.execute();
    }
    @Override public void close() { this.closed = true; ++this.generation; if(this.worker != null) this.worker.cancel(true); }
  }

  static final class FileList extends JList<Entry> {
    private final Set<Path> selected = new LinkedHashSet<>();
    private final Consumer<Entry> openDirectory;
    private final Runnable selectionChanged;
    FileList(final DefaultListModel<Entry> model, final Consumer<Entry> openDirectory, final Runnable selectionChanged) {
      super(model); this.openDirectory = openDirectory; this.selectionChanged = selectionChanged;
      this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      this.setCellRenderer(this::row);
      this.getAccessibleContext().setAccessibleName("Folders and disc files");
      this.getAccessibleContext().setAccessibleDescription("File checkboxes select files for import; folders open when activated.");
      this.getActionMap().put("activate", new AbstractAction() { @Override public void actionPerformed(final java.awt.event.ActionEvent event) { FileList.this.activate(); } });
    }
    List<Path> chosen() { return List.copyOf(this.selected); }
    void activate() { final Entry entry = this.getSelectedValue(); if(entry == null) return; if(entry.directory()) this.openDirectory.accept(entry); else this.toggle(entry); }
    private void toggle(final Entry entry) {
      final boolean wasSelected = this.selected.remove(entry.path()); if(!wasSelected) this.selected.add(entry.path());
      this.selectionChanged.run();
      this.getAccessibleContext().firePropertyChange(javax.accessibility.AccessibleContext.ACCESSIBLE_STATE_PROPERTY, wasSelected ? javax.accessibility.AccessibleState.CHECKED : null, wasSelected ? null : javax.accessibility.AccessibleState.CHECKED);
      this.repaint();
    }
    private Component row(final JList<? extends Entry> list, final Entry entry, final int index, final boolean focused, final boolean cellFocus) {
      final JPanel row = new JPanel(new BorderLayout(14, 0)); row.setOpaque(true);
      row.setBackground(focused ? new Color(0xdfe9df) : index % 2 == 0 ? Color.WHITE : new Color(0xf8f9f5));
      row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(cellFocus ? ManagerView.GREEN : row.getBackground(), 2), BorderFactory.createEmptyBorder(6, 14, 6, 14)));
      row.getAccessibleContext().setAccessibleName(entry.name());
      row.getAccessibleContext().setAccessibleDescription(entry.path().toString());
      if(!entry.directory()) {
        final JCheckBox check = new JCheckBox(); check.setSelected(this.selected.contains(entry.path())); check.setOpaque(false); check.setFocusable(false); check.setPreferredSize(new Dimension(28, 28));
        check.getAccessibleContext().setAccessibleName(entry.name());
        check.addActionListener(event -> this.toggle(entry));
        row.add(check, BorderLayout.WEST);
      }
      final JLabel label = new JLabel(entry.name()); label.setFont(list.getFont()); label.setForeground(ManagerView.INK); label.setToolTipText(entry.path().toString()); row.add(label, BorderLayout.CENTER);
      if(entry.directory()) { final JLabel folder = new JLabel("Folder"); folder.setForeground(ManagerView.MUTED); folder.setFont(ManagerView.font(14, false)); row.add(folder, BorderLayout.EAST); }
      return row;
    }
  }
}
