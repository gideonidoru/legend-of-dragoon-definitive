// Definitive persistent release ordering (2026-10-11), AGPL v3; see LICENSE.
package legend.definitive.manager;

import java.io.IOException;
import java.util.*;
import java.net.URI;
import java.time.Instant;

/** Logical revision ordering survives carrier replacement and local Restore. */
final class CurrentRelease {
  private CurrentRelease() { }
  private static final String[] FIELDS = {"Generation", "SourceRevision", "ManifestSha256", "BuildRun"};
  static final String HEADER = "DEFINITIVE_CURRENT_V1\n", END = "\nEND_DEFINITIVE_CURRENT_V1\n";
  static final int MAX_MANIFEST_BYTES = 8 * 1024;
  private static final String ROOT = "https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/";
  private static final long MAX_NUMBER = 9007199254740991L;

  static boolean present(final String json) throws IOException {
    if(!(new ReleaseUpdates.Json(json).read() instanceof List<?> releases)) throw new IOException("Invalid release metadata.");
    return releases.stream().anyMatch(value -> value instanceof Map<?, ?> release && !Boolean.TRUE.equals(release.get("draft")) && ("definitive-current".equals(release.get("tag_name")) || release.get("body") instanceof String body && body.contains("DEFINITIVE_CURRENT_")));
  }
  static Optional<ReleaseUpdates.Candidate> select(final String releasesJson, final String platform, final Properties state, final boolean includeInstalled) throws IOException {
    final Object parsed = new ReleaseUpdates.Json(releasesJson).read();
    if(!(parsed instanceof List<?> releases)) throw new IOException("Invalid current release metadata.");
    ReleaseUpdates.Candidate candidate = null;
    for(final Object value : releases) {
      final Map<?, ?> release = object(value);
      if(Boolean.TRUE.equals(release.get("draft"))) continue;
      if(!(release.get("body") instanceof String body) || !body.contains("DEFINITIVE_CURRENT_")) {
        if("definitive-current".equals(release.get("tag_name"))) throw new IOException("Current release manifest is missing.");
        continue;
      }
      if(candidate != null) throw new IOException("More than one current build is published.");
      final String exact = manifestText(body);
      final byte[] bytes = exact.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
      final String digest = PackageManifest.digest(bytes);
      matchAsset(release, Map.of("name", "manifest-" + digest + ".json", "sha256", digest, "bytes", Long.valueOf(bytes.length)), "manifest-" + digest + ".json", false);
      candidate = parse(exact, digest, release, platform);
    }
    if(candidate == null) return Optional.empty();
    requireAllowed(state, candidate.revision());
    return !includeInstalled && installed(state, candidate.revision()) ? Optional.empty() : Optional.of(candidate);
  }
  private static String manifestText(final String body) throws IOException {
    if(body.length() > 12000 || !body.startsWith(HEADER)) throw new IOException("Invalid current manifest framing.");
    final int end = body.indexOf(END, HEADER.length());
    if(end < 0 || body.indexOf("DEFINITIVE_CURRENT_V1", HEADER.length()) != end + 5 || body.indexOf("DEFINITIVE_CURRENT_V1", end + END.length()) >= 0) throw new IOException("Missing or duplicate current manifest marker.");
    final String exact = body.substring(HEADER.length(), end);
    if(exact.isEmpty() || exact.length() > MAX_MANIFEST_BYTES || exact.chars().anyMatch(c -> c > 127)) throw new IOException("Invalid current manifest size or encoding.");
    return exact;
  }
  static Set<String> referencedNames(final String body) throws IOException {
    final String exact = manifestText(body);
    final var pointer = object(new ReleaseUpdates.Json(exact).read());
    final Set<String> names = new HashSet<>(); names.add("manifest-" + PackageManifest.digest(exact.getBytes(java.nio.charset.StandardCharsets.US_ASCII)) + ".json");
    final var platforms = object(pointer.get("platforms"));
    for(final String platform : List.of("linux-x64", "macos-arm64")) {
      final var spec = object(platforms.get(platform));
      for(final String kind : List.of("package", "contents")) names.add(text(object(spec.get(kind)), "name"));
    }
    for(final String kind : List.of("installer", "fmvSource", "entry")) names.add(text(object(pointer.get(kind)), "name"));
    return names;
  }
  private static ReleaseUpdates.Candidate parse(final String exact, final String digest, final Map<?, ?> carrier, final String platform) throws IOException {
    final Map<?, ?> pointer = object(new ReleaseUpdates.Json(exact).read());
    if(!ReleaseUpdates.json(canonical(pointer)).equals(exact)) throw new IOException("Current manifest is not canonical JSON.");
    fields(pointer, "format", "generation", "sourceRevision", "buildRun", "releaseTag", "releaseId", "publishedAt", "platforms", "installer", "fmvSource", "entry");
    if(number(pointer, "format", 1) != 1) throw new IOException("Unsupported current manifest format.");
    final String tag = text(carrier, "tag_name");
    if(!tag.equals("definitive-current") || !tag.equals(text(pointer, "releaseTag")) || !identifier(text(pointer, "releaseId")) || !text(pointer, "releaseId").equals(String.valueOf(carrier.get("id")))) throw new IOException("Current manifest carrier mismatch.");
    final Map<?, ?> platforms = object(pointer.get("platforms")); fields(platforms, "linux-x64", "macos-arm64");
    Map<?, ?> selected = null;
    for(final String target : List.of("linux-x64", "macos-arm64")) {
      final Map<?, ?> spec = object(platforms.get(target)); fields(spec, "package", "contents");
      for(final String kind : List.of("package", "contents")) {
        final Map<?, ?> descriptor = object(spec.get(kind));
        matchAsset(carrier, descriptor, kind + "-" + target + "-" + text(descriptor, "sha256") + ".zip", true);
        number(descriptor, "bytes", kind.equals("contents") ? 8L*1024*1024 : 2L*1024*1024*1024);
      }
      if(target.equals(platform)) selected = spec;
    }
    if(selected == null) throw new IOException("No current build for this platform.");
    final Map<?, ?> installer = object(pointer.get("installer")), entry = object(pointer.get("entry"));
    matchAsset(carrier, installer, "installer-" + text(installer, "sha256") + ".zip", true);
    matchAsset(carrier, entry, "entry-" + text(entry, "sha256") + ".sh", true);
    matchAsset(carrier, object(pointer.get("fmvSource")), "FMVHD-v0.1.0-videos.zip", true);
    final Map<?, ?> pack = object(selected.get("package")), contents = object(selected.get("contents"));
    final Map<?, ?> packAsset = matchAsset(carrier, pack, text(pack, "name"), true), contentsAsset = matchAsset(carrier, contents, text(contents, "name"), true);
    final var revision = new ReleaseUpdates.CurrentRevision(number(pointer, "generation", MAX_NUMBER), text(pointer, "sourceRevision"), digest, Long.toString(number(pointer, "buildRun", MAX_NUMBER)), number(pack, "bytes", 2L*1024*1024*1024), number(contents, "bytes", 8L*1024*1024));
    validate(revision);
    final Instant published;
    try { final String date = text(pointer, "publishedAt"); if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z")) throw new IOException("Current build date must use UTC."); published = Instant.parse(date); } catch(final java.time.DateTimeException failure) { throw new IOException("Invalid current build publication date.", failure); }
    final String body = text(carrier, "body"), notes = body.substring(body.indexOf(END) + END.length());
    return new ReleaseUpdates.Candidate(tag, text(packAsset, "id"), URI.create(text(packAsset, "browser_download_url")), text(pack, "sha256"), published, notes,
      new ReleaseUpdates.ContentsAsset(text(contentsAsset, "id"), URI.create(text(contentsAsset, "browser_download_url")), text(contents, "sha256")), revision);
  }
  private static Map<?, ?> matchAsset(final Map<?, ?> carrier, final Map<?, ?> descriptor, final String expectedName, final boolean requireId) throws IOException {
    if(requireId) fields(descriptor, "name", "id", "sha256", "bytes");
    if(!expectedName.equals(text(descriptor, "name")) || !text(descriptor, "sha256").matches("[a-f0-9]{64}")) throw new IOException("Invalid current asset identity.");
    if(!(carrier.get("assets") instanceof List<?> assets)) throw new IOException("Current assets are unavailable.");
    Map<?, ?> found = null;
    for(final Object item : assets) {
      final Map<?, ?> asset = object(item);
      if(expectedName.equals(asset.get("name"))) { if(found != null) throw new IOException("Duplicate current asset."); found = asset; }
    }
    if(found == null || !"uploaded".equals(found.get("state")) || !("sha256:" + text(descriptor, "sha256")).equals(found.get("digest")) || number(found, "size", 2L*1024*1024*1024) != number(descriptor, "bytes", 2L*1024*1024*1024)
      || !identifier(String.valueOf(found.get("id"))) || requireId && !text(descriptor, "id").equals(String.valueOf(found.get("id"))) || !text(found, "browser_download_url").equals(ROOT + text(carrier, "tag_name") + "/" + expectedName)) throw new IOException("Current asset lacks verified release metadata.");
    final var normalized = new HashMap<Object, Object>(found); normalized.put("id", String.valueOf(found.get("id"))); return normalized;
  }
  private static Object canonical(final Object value) throws IOException {
    if(value instanceof Map<?, ?> map) {
      final Map<String, Object> sorted = new TreeMap<>();
      for(final var item : map.entrySet()) { if(!(item.getKey() instanceof String key)) throw new IOException("Invalid manifest key."); sorted.put(key, canonical(item.getValue())); }
      return sorted;
    }
    if(value instanceof List<?> list) { final var result = new ArrayList<>(); for(final Object item : list) result.add(canonical(item)); return result; }
    return value;
  }
  private static void fields(final Map<?, ?> map, final String... names) throws IOException { if(!map.keySet().equals(Set.of(names))) throw new IOException("Unexpected or missing current manifest fields."); }
  private static Map<?, ?> object(final Object value) throws IOException { if(value instanceof Map<?, ?> map) return map; throw new IOException("Invalid current manifest object."); }
  private static String text(final Map<?, ?> map, final String key) throws IOException { if(map.get(key) instanceof String value) return value; throw new IOException("Missing current manifest field: " + key); }
  private static boolean identifier(final String value) {
    return value.matches("[1-9][0-9]{0,15}") && Long.parseLong(value) <= MAX_NUMBER;
  }
  private static long number(final Map<?, ?> map, final String key, final long limit) throws IOException {
    if(!(map.get(key) instanceof Long value) || value <= 0 || value > limit) throw new IOException("Invalid current manifest number: " + key);
    return value;
  }

  static void validate(final ReleaseUpdates.CurrentRevision revision) throws IOException {
    if(revision == null || revision.generation() <= 0 || revision.generation() > MAX_NUMBER || !revision.sourceRevision().matches("[a-f0-9]{40}")
      || !revision.manifestSha256().matches("[a-f0-9]{64}") || !revision.buildRun().matches("[1-9][0-9]{0,15}") || Long.parseLong(revision.buildRun()) > MAX_NUMBER
      || revision.packageBytes() <= 0 || revision.packageBytes() > 2L * 1024 * 1024 * 1024
      || revision.contentsBytes() <= 0 || revision.contentsBytes() > 8L * 1024 * 1024) throw new IOException("Invalid current build identity.");
  }
  static void requireAllowed(final Properties state, final ReleaseUpdates.CurrentRevision revision) throws IOException {
    validate(revision);
    validateState(state);
    final long floor = generation(state, "acceptedGeneration");
    if(revision.generation() < floor) throw new IOException("The current build is older than a previously accepted build. Retry the update check.");
    if(revision.generation() == floor && (!revision.manifestSha256().equals(state.getProperty("acceptedManifestSha256"))
      || !revision.sourceRevision().equals(state.getProperty("acceptedSourceRevision")))) throw new IOException("The current build conflicts with an accepted generation. Retry the update check.");
  }
  static boolean installed(final Properties state, final ReleaseUpdates.CurrentRevision revision) throws IOException {
    requireAllowed(state, revision);
    return revision.generation() == generation(state, "currentGeneration")
      && revision.manifestSha256().equals(state.getProperty("currentManifestSha256"))
      && revision.sourceRevision().equals(state.getProperty("currentSourceRevision")) && !Boolean.parseBoolean(state.getProperty("uninstalled", "false"));
  }
  static long generation(final Properties state, final String key) throws IOException {
    final String value = state.getProperty(key, "");
    if(value.isEmpty()) return 0;
    if(!value.matches("[1-9][0-9]{0,18}")) throw new IOException("Invalid installed build generation.");
    try { final long result = Long.parseLong(value); if(result > MAX_NUMBER) throw new IOException("Installed build generation exceeds its limit."); return result; } catch(final NumberFormatException failure) { throw new IOException("Invalid installed build generation.", failure); }
  }
  static void validateState(final Properties state) throws IOException {
    for(final String prefix : List.of("current", "previousCurrent", "accepted")) {
      if(Arrays.stream(FIELDS).noneMatch(field -> state.containsKey(prefix + field))) continue;
      if(generation(state, prefix + "Generation") == 0 || !state.getProperty(prefix + "SourceRevision", "").matches("[a-f0-9]{40}")
        || !state.getProperty(prefix + "ManifestSha256", "").matches("[a-f0-9]{64}") || !identifier(state.getProperty(prefix + "BuildRun", ""))) throw new IOException("Installed current build identity is incomplete.");
    }
    if(generation(state, "currentGeneration") > generation(state, "acceptedGeneration")) throw new IOException("Installed current build ordering is inconsistent.");
  }
  static void activate(final Properties next, final Properties old, final ReleaseUpdates.CurrentRevision revision) throws IOException {
    if(next != old) for(final String field : FIELDS) {
      final String prior = old.getProperty("current" + field, "");
      if(!prior.isEmpty()) next.setProperty("previousCurrent" + field, prior);
      else next.remove("previousCurrent" + field);
    }
    for(final String field : FIELDS) if(old.containsKey("accepted" + field)) next.setProperty("accepted" + field, old.getProperty("accepted" + field));
    if(revision == null) {
      if(generation(old, "acceptedGeneration") > 0) throw new IOException("Choose a verified current build before updating this installation.");
      return;
    }
    requireAllowed(old, revision);
    final String[] values = {Long.toString(revision.generation()), revision.sourceRevision(), revision.manifestSha256(), revision.buildRun()};
    for(int i = 0; i < FIELDS.length; i++) { next.setProperty("current" + FIELDS[i], values[i]); next.setProperty("accepted" + FIELDS[i], values[i]); }
  }
  static void restore(final Properties state) {
    for(final String field : FIELDS) {
      final String previous = state.getProperty("previousCurrent" + field, "");
      if(previous.isEmpty()) state.remove("current" + field); else state.setProperty("current" + field, previous);
      state.remove("previousCurrent" + field);
    }
  }
}
