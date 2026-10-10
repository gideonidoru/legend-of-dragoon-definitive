// Definitive accessible file picker (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Large rows, explicit selection, no Ctrl-click requirement. Keyboard/gamepad actions share the same path. */
final class TouchFilePicker {
  private final JDialog dialog;
  private final JList<Path> list = new JList<>();
  private final DefaultListModel<Path> model = new DefaultListModel<>();
  private final Set<Path> selected = new LinkedHashSet<>();
  private final JLabel location = new JLabel();
  private final JButton choose;
  private Path folder;
  private final boolean directories;
  private List<Path> result = List.of();
  private TouchFilePicker(final JFrame owner, final Path initial, final boolean directories, final java.util.function.Consumer<List<Path>> completed) {
    this.directories = directories; this.folder = initial != null && Files.isDirectory(initial) ? initial : Path.of(System.getProperty("user.home"));
    this.dialog = new JDialog(owner, directories ? "Choose installation location" : "Choose your disc files", false);
    final JPanel content = new JPanel(new BorderLayout(0, 18)); content.setBackground(ManagerView.PAPER); content.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
    final JPanel header = new JPanel(new BorderLayout(12, 12)); header.setOpaque(false);
    final JPanel navigation = new JPanel(new GridLayout(1, 2, 12, 0)); navigation.setOpaque(false);
    final JButton up = ManagerView.button("↑  Parent folder", false), home = ManagerView.button("Home", false);
    up.addActionListener(e -> { if(this.folder.getParent() != null) this.open(this.folder.getParent()); }); home.addActionListener(e -> this.open(Path.of(System.getProperty("user.home"))));
    navigation.add(up); navigation.add(home); final JPanel title = new JPanel(new BorderLayout(0, 18)); title.setOpaque(false);
    final JLabel heading = new JLabel(directories ? "Choose a folder" : "Choose your discs"); heading.setFont(ManagerView.font(26, true)); heading.setForeground(ManagerView.INK);
    title.add(heading, BorderLayout.NORTH); title.add(navigation, BorderLayout.SOUTH); header.add(title, BorderLayout.NORTH);
    this.location.setFont(ManagerView.font(15, false)); this.location.setForeground(ManagerView.MUTED); header.add(this.location, BorderLayout.SOUTH); content.add(header, BorderLayout.NORTH);
    this.list.setModel(this.model); this.list.setFixedCellHeight(54); this.list.setFont(ManagerView.font(18, false)); this.list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); this.list.setBackground(Color.WHITE);
    this.list.setCellRenderer((list, path, index, focused, cellFocus) -> {
      final JLabel label = new JLabel((Files.isDirectory(path) ? "›   " : this.selected.contains(path) ? "✓   " : "○   ") + path.getFileName());
      label.setFont(list.getFont()); label.setOpaque(true); label.setForeground(ManagerView.INK); label.setBackground(focused ? new Color(0xdfe9df) : index % 2 == 0 ? Color.WHITE : new Color(0xf8f9f5)); label.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16)); return label;
    });
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
    this.list.addMouseListener(new MouseAdapter() { @Override public void mousePressed(final MouseEvent e) { dragStart[0] = e.getLocationOnScreen(); startY[0] = ((JViewport)TouchFilePicker.this.list.getParent()).getViewPosition().y; dragged[0] = false; } });
    this.list.addMouseListener(new MouseAdapter() { @Override public void mouseReleased(final MouseEvent e) { if(dragged[0]) return; final int index = TouchFilePicker.this.list.locationToIndex(e.getPoint()); if(index >= 0 && TouchFilePicker.this.list.getCellBounds(index, index).contains(e.getPoint())) { TouchFilePicker.this.list.setSelectedIndex(index); TouchFilePicker.this.activate(); } } });
    this.list.getActionMap().put("activate", new AbstractAction() { @Override public void actionPerformed(final ActionEvent e) { TouchFilePicker.this.activate(); } });
    for(final int key : new int[]{KeyEvent.VK_ENTER, KeyEvent.VK_SPACE}) this.list.getInputMap().put(KeyStroke.getKeyStroke(key, 0), "activate");
    final JScrollPane scroll = new JScrollPane(this.list); scroll.setBorder(ManagerView.roundedBorder()); scroll.getVerticalScrollBar().setPreferredSize(new Dimension(44, 0)); scroll.getVerticalScrollBar().setUnitIncrement(54); content.add(scroll, BorderLayout.CENTER);
    final JPanel footer = new JPanel(new BorderLayout(12, 12)); footer.setOpaque(false);
    final JLabel help = new JLabel(directories ? "Open a folder, then choose Use this location." : "Tap a file to select it. You can choose files across folders."); help.setFont(ManagerView.font(14, false)); help.setForeground(ManagerView.MUTED); footer.add(help, BorderLayout.NORTH);
    final JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0)); actions.setOpaque(false);
    final JButton cancel = ManagerView.button("Cancel", false); cancel.addActionListener(e -> this.dialog.dispose());
    this.choose = ManagerView.button(directories ? "Use this location" : "Use selected files", true); this.choose.addActionListener(e -> {
      this.result = directories ? List.of(this.folder) : List.copyOf(this.selected);
      this.dialog.setVisible(false); this.dialog.dispose();
      if(owner != null) owner.setEnabled(true);
      SwingUtilities.invokeLater(() -> completed.accept(this.result));
    }); actions.add(cancel); actions.add(this.choose); footer.add(actions, BorderLayout.SOUTH); content.add(footer, BorderLayout.SOUTH);
    this.dialog.setContentPane(content); this.dialog.getRootPane().setDefaultButton(this.choose);
    this.dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel"); this.dialog.getRootPane().getActionMap().put("cancel", new AbstractAction() { @Override public void actionPerformed(final ActionEvent e) { TouchFilePicker.this.dialog.dispose(); } });
    this.dialog.addWindowListener(new WindowAdapter() { @Override public void windowClosed(final WindowEvent e) { if(owner != null) owner.setEnabled(true); } });
    this.dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    this.dialog.setSize(880, 640); this.dialog.setLocationRelativeTo(owner); this.open(this.folder);
  }
  static void choose(final JFrame owner, final Path initial, final boolean directories, final java.util.function.Consumer<List<Path>> completed) {
    final var picker = new TouchFilePicker(owner, initial, directories, completed);
    if(owner != null) owner.setEnabled(false);
    picker.dialog.setVisible(true);
  }
  private void activate() {
    final Path path = this.list.getSelectedValue(); if(path == null) return;
    if(Files.isDirectory(path)) this.open(path);
    else { if(!this.selected.remove(path)) this.selected.add(path); this.choose.setEnabled(!this.selected.isEmpty()); this.choose.setText("Use " + this.selected.size() + " " + (this.selected.size() == 1 ? "file" : "files")); this.list.repaint(); }
  }
  private void open(final Path folder) {
    try(final var files = Files.list(folder)) {
      final List<Path> entries = files.filter(p -> Files.isDirectory(p) || !this.directories && p.getFileName().toString().matches("(?i).*\\.(bin|iso|zip|rar|7z)")).sorted(Comparator.<Path, Boolean>comparing(p -> !Files.isDirectory(p)).thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
      this.folder = folder.toAbsolutePath().normalize(); this.location.setText(this.folder.toString()); this.location.setToolTipText(this.folder.toString()); this.model.clear(); entries.forEach(this.model::addElement); if(!entries.isEmpty()) this.list.setSelectedIndex(0); this.list.requestFocusInWindow(); this.choose.setEnabled(this.directories || !this.selected.isEmpty());
    } catch(final IOException e) { this.location.setText("Cannot open this folder. Choose another location."); }
  }
}
