package legend.definitive.manager;

import legend.definitive.fmv.FmvAssetManifest;
import legend.definitive.fmv.StreamingMovie;
import legend.game.modding.events.fmv.FmvPlaybackEvent;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** Mandatory, windowless checks of the real small mod, all enhanced footage and local import. */
public final class FmvSplitSourceProbe {
  private static void require(final boolean condition, final String message) throws IOException {
    if(!condition) throw new IOException(message);
  }
  private static final class ModLoader extends URLClassLoader {
    ModLoader(final Path jar) throws Exception { super(new URL[]{jar.toUri().toURL()}, FmvSplitSourceProbe.class.getClassLoader()); }
    @Override protected synchronized Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
      if(!name.startsWith("fmvhd.")) return super.loadClass(name,resolve);
      Class<?> type = findLoadedClass(name); if(type == null) type = findClass(name); if(resolve) resolveClass(type); return type;
    }
    @Override public URL getResource(final String name) {
      return name.startsWith("fmvhd/") ? findResource(name) : super.getResource(name);
    }
  }
  public static void main(final String[] args) throws Exception {
    if(args.length != 3) throw new IOException("Supply the staged data directory, small code JAR and pinned legacy installed release");
    final Path data = Path.of(args[0]).toRealPath(), jar = Path.of(args[1]).toRealPath(), legacy = Path.of(args[2]).toRealPath();
    final FmvAssetManifest assets;
    try(final var input = Files.newInputStream(data.resolve("manifest.properties"))) { assets = FmvAssetManifest.read(input); }
    final Path temporary = Files.createTempDirectory("definitive-fmv-split-probe-").toRealPath();
    final long initialWorkers = movieWorkers();
    try(final var loader = new ModLoader(jar)) {
      final Path artwork = temporary.resolve("artwork"); Files.createDirectory(artwork);
      // The normal manager link is top-level gfx -> release/gfx, with ordinary children.
      InstallStore.copyTree(data,artwork.resolve("fmvhd"));
      final Path gfx = temporary.resolve("gfx"); Files.createSymbolicLink(gfx,artwork);
      final Class<?> type = loader.loadClass("fmvhd.FmvHdMod");
      require(type.getProtectionDomain().getCodeSource().getLocation().equals(jar.toUri().toURL()),"Probe must load the actual small code JAR");
      final Object mod = type.getConstructor().newInstance();
      final var installed = type.getMethod("installed",Path.class,FmvAssetManifest.Film.class);
      final var replace = type.getMethod("replace",FmvPlaybackEvent.class);
      final var claimed = new FmvPlaybackEvent("STR/OPENH.IKI",new byte[]{1}); claimed.replacement=temporary.resolve("first-owner");replace.invoke(mod,claimed);
      require(claimed.replacement.equals(temporary.resolve("first-owner")),"Earlier mod ownership was overwritten");
      final var wrongSource = new FmvPlaybackEvent("STR/OPENH.IKI",new byte[]{1});replace.invoke(mod,wrongSource);require(wrongSource.replacement==null,"Wrong native source gained a replacement");
      long frames = 0;
      for(final String name:FmvAssetManifest.NAMES) {
        final var film = assets.films().get(name);
        final Path movie = (Path)installed.invoke(mod,gfx,film);
        require(movie.equals(artwork.resolve("fmvhd/videos/"+film.source().sha256()+".mp4")),"Lookup must use managed ordinary data directly");
        long count = 0, last = -1, due = 0;
        try(final var stream = new StreamingMovie(movie)) {
          require(stream.width==1280 && stream.height==768 && Math.abs(stream.frameRate-15)<0.00001,"Movie dimensions/cadence differ");
          require(Math.abs(stream.durationMicros-film.duration().movePointRight(6).longValue())<100_000,"Movie duration differs from pinned metadata");
          final long deadline = System.nanoTime()+180_000_000_000L;
          while(!stream.drained()) {
            if(stream.failure()!=null) throw stream.failure();
            require(System.nanoTime()<deadline,"Whole movie decode exceeded deadline: "+name);
            while(stream.pollAudio()!=null) { /* Drain copied PCM, never retain or play it. */ }
            final var image = stream.pollVideo(due);
            if(image!=null) {
              if(last>=0) require(Math.abs(image.timestamp()-last-66_667)<=2,"Skipped, duplicated or accelerated movie frame: "+name);
              last=image.timestamp();count++;due=last+51_667;
            } else if(stream.bufferedImages()>0) due+=1000;
            else Thread.sleep(1);
          }
          require(count==film.frames(),"Whole enhanced frame count mismatch: "+name+" "+count+"/"+film.frames());
          require(Math.abs(last-(film.frames()-1)*1_000_000L/15)<1000,"Pinned video tail timestamp differs: "+name);
        }
        frames+=count; System.out.println("FMVHD complete source decode PASS "+name+" frames="+count);
      }
      require(movieWorkers()==initialWorkers,"Movie workers survived close");
      require(!Files.exists(temporary.resolve("cache")),"External lookup must not materialize a cache");
      final Path staged = temporary.resolve("staged");Files.createDirectories(staged.resolve("gfx/fmvhd/videos"));
      Files.copy(data.resolve("manifest.properties"),staged.resolve(FmvAssetManifest.FILE));
      final Properties hashes = new Properties();hashes.setProperty(FmvAssetManifest.FILE,PackageManifest.sha256(staged.resolve(FmvAssetManifest.FILE)));
      final Set<String> imported = new HashSet<>();
      try(final var importer = new FmvMigration(legacy,staged,new PackageManifest(new Properties(),hashes),true)) {
        for(final String name:FmvAssetManifest.NAMES) {
          final var film=assets.films().get(name);if(!imported.add(film.path())) continue;
          require(importer.importMovie(film.path(),film.source().sha256(),film.source().bytes(),staged.resolve(film.path())),"Real installed legacy movie could not be verified/imported: "+name);
          require(PackageManifest.sha256(staged.resolve(film.path())).equals(film.source().sha256()),"Imported real movie identity differs");
          System.out.println("FMVHD real local import PASS "+name);
        }
      }
      System.out.println("FMVHD_SPLIT_SOURCE_PASS names=18 objects="+imported.size()+" frames="+frames+" movieNetworkRequests=0 cacheCopies=0 workers="+movieWorkers());
    } finally { InstallStore.deleteOwnedTree(temporary); }
  }
  private static long movieWorkers() {
    return Thread.getAllStackTraces().keySet().stream().filter(t->t.isAlive() && (t.getName().equals("FMV decoder") || t.getName().equals("FMV audio decoder"))).count();
  }
}
