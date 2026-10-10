#version 330 core
#define SMAA_GLSL_3
#define SMAA_PRESET_HIGH
uniform vec4 metrics;
#define SMAA_RT_METRICS metrics
#include "smaa/SMAA.hlsl"
smooth in vec2 vertUv;
uniform sampler2D colourTex;
uniform sampler2D coverageTex;
uniform bool protectInterface;
layout(location = 0) out vec4 frag;
void main() {
  if(protectInterface) {
    ivec2 p = ivec2(vertUv * metrics.zw);
    for(int y = -1; y <= 1; y++) for(int x = -1; x <= 1; x++) {
      if(texelFetch(coverageTex, clamp(p + ivec2(x,y), ivec2(0), ivec2(metrics.zw)-1), 0).r > 0.5) discard;
    }
  }
  vec4 offsets[3];
  SMAAEdgeDetectionVS(vertUv, offsets);
  frag = vec4(SMAAColorEdgeDetectionPS(vertUv, offsets, colourTex), 0, 0);
}
