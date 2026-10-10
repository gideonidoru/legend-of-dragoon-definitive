package legend.definitive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SteamIconRetentionTest {
  @TempDir Path temporary;
  @Test void refreshingShortcutPreservesAnExistingCustomIcon() throws Exception {
    final Path install = Files.createDirectories(this.temporary.resolve("install"));
    Files.writeString(install.resolve("Play Game.sh"), "fixture");
    final var account = new SteamLibrary.Account(Files.createDirectories(this.temporary.resolve("account/config")));
    SteamLibrary.add(account, install, () -> false);
    final Path shortcut = account.config().resolve("shortcuts.vdf");
    final var root = SteamLibrary.decode(Files.readAllBytes(shortcut));
    @SuppressWarnings("unchecked") final var entries = (List<SteamLibrary.Value>)root.getFirst().data;
    @SuppressWarnings("unchecked") final var fields = (List<SteamLibrary.Value>)entries.getFirst().data;
    final Path custom = this.temporary.resolve("player-icon.png"); Files.writeString(custom, "player artwork");
    final int index = java.util.stream.IntStream.range(0, fields.size()).filter(i -> fields.get(i).key.equalsIgnoreCase("icon")).findFirst().orElseThrow();
    fields.set(index, new SteamLibrary.Value(1, "icon", custom.toString()));
    final byte[] customized = SteamLibrary.encode(root); Files.write(shortcut, customized);
    SteamLibrary.add(account, install, () -> false);
    assertArrayEquals(customized, Files.readAllBytes(shortcut));
    assertEquals("player artwork", Files.readString(custom));
    Files.delete(custom);
    SteamLibrary.add(account, install, () -> false);
    assertFalse(java.util.Arrays.equals(customized, Files.readAllBytes(shortcut)), "Missing icons should be repaired");
  }
}
