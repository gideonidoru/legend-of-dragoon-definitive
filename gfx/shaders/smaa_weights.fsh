#version 330 core
#define SMAA_GLSL_3
#define SMAA_PRESET_HIGH
uniform vec4 metrics;
#define SMAA_RT_METRICS metrics
#include "smaa/SMAA.hlsl"
smooth in vec2 vertUv;
uniform sampler2D edgesTex;
uniform sampler2D areaTex;
uniform sampler2D searchTex;
layout(location = 0) out vec4 frag;
void main() {
  vec2 pixcoord;
  vec4 offsets[3];
  SMAABlendingWeightCalculationVS(vertUv, pixcoord, offsets);
  frag = SMAABlendingWeightCalculationPS(vertUv, pixcoord, offsets, edgesTex, areaTex, searchTex, vec4(0));
}
