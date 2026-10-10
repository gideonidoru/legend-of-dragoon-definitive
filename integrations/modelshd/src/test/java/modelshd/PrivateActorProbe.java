package modelshd;

import legend.core.GameEngine;
import legend.core.gte.ModelPart10;
import legend.core.renderer.MeshObj;
import legend.core.renderer.Obj;
import legend.core.renderer.noop.NoopApi;
import legend.game.tmd.TmdObjTable1c;
import legend.game.tmd.UvAdjustmentMetrics14;
import legend.game.types.CContainer;
import legend.game.types.Model124;
import legend.game.unpacker.FileData;
import legend.game.combat.types.CombatantStruct1a8;
import java.nio.file.*;
import java.util.*;

/** Explicit private asset opt-in. CPU/native mesh construction only, no window or gameplay. */
public final class PrivateActorProbe {
  public static void main(String[] args) throws Exception {
    if(args.length!=4) throw new IllegalArgumentException("source-container control-pack pilot-pack report");
    var field=GameEngine.RENDERER.getClass().getDeclaredField("api");field.setAccessible(true);field.set(GameEngine.RENDERER,new NoopApi());
    var source=new CContainer("private model",new FileData(Files.readAllBytes(Path.of(args[0]))));
    if(source.clutAnimations_04!=null || source.ptr_08!=null)throw new AssertionError("Source is held by current event compatibility policy");
    var original=source.tmdPtr_00.tmd.objTable;var identity=ModelPack.identity(original);var rows=new ArrayList<float[]>();
    for(var part:original) for(var mesh:((MeshObj)part.getObj()).meshes) rows.add(mesh.vertices().clone());
    TextureCompatibility.requireNativeAddressing(original);
    var control=ModelPack.read(Path.of(args[1]),original);int meshIndex=0;
    for(var part:control) for(var mesh:((MeshObj)part.buildObjLike(original[meshIndexPart(control,part)])).meshes) {
      var expected=rows.get(meshIndex++); var actual=mesh.vertices(); if(!Arrays.equals(expected,actual)) { for(int j=0;j<Math.min(expected.length,actual.length);j++)if(Float.floatToIntBits(expected[j])!=Float.floatToIntBits(actual[j]))System.err.println("DIFF mesh="+(meshIndex-1)+" row="+(j/16)+" col="+(j%16)+" old="+expected[j]+" new="+actual[j]); throw new AssertionError("Original control native vertex data differs: lengths "+expected.length+"/"+actual.length); }
    }
    var model=new Model124("private pilot");model.modelParts_00=new ModelPart10[original.length];
    for(int i=0;i<original.length;i++){model.modelParts_00[i]=new ModelPart10();model.modelParts_00[i].tmd_08=original[i];}
    var animation=model.anim_08;
    var combatant=new CombatantStruct1a8();combatant.flags_19e=5;combatant.charIndex_1a2=Path.of(args[0]).getParent().getFileName().toString().equals("dragoon")?1:0;combatant.tmd_08=source;
    final boolean bundled=args[2].equals("--bundled");
    final Path packs=bundled?Path.of(args[3]).getParent().resolve("no-external-packs"):Path.of(args[2]).getParent();
    if(Files.exists(packs) && bundled)throw new AssertionError("Bundled proof requires no local overrides");
    if(!ModelsHdMod.replaceIfSupported(combatant,model,packs))throw new AssertionError("Battle adapter declined source");
    var candidate=new TmdObjTable1c[model.modelParts_00.length];for(int i=0;i<candidate.length;i++)candidate[i]=model.modelParts_00[i].tmd_08;
    if(model.anim_08!=animation)throw new AssertionError("Animation changed");long floats=0;
    for(var part:model.modelParts_00)for(var mesh:((MeshObj)part.tmd_08.getObj()).meshes)for(float v:mesh.vertices()){if(!Float.isFinite(v))throw new AssertionError("Nonfinite native vertex");floats++;}
    // A separate active-source route emulates PNG conversion while retaining its native dimensions.
    var rgba=new CContainer("private RGBA route",new FileData(Files.readAllBytes(Path.of(args[0])))).tmdPtr_00.tmd.objTable;
    for(var part:rgba){for(var primitive:part.primitives_10)if((primitive.header()&0x04000000)!=0)UvAdjustmentMetrics14.PNG.apply(primitive);part.rebuildObj(256,112);}
    TextureCompatibility.requireNativeAddressing(rgba);
    final TmdObjTable1c[] hd;
    if(bundled)hd=ModelPack.read(ModelsHdMod.class.getResourceAsStream("/modelshd/models/battle/"+identity+".json"),rgba);
    else hd=ModelPack.read(Path.of(args[2]),rgba);
    for(int i=0;i<hd.length;i++)hd[i].buildObjLike(rgba[i]);
    for(var group:List.of(control,candidate,rgba,hd))for(var part:group)part.delete();Obj.deleteObjects();
    var report=new com.google.gson.JsonObject();report.addProperty("result","passed");report.addProperty("scope","Private whole-actor battle adapter/source/control/candidate native CPU vertex construction with no-op allocation. PNG conversion/dimensions are simulated; no actual CharHD, GL draw, gameplay or Deck proof.");report.addProperty("sourceGeometrySha256",identity);report.addProperty("battleAdapterApplied",true);report.addProperty("dragoon",combatant.isDragoon());report.addProperty("animationParts",original.length);report.addProperty("sourceControlMeshesExact",meshIndex);report.addProperty("candidateNativeFloatsFinite",floats);report.addProperty("rgbaPartsConstructed",hd.length);Files.writeString(Path.of(args[3]),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");System.out.println(report);
  }
  private static int meshIndexPart(TmdObjTable1c[] parts,TmdObjTable1c part){for(int i=0;i<parts.length;i++)if(parts[i]==part)return i;throw new AssertionError();}
}
