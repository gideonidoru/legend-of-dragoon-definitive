// Definitive accessible file picker, AGPL v3; see LICENSE.
package legend.definitive.manager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Filesystem work stays off the UI thread; every input route changes the same selection model. */
final class TouchFilePicker {
  private static final Map<JFrame, TouchFilePicker> OPEN = new HashMap<>(); // EDT only
  private final JDialog dialog;
  private final DefaultListModel<PickerFiles.Entry> model = new DefaultListModel<>();
  private final PickerFiles.FileList list;
  private final PickerFiles.Requests requests = new PickerFiles.Requests(PickerFiles::entries);
  private final JLabel location = new JLabel();
  private final JButton choose;
  private Path folder;
  private boolean loading;
  private final boolean directories;
  private TouchFilePicker(final JFrame owner, final Path initial, final boolean directories, final java.util.function.Consumer<List<Path>> completed) {
    this.directories = directories;
    this.folder = (initial == null ? home() : initial).toAbsolutePath().normalize();
    this.dialog = new JDialog(owner, directories ? "Choose installation location" : "Choose your disc files", false);
    this.list = new PickerFiles.FileList(this.model, entry -> this.open(entry.path()), this::selectionChanged);
    final JPanel content = ManagerView.surface(new BorderLayout(0, 18)); content.setBackground(ManagerView.PAPER); content.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
    final JPanel header = new JPanel(new BorderLayout(12, 12)); header.setOpaque(false);
    final JPanel navigation = new JPanel(new GridLayout(1, 2, 12, 0)); navigation.setOpaque(false);
    final JButton up = ManagerView.button("↑  Parent folder", false), home = ManagerView.button("Home", false);
    up.addActionListener(e -> { if(this.folder.getParent() != null) this.open(this.folder.getParent()); }); home.addActionListener(e -> this.open(home()));
    navigation.add(up); navigation.add(home); final JPanel title = new JPanel(new BorderLayout(0, 18)); title.setOpaque(false);
    final JLabel heading = new JLabel(directories ? "Choose a folder" : "Choose your discs"); heading.setFont(ManagerView.font(26, true)); heading.setForeground(ManagerView.INK);
    title.add(heading, BorderLayout.NORTH); title.add(navigation, BorderLayout.SOUTH); header.add(title, BorderLayout.NORTH);
    this.location.setFont(ManagerView.font(15, false)); this.location.setForeground(ManagerView.MUTED);
    this.location.setPreferredSize(new Dimension(760, 38)); this.location.setMinimumSize(new Dimension(0, 38));
    header.add(this.location, BorderLayout.SOUTH); content.add(header, BorderLayout.NORTH);
    this.list.setFixedCellHeight(54); this.list.setFont(ManagerView.font(18, false)); this.list.setBackground(Color.WHITE);
    final Point[] dragStart = {null}; final int[] startY = {0}; final boolean[] dragged = {false};
    this.list.addMouseMotionListener(new MouseMotionAdapter() { @Override public void mouseDragged(final MouseEvent e) {
      if(dragStart[0] == null) return;
      final int delta = e.getYOnScreen() - dragStart[0].y;
      if(Math.abs(delta) > 8) {
        dragged[0] = true;
        final JViewport viewport = (JViewport)TouchFilePicker.this.list.getParent();
        viewport.setViewPosition(new Point(0, Math.max(0, Math.min(TouchFilePicker.this.list.getHeight() - viewport.getHeight(), startY[0] - delta))));
      }
    } });
    this.list.addMouseListener(new MouseAdapter() {
      @Override public void mousePressed(final MouseEvent e) { dragStart[0] = e.getLocationOnScreen(); startY[0] = ((JViewport)TouchFilePicker.this.list.getParent()).getViewPosition().y; dragged[0] = false; }
      @Override public void mouseReleased(final MouseEvent e) {
        if(dragged[0]) return;
        final int index = TouchFilePicker.this.list.locationToIndex(e.getPoint());
        if(index >= 0 && TouchFilePicker.this.list.getCellBounds(index, index).contains(e.getPoint())) { TouchFilePicker.this.list.setSelectedIndex(index); TouchFilePicker.this.list.activate(); }
      }
    });
    for(final int key : new int[]{KeyEvent.VK_ENTER, KeyEvent.VK_SPACE}) this.list.getInputMap().put(KeyStroke.getKeyStroke(key, 0), "activate");
    final JScrollPane scroll = new JScrollPane(this.list); scroll.setBorder(ManagerView.roundedBorder()); scroll.getVerticalScrollBar().setPreferredSize(new Dimension(44, 0)); scroll.getVerticalScrollBar().setUnitIncrement(54); content.add(scroll, BorderLayout.CENTER);
    final JPanel footer = new JPanel(new BorderLayout(12, 12)); footer.setOpaque(false);
    final JLabel help = new JLabel(directories ? "Open a folder, then choose Use this location." : "Tap a file to select it. You can choose files across folders."); help.setFont(ManagerView.font(14, false)); help.setForeground(ManagerView.MUTED); footer.add(help, BorderLayout.NORTH);
    final JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0)); actions.setOpaque(false);
    final JButton cancel = ManagerView.button("Cancel", false); cancel.addActionListener(e -> this.dialog.dispose());
    this.choose = ManagerView.button(directories ? "Use this location" : "Use selected files", true);
    this.choose.addActionListener(e -> {
      if(this.loading) return;
      final List<Path> result = directories ? List.of(this.folder) : this.list.chosen();
      this.dialog.setVisible(false); this.dialog.dispose(); if(owner != null) owner.setEnabled(true);
      SwingUtilities.invokeLater(() -> completed.accept(result));
    }); actions.add(cancel); actions.add(this.choose); footer.add(actions, BorderLayout.SOUTH); content.add(footer, BorderLayout.SOUTH);
    this.dialog.setContentPane(content); this.dialog.getRootPane().setDefaultButton(this.choose);
    this.dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel"); this.dialog.getRootPane().getActionMap().put("cancel", new AbstractAction() { @Override public void actionPerformed(final ActionEvent e) { TouchFilePicker.this.dialog.dispose(); } });
    this.dialog.addWindowListener(new WindowAdapter() { @Override public void windowClosed(final WindowEvent e) { TouchFilePicker.this.requests.close(); OPEN.remove(owner, TouchFilePicker.this); if(owner != null) owner.setEnabled(true); } });
    this.dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    this.dialog.setSize(880, 640); this.dialog.setLocationRelativeTo(owner); this.open(this.folder);
  }
  private static Path home() { return Path.of(System.getProperty("user.home")); }
  static void choose(final JFrame owner, final Path initial, final boolean directories, final java.util.function.Consumer<List<Path>> completed) {
    if(!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Open pickers on the UI thread");
    final var existing = OPEN.get(owner);
    if(existing != null && existing.dialog.isDisplayable()) { existing.list.requestFocusInWindow(); return; }
    final var picker = new TouchFilePicker(owner, initial, directories, completed); OPEN.put(owner, picker);
    if(owner != null) owner.setEnabled(false); picker.dialog.setVisible(true);
  }
  private void selectionChanged() {
    final int count = this.list.chosen().size(); this.choose.setEnabled(!this.loading && (this.directories || count > 0));
    if(!this.directories) this.choose.setText(count == 0 ? "Use selected files" : "Use " + count + " " + (count == 1 ? "file" : "files"));
  }
  private void location(final Path path, final String status) {
    final String full = path.toString(); this.location.setText(ManagerView.locationLabel(full) + (status.isEmpty() ? "" : " · " + status));
    this.location.setToolTipText(full); this.location.getAccessibleContext().setAccessibleName(full); this.location.getAccessibleContext().setAccessibleDescription(status);
  }
  private void open(final Path target) {
    final Path requested = target.toAbsolutePath().normalize();
    this.loading = true; this.model.clear(); this.location(requested, "Loading…"); this.selectionChanged();
    this.requests.open(requested, this.directories, entries -> {
      this.folder = requested; this.loading = false; entries.forEach(this.model::addElement); this.location(this.folder, ""); this.selectionChanged();
      if(!entries.isEmpty()) this.list.setSelectedIndex(0); this.list.requestFocusInWindow();
    }, failure -> {
      this.loading = false; this.location(this.folder, "Could not open folder. Try Home or Parent folder."); this.choose.setEnabled(false);
    });
  }
}
