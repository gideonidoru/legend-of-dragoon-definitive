import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.legendofdragoon.scripting.Disassembler;
import org.legendofdragoon.scripting.OpType;
import org.legendofdragoon.scripting.meta.MetaManager;
import org.legendofdragoon.scripting.tokens.Op;

/** Headless metadata audit. Exports IDs, call offsets and hashes, never script bytes. */
public class BattleRoleScan {
  public static void main(final String[] args) throws Exception {
    if(args.length != 3) {
      throw new IllegalArgumentException("Usage: BattleRoleScan REPO EXTRACTED_FILES OUTPUT_JSON");
    }
    final Path repo = Path.of(args[0]);
    final Path files = Path.of(args[1]);
    final Disassembler decoder = new Disassembler(new MetaManager(null, repo.resolve("patches")).loadMeta("meta"));
    final List<String> rows = new ArrayList<>();
    int failed = 0;
    int scanned = 0;
    for(int id = 0; id < 512; id++) {
      final String relative = "SECT/DRGN1.BIN/" + (id + 1);
      final Path path = files.resolve(relative);
      if(!Files.isRegularFile(path) || Files.size(path) == 0) {
        continue;
      }
      try {
        final byte[] bytes = Files.readAllBytes(path);
        final var script = decoder.disassemble("CombatantId" + id, bytes, List.of(), Map.of());
        final List<Integer> offsets = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        for(final var entry : script.entries) {
          if(entry instanceof Op op && op.type == OpType.CALL) {
            final boolean literalBossAction = op.headerParam == 173 && op.params.length > 0
              && op.params[0].resolvedValue.isPresent() && !op.params[0].resolvedValue.isRange()
              && op.params[0].resolvedValue.get() == 3;
            if(op.headerParam == 172 || literalBossAction) {
              offsets.add(op.address);
              calls.add("{\"offset\":" + op.address + ",\"opcode\":\"CALL\",\"functionIndex\":"
                + op.headerParam + (literalBossAction ? ",\"legacyAction\":3" : "") + "}");
            }
          }
        }
        scanned++;
        if(!offsets.isEmpty()) {
          final String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
          rows.add("{\"monsterId\":" + id + ",\"script\":\"" + relative
            + "\",\"scriptSha256\":\"" + sha256 + "\",\"bossKillCallOffsets\":" + offsets
            + ",\"bossKillCalls\":[" + String.join(",", calls) + "]"
            + ",\"disassemblerWarningCount\":" + script.warnings.size() + "}");
        }
      } catch(final Exception exception) {
        // Preserve partial coverage; never expose source bytes in diagnostics.
        failed++;
      }
    }
    final Path output = Path.of(args[2]);
    Files.createDirectories(output.toAbsolutePath().getParent());
    final String decoderSha256 = sha256(Path.of(Disassembler.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
    final String toolSha256 = sha256(repo.resolve("scripts/modelshd/BattleRoleScan.java"));
    final String engineSourceSha256 = sha256(repo.resolve("src/main/java/legend/game/combat/Battle.java"));
    final TreeMap<String, String> metadata = new TreeMap<>();
    try(final var paths = Files.list(repo.resolve("patches/meta"))) {
      for(final Path path : paths.filter(Files::isRegularFile).toList()) {
        metadata.put("patches/meta/" + path.getFileName(), sha256(path));
      }
    }
    final List<String> metadataRows = new ArrayList<>();
    metadata.forEach((name, hash) -> metadataRows.add("\"" + name + "\":\"" + hash + "\""));
    Files.writeString(output, "{\"decoder\":\"org.legendofdragoon:script-recompiler:0.7.11\","
      + "\"decoderSha256\":\"" + decoderSha256 + "\",\"toolSha256\":\"" + toolSha256 + "\","
      + "\"engineSourceSha256\":\"" + engineSourceSha256 + "\","
      + "\"metadataSha256\":{" + String.join(",", metadataRows) + "},"
      + "\"scope\":\"Decoded boss-kill action context only; absence is not proof of non-boss status\","
      + "\"scanned\":" + scanned + ",\"failed\":" + failed
      + ",\"bossKillScripts\":[" + String.join(",", rows) + "]}\n");
    System.out.println("Scanned=" + scanned + " failed=" + failed + " bossKillScripts=" + rows.size());
  }

  private static String sha256(final Path path) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
  }
}
