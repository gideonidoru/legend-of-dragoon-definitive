package legend.definitive.mods;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class ManagedModProfileTest {
  @TempDir Path temporary;
  private String oldProfile, oldPreferences;
  private Path preferences;
  @BeforeEach void managedProfile() throws Exception {
    this.oldProfile=System.getProperty(ManagedModProfile.PROFILE_PROPERTY);
    this.oldPreferences=System.getProperty(ManagedModProfile.PREFERENCES_PROPERTY);
    this.preferences=this.temporary.toRealPath().resolve("definitive-hd-mods.properties");
    System.setProperty(ManagedModProfile.PROFILE_PROPERTY,"hd");
    System.setProperty(ManagedModProfile.PREFERENCES_PROPERTY,this.preferences.toString());
  }
  @AfterEach void restore() {
    restore(ManagedModProfile.PROFILE_PROPERTY,this.oldProfile);restore(ManagedModProfile.PREFERENCES_PROPERTY,this.oldPreferences);
  }
  private static void restore(String key,String value) { if(value==null)System.clearProperty(key);else System.setProperty(key,value); }

  @Test void legacyCampaignGetsAllInstalledVisualModulesWithoutChangingItsSelectionOrCreatingPreferences() {
    final var oldCampaign=new HashSet<>(Set.of("lod","lod_core","custom_gameplay","missing_custom"));
    final var before=Set.copyOf(oldCampaign);final var installed=new HashSet<>(ManagedModProfile.MOD_IDS);installed.add("custom_gameplay");
    final var result=ManagedModProfile.effective(oldCampaign,installed);
    assertTrue(result.containsAll(ManagedModProfile.MOD_IDS));assertTrue(result.containsAll(before));assertEquals(before,oldCampaign);
    assertFalse(Files.exists(this.preferences));assertEquals(7,ManagedModProfile.MOD_IDS.size());
  }

  @Test void missingManagedArtifactsAreNotRequestedAndCustomMissingIdsStillReachTheNormalLoader() {
    final var requested=Set.of("lod","modelshd","custom_missing");
    assertEquals(Set.of("lod","uihd","custom_missing"),ManagedModProfile.effective(requested,Set.of("uihd")));
  }

  @Test void explicitOptoutPersistsAndReenableOverridesOldCampaignOmissions() throws Exception {
    ManagedModProfile.recordSelection("uihd",false);ManagedModProfile.recordSelection("fmvhd",false);
    assertFalse(ManagedModProfile.effective(Set.of("lod","uihd"),ManagedModProfile.MOD_IDS).contains("uihd"));
    assertFalse(ManagedModProfile.effective(Set.of("lod"),ManagedModProfile.MOD_IDS).contains("fmvhd"));
    ManagedModProfile.recordSelection("uihd",true);
    final var next=ManagedModProfile.effective(Set.of("lod"),ManagedModProfile.MOD_IDS);
    assertTrue(next.contains("uihd"));assertFalse(next.contains("fmvhd"));
    final byte[] saved=Files.readAllBytes(this.preferences);
    ManagedModProfile.effective(Set.of(),ManagedModProfile.MOD_IDS);assertArrayEquals(saved,Files.readAllBytes(this.preferences));
  }

  @Test void originalProfileKeepsFiveVisualModulesAndHonorsOptoutsButRemovesSkurfaAndModels() throws Exception {
    ManagedModProfile.recordSelection("uihd",false);ManagedModProfile.recordSelection("modelshd",true);
    System.setProperty(ManagedModProfile.PROFILE_PROPERTY,"original");
    final var result=ManagedModProfile.effective(Set.of("lod","scbackgroundhd","modelshd","custom_gameplay"),ManagedModProfile.MOD_IDS);
    assertEquals(Set.of("lod","envhd","charhd","fxhd","fmvhd","custom_gameplay"),result);
    System.setProperty(ManagedModProfile.PROFILE_PROPERTY,"hd");
    assertTrue(ManagedModProfile.effective(Set.of("lod"),ManagedModProfile.MOD_IDS).contains("modelshd"));
  }

  @Test void noPropertiesInvalidProfilesAndRelativePathsRetainUpstreamBehavior() throws Exception {
    final var requested=Set.of("lod","uihd","custom");
    System.clearProperty(ManagedModProfile.PREFERENCES_PROPERTY);
    assertFalse(ManagedModProfile.isManaged());assertEquals(requested,ManagedModProfile.effective(requested,Set.of()));
    ManagedModProfile.recordSelection("uihd",false);assertFalse(Files.exists(this.preferences));
    System.setProperty(ManagedModProfile.PREFERENCES_PROPERTY,"definitive-hd-mods.properties");
    assertEquals(requested,ManagedModProfile.effective(requested,Set.of()));
    System.setProperty(ManagedModProfile.PREFERENCES_PROPERTY,"/");
    assertFalse(ManagedModProfile.isManaged());assertEquals(requested,ManagedModProfile.effective(requested,Set.of()));
    System.setProperty(ManagedModProfile.PREFERENCES_PROPERTY,this.preferences.toString());System.setProperty(ManagedModProfile.PROFILE_PROPERTY,"unknown");
    assertEquals(requested,ManagedModProfile.effective(requested,Set.of()));
  }

  @Test void batchValidationCannotPartiallyCommitAndEmptyCustomBatchesNeverCreatePreferences() throws Exception {
    ManagedModProfile.recordSelections(java.util.Map.of("custom",false));
    assertFalse(Files.exists(this.preferences));
    ManagedModProfile.recordSelections(java.util.Map.of("uihd",false,"fmvhd",false));
    final byte[] saved=Files.readAllBytes(this.preferences);
    final var invalid=new java.util.HashMap<String,Boolean>();invalid.put("uihd",true);invalid.put("fmvhd",null);
    assertThrows(java.io.IOException.class,()->ManagedModProfile.recordSelections(invalid));
    assertArrayEquals(saved,Files.readAllBytes(this.preferences));
  }

  @Test void customSelectionNeverWritesManagedChoicesAndUnknownFuturePreferencesAreRetained() throws Exception {
    ManagedModProfile.recordSelection("custom_gameplay",false);assertFalse(Files.exists(this.preferences));
    Files.writeString(this.preferences,"future_visual=true\nuihd=false\n");
    ManagedModProfile.recordSelection("fmvhd",false);
    assertTrue(Files.readString(this.preferences).contains("future_visual=true"));
    assertFalse(ManagedModProfile.effective(Set.of(),ManagedModProfile.MOD_IDS).contains("uihd"));
  }

  @Test void corruptOversizedAndLinkedPreferencesNeverOverwriteTheirBytesOrTheirExternalTargets() throws Exception {
    Files.writeString(this.preferences,"uihd=invalid\n");final byte[] invalid=Files.readAllBytes(this.preferences);
    assertThrows(java.io.IOException.class,()->ManagedModProfile.recordSelection("uihd",false));assertArrayEquals(invalid,Files.readAllBytes(this.preferences));
    assertEquals(Set.of("lod","uihd","custom"),ManagedModProfile.effective(Set.of("lod","uihd","custom"),ManagedModProfile.MOD_IDS));
    Files.write(this.preferences,new byte[8193]);assertThrows(java.io.IOException.class,()->ManagedModProfile.recordSelection("uihd",false));
    Files.delete(this.preferences);final Path target=this.temporary.resolve("protected.txt");Files.writeString(target,"protected");Files.createSymbolicLink(this.preferences,target);
    assertThrows(java.io.IOException.class,()->ManagedModProfile.recordSelection("uihd",false));assertEquals("protected",Files.readString(target));
    assertEquals(Set.of("lod"),ManagedModProfile.effective(Set.of("lod"),ManagedModProfile.MOD_IDS));
    Files.delete(this.preferences);Files.createDirectories(this.preferences);
    assertThrows(java.io.IOException.class,()->ManagedModProfile.recordSelection("uihd",false));
  }

  @Test void concurrentExplicitChoicesSerializeWithoutLosingOtherModuleChoices() throws Exception {
    final var threads=new java.util.ArrayList<Thread>();final var errors=new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
    for(String id:ManagedModProfile.MOD_IDS) {
      final var thread=new Thread(()->{try{ManagedModProfile.recordSelection(id,false);}catch(Throwable e){errors.add(e);}});threads.add(thread);thread.start();
    }
    for(var thread:threads)thread.join(2000);
    assertTrue(threads.stream().noneMatch(Thread::isAlive));assertTrue(errors.isEmpty());
    assertEquals(Set.of("lod"),ManagedModProfile.effective(Set.of("lod"),ManagedModProfile.MOD_IDS));
    try(var files=Files.list(this.preferences.getParent())){assertTrue(files.noneMatch(p->p.getFileName().toString().endsWith(".tmp")));}
  }
}
