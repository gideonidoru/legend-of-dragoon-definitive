package legend.definitive.rendering;

import org.joml.Matrix3fc;
import org.joml.Vector3fc;

/** Conservative live profile from the game's authored world-space lights, including scripted changes. */
public final class NativeSceneLighting {
  public static final float INFLUENCE = 0.12f;
  private NativeSceneLighting() { }
  public static EnvironmentLight profile(final Matrix3fc directions, final Matrix3fc colours, final Vector3fc ambient) {
    if(!ambient.isFinite()) return EnvironmentLight.NONE;
    for(int column=0;column<3;column++) for(int row=0;row<3;row++) {
      if(!Float.isFinite(directions.get(column,row)) || !Float.isFinite(colours.get(column,row))) return EnvironmentLight.NONE;
    }
    double weightSum=0, dx=0, dy=0, dz=0, red=0, green=0, blue=0;
    for(int i=0;i<3;i++) {
      final double x=directions.get(0,i), y=directions.get(1,i), z=directions.get(2,i);
      final double length=Math.sqrt(x*x+y*y+z*z);
      if(length <= 1e-6) continue;
      final double r=Math.max(0,colours.get(i,0))*length, g=Math.max(0,colours.get(i,1))*length, b=Math.max(0,colours.get(i,2))*length;
      final double energy=r*.2126+g*.7152+b*.0722, weight=energy*energy;
      weightSum+=weight;
      dx+=x/length*weight; dy+=y/length*weight; dz+=z/length*weight;
      red+=r*weight; green+=g*weight; blue+=b*weight;
    }
    final float ar=bound(ambient.x()), ag=bound(ambient.y()), ab=bound(ambient.z());
    if(weightSum == 0) return ar+ag+ab == 0 ? EnvironmentLight.NONE : new EnvironmentLight(0,-1,0,0,0,0,ar,ag,ab,INFLUENCE);
    dx/=weightSum; dy/=weightSum; dz/=weightSum;
    final double coherence=Math.sqrt(dx*dx+dy*dy+dz*dz);
    // Opposing authored lights fade the profile instead of flipping an invented key direction.
    if(coherence < 1e-6) return EnvironmentLight.NONE;
    red/=weightSum; green/=weightSum; blue/=weightSum;
    final double scale=Math.max(1,Math.max(red,Math.max(green,blue)));
    return new EnvironmentLight((float)(dx/coherence),(float)(dy/coherence),(float)(dz/coherence),
      (float)(red/scale),(float)(green/scale),(float)(blue/scale),ar,ag,ab,(float)(INFLUENCE*Math.min(1,coherence)));
  }

  private static float bound(final float value) { return Math.max(0,Math.min(1,value)); }
}
