// Definitive Steam shortcut integration (2026-10-09), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;

/** Binary KeyValues shortcut editing. Preserve unrelated entries; back up before atomic replacement. */
public final class SteamLibrary {
  private SteamLibrary() { }
  public record Account(Path config) {
    @Override public String toString() { return "Steam account " + config.getParent().getFileName(); }
  }
  static final class Value {
    final int type; final String key; final Object data;
    Value(final int type, final String key, final Object data) { this.type = type; this.key = key; this.data = data; }
  }
  public static List<Account> accounts() throws IOException {
    final Path home = Path.of(System.getProperty("user.home"));
    final List<Account> accounts = new ArrayList<>(); final Set<Path> seen = new HashSet<>();
    for(final Path base : List.of(home.resolve(".local/share/Steam/userdata"), home.resolve(".steam/steam/userdata"), home.resolve("Library/Application Support/Steam/userdata"))) {
      if(!Files.isDirectory(base)) continue;
      try(final var users = Files.list(base.toRealPath())) {
        for(final Path user : users.toList()) if(user.getFileName().toString().matches("[0-9]+") && Files.isDirectory(user.resolve("config"))) {
          final Path config = user.resolve("config").toRealPath();
          if(seen.add(config)) accounts.add(new Account(config));
        }
      }
    }
    accounts.sort(Comparator.comparing(a -> a.config().toString())); return accounts;
  }
  public static boolean steamRunning() {
    try(final var processes = ProcessHandle.allProcesses()) {
      return processes.anyMatch(p -> p.info().command().map(c -> Path.of(c).getFileName().toString().toLowerCase(Locale.ROOT)).map(c -> c.equals("steam") || c.equals("steam_osx") || c.equals("steam.sh") || c.equals("steamwebhelper")).orElse(false));
    }
  }
  public static String add(final Account account, final Path install) throws IOException { return add(account, install, SteamLibrary::steamRunning); }
  static String add(final Account account, final Path install, final BooleanSupplier running) throws IOException {
    if(Files.isSymbolicLink(account.config().resolve(".definitive-shortcut-lock"))) throw new IOException("Unexpected linked Steam operation lock.");
    try(final var channel = java.nio.channels.FileChannel.open(account.config().resolve(".definitive-shortcut-lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      final java.nio.channels.FileLock lock;
      try { lock = channel.tryLock(); }
      catch(final java.nio.channels.OverlappingFileLockException e) { throw new IOException("Another Steam shortcut operation is running."); }
      if(lock == null) throw new IOException("Another Steam shortcut operation is running.");
      try(lock) { return addLocked(account, install, running); }
    }
  }
  private static String addLocked(final Account account, final Path install, final BooleanSupplier running) throws IOException {
    if(running.getAsBoolean()) throw new IOException("Close Steam, then choose Add to Steam again. Your library has not been changed.");
    final Path folder = account.config().toRealPath();
    final Path file = folder.resolve("shortcuts.vdf");
    if(Files.isSymbolicLink(file)) throw new IOException("Linked Steam shortcut files are unsupported.");
    final boolean exists = Files.exists(file);
    if(exists && Files.size(file) > 8 * 1024 * 1024) throw new IOException("Steam shortcuts file is unexpectedly large.");
    final byte[] original = exists ? Files.readAllBytes(file) : new byte[]{0, 's','h','o','r','t','c','u','t','s',0,8,8};
    final List<Value> root = decode(original);
    if(root.size() != 1 || root.getFirst().type != 0 || !root.getFirst().key.equals("shortcuts")) throw new IOException("Unexpected Steam shortcut format. Your library has not been changed.");
    @SuppressWarnings("unchecked") final List<Value> shortcuts = (List<Value>)root.getFirst().data;
    final Path launcher = install.toAbsolutePath().normalize().resolve("Play Game.sh");
    if(!Files.isRegularFile(launcher, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Finish installing before adding to Steam.");
    if(launcher.toString().contains("\"") || launcher.toString().contains("\n")) throw new IOException("Choose an installation path without quotes or line breaks.");
    final String exe = '"' + launcher.toString() + '"';
    final Set<Integer> appids = new HashSet<>(); int index = 0;
    for(final Value shortcut : shortcuts) {
      if(shortcut.type != 0 || !shortcut.key.matches("[0-9]+")) throw new IOException("Unexpected Steam shortcut entry.");
      try { index = Math.max(index, Math.addExact(Integer.parseInt(shortcut.key), 1)); }
      catch(final ArithmeticException | NumberFormatException e) { throw new IOException("Invalid Steam shortcut index."); }
      @SuppressWarnings("unchecked") final List<Value> fields = (List<Value>)shortcut.data;
      if(fields.stream().anyMatch(v -> v.type == 1 && v.key.equalsIgnoreCase("exe") && exe.equals(v.data))) return "Already in your Steam library. Open Steam to play.";
      fields.stream().filter(v -> v.type == 2 && v.key.equalsIgnoreCase("appid")).forEach(v -> appids.add(leInt((byte[])v.data)));
    }
    final String name = "The Legend of Dragoon: Definitive";
    int appid; int salt = 0;
    do { final CRC32 crc = new CRC32(); crc.update((exe + name + (salt == 0 ? "" : "-" + salt)).getBytes(StandardCharsets.UTF_8)); appid = (int)crc.getValue() | 0x80000000; salt++; } while(appids.contains(appid));
    final List<Value> fields = new ArrayList<>();
    fields.add(integer("appid", appid)); fields.add(string("AppName", name)); fields.add(string("exe", exe));
    fields.add(string("StartDir", '"' + install.toAbsolutePath().normalize().toString() + '"'));
    fields.add(string("icon", "")); fields.add(string("ShortcutPath", "")); fields.add(string("LaunchOptions", ""));
    for(final String key : List.of("IsHidden", "OpenVR", "Devkit", "LastPlayTime")) fields.add(integer(key, 0));
    fields.add(integer("AllowDesktopConfig", 0)); fields.add(integer("AllowOverlay", 1)); fields.add(string("DevkitGameID", ""));
    fields.add(new Value(0, "tags", new ArrayList<>(List.of(string("0", "Definitive")))));
    shortcuts.add(new Value(0, Integer.toString(index), fields));
    final byte[] output = encode(root);
    final Path temp = Files.createTempFile(folder, ".definitive-shortcuts-", ".tmp");
    try {
      Files.write(temp, output);
      if(running.getAsBoolean() || Files.isSymbolicLink(file) || exists != Files.exists(file) || exists && !Arrays.equals(original, Files.readAllBytes(file))) throw new IOException("Steam or its library changed during setup. Close Steam and try again; no shortcut was written.");
      if(exists) Files.write(folder.resolve("shortcuts.vdf.definitive-backup-" + UUID.randomUUID()), original, StandardOpenOption.CREATE_NEW);
      Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return "Added to Steam. Open Steam or return to Gaming Mode to find Definitive in your library.";
    } finally { Files.deleteIfExists(temp); }
  }
  private static Value string(final String key, final String value) { return new Value(1, key, value); }
  private static Value integer(final String key, final int value) { return new Value(2, key, new byte[]{(byte)value,(byte)(value >> 8),(byte)(value >> 16),(byte)(value >> 24)}); }
  private static int leInt(final byte[] bytes) { return (bytes[0] & 255) | (bytes[1] & 255) << 8 | (bytes[2] & 255) << 16 | bytes[3] << 24; }
  static List<Value> decode(final byte[] bytes) throws IOException {
    final var input = new ByteArrayInputStream(bytes); final List<Value> values = read(input, 0, new int[]{0});
    if(input.available() != 0) throw new IOException("Unexpected Steam shortcut trailing data."); return values;
  }
  private static List<Value> read(final ByteArrayInputStream in, final int depth, final int[] count) throws IOException {
    if(depth > 16) throw new IOException("Steam shortcuts nesting is too deep.");
    final List<Value> values = new ArrayList<>();
    while(true) {
      final int type = in.read(); if(type == 8) return values;
      if(type < 0 || ++count[0] > 30000) throw new IOException("Truncated or oversized Steam shortcut data.");
      final String key = text(in); final Object data;
      if(type == 0) data = read(in, depth + 1, count);
      else if(type == 1) data = text(in);
      else if(type == 2 || type == 3 || type == 4 || type == 6 || type == 7) {
        final int length = type == 7 ? 8 : 4; final byte[] raw = in.readNBytes(length);
        if(raw.length != length) throw new IOException("Truncated Steam value."); data = raw;
      } else throw new IOException("Unsupported Steam value type; library preserved.");
      values.add(new Value(type, key, data));
    }
  }
  private static String text(final InputStream in) throws IOException {
    final var bytes = new ByteArrayOutputStream();
    for(int c; (c = in.read()) != 0;) {
      if(c < 0 || bytes.size() > 65535) throw new IOException("Invalid Steam text."); bytes.write(c);
    }
    try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString(); }
    catch(final java.nio.charset.CharacterCodingException e) { throw new IOException("Invalid Steam text encoding; library preserved."); }
  }
  static byte[] encode(final List<Value> values) throws IOException { final var out = new ByteArrayOutputStream(); write(out, values); return out.toByteArray(); }
  private static void write(final OutputStream out, final List<Value> values) throws IOException {
    for(final Value v : values) {
      out.write(v.type); out.write(v.key.getBytes(StandardCharsets.UTF_8)); out.write(0);
      if(v.type == 0) { @SuppressWarnings("unchecked") final List<Value> nested = (List<Value>)v.data; write(out, nested); }
      else if(v.type == 1) { out.write(((String)v.data).getBytes(StandardCharsets.UTF_8)); out.write(0); }
      else out.write((byte[])v.data);
    }
    out.write(8);
  }
}
