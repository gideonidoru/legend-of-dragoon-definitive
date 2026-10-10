// Definitive touch-ready dialogs (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

final class ManagerDialogs {
  private ManagerDialogs() { }
  static boolean confirm(final JFrame owner, final String title, final JComponent content, final String action) {
    final boolean[] accepted = {false};
    final JDialog dialog = new JDialog(owner, title, true);
    final JPanel panel = panel(title, content, action, dialog::dispose, () -> { accepted[0] = true; dialog.dispose(); });
    final JButton accept = (JButton)panel.getClientProperty("primaryAction");
    dialog.setContentPane(panel); dialog.getRootPane().setDefaultButton(accept);
    dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
    dialog.getRootPane().getActionMap().put("cancel", new AbstractAction() { @Override public void actionPerformed(final ActionEvent e) { dialog.dispose(); } });
    dialog.setSize(700, 460); dialog.setLocationRelativeTo(owner); dialog.addWindowListener(new WindowAdapter() { @Override public void windowOpened(final WindowEvent e) { accept.requestFocusInWindow(); } }); dialog.setVisible(true); return accepted[0];
  }
  static JPanel panel(final String title, final JComponent content, final String action, final Runnable cancel, final Runnable accepted) {
    final JPanel panel = ManagerView.surface(new BorderLayout(0, 24)); panel.setBackground(ManagerView.PAPER); panel.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
    final JLabel heading = new JLabel(title); heading.setFont(ManagerView.font(26, true)); heading.setForeground(ManagerView.INK); panel.add(heading, BorderLayout.NORTH); panel.add(content, BorderLayout.CENTER);
    final JPanel buttons = new JPanel(new GridLayout(1, action.equals("Close") ? 1 : 2, 12, 0)); buttons.setOpaque(false);
    if(!action.equals("Close")) { final JButton back = ManagerView.button("Cancel", false); back.addActionListener(e -> cancel.run()); buttons.add(back); }
    final JButton accept = ManagerView.button(action, true); accept.addActionListener(e -> accepted.run()); buttons.add(accept); panel.add(buttons, BorderLayout.SOUTH); panel.putClientProperty("primaryAction", accept); return panel;
  }
  static boolean restore(final JFrame owner) {
    final JLabel content = ManagerView.copy("Restore the previous version with its pre-update saves and settings. Your newer data is kept separately.", 18, ManagerView.MUTED);
    return confirm(owner, "Restore previous version", content, "Restore version");
  }
  static SteamLibrary.Account account(final JFrame owner, final List<SteamLibrary.Account> accounts) {
    final JList<SteamLibrary.Account> list = new JList<>(accounts.toArray(SteamLibrary.Account[]::new)); list.setFixedCellHeight(52); list.setFont(ManagerView.font(18, false)); list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); list.setSelectedIndex(0); list.setBackground(Color.WHITE); list.setSelectionBackground(new Color(0xdfe9df)); list.setSelectionForeground(ManagerView.INK);
    list.getActionMap().put("activate", new AbstractAction() { @Override public void actionPerformed(final ActionEvent e) { list.transferFocus(); } });
    return confirm(owner, "Choose your Steam account", new JScrollPane(list), "Use account") ? list.getSelectedValue() : null;
  }
}
