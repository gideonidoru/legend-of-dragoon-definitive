#version 330 core

in GS_OUT {
  smooth vec2 vertUv;
  flat vec2 vertTpage;
  flat vec2 vertClut;
  flat int vertBpp;
  smooth vec4 vertColour;
  smooth vec3 lightingNormal;
  smooth vec3 worldPosition;
  smooth vec3 localPosition;
  smooth vec3 worldNormal;
  smooth vec3 localViewDirection;
  smooth vec3 worldViewDirection;
  smooth vec3 lightingColour;
  flat int lightingIndex;
  flat int vertFlags;

  flat int translucency;

  flat float widthMultiplier;
  flat int widthMask;
  flat int indexShift;
  flat int indexMask;

  smooth float depth;
  smooth float depthOffset;
};

layout(std140) uniform projectionInfo {
  float znear;
  float zfar;
  float zdiffInv;
  float projectionMode;
};

layout(std140) uniform scissor {
  float scissorX;
  float scissorY;
  float scissorW;
  float scissorH;
};

// Definitive: reuse the authored scene lights, evaluated on interpolated normals.
struct Light {
  mat4 lightDirection;
  mat3 lightColour;
  vec4 backgroundColour;
};
layout(std140) uniform lighting {
  Light[128] lights;
};
uniform bool smoothLighting;
uniform bool sceneLighting;
uniform vec3 sceneKeyTint;
uniform vec3 sceneAmbientTint;

uniform vec3 recolour;
uniform vec2 uvOffset;
uniform float discardTranslucency;
uniform int tmdTranslucency;
uniform sampler2D tex24;
uniform sampler2D faceDetailTex;
uniform usampler2D tex15;

layout(location = 0) out vec4 outColour;
layout(location = 1) out vec4 outEmission;
layout(location = 2) out vec4 outInterface;
uniform bool uiLayer;
uniform float emission;
uniform bool hdTexture;
uniform bool effectArtworkEnabled;
uniform usampler2D effectDetailTex;
uniform bool materialLighting;
uniform vec2 surfaceResponse;
uniform int effectLightCount;
uniform vec4 effectPositions[4];
uniform vec4 effectColours[4];

uniform vec4 environmentDirection;
uniform vec3 environmentColour;
uniform vec3 environmentAmbient;
uniform bool normalMapEnabled;
uniform bool defaultSurfaceMaps;
uniform bool roughnessMapEnabled;
uniform sampler2D normalMapTex;
uniform sampler2D roughnessMapTex;
uniform float normalMapStrength;
vec3 materialNormal;
vec3 materialWorldNormal;
vec2 detailUv;
vec2 detailGradientX;
vec2 detailGradientY;

mat3 detailFrame(vec3 n, vec3 dpX, vec3 dpY, vec2 uvX, vec2 uvY) {
  vec3 px = cross(dpY, n);
  vec3 py = cross(n, dpX);
  vec3 t = px * uvX.x + py * uvY.x;
  vec3 b = px * uvX.y + py * uvY.y;
  float size = max(dot(t,t), dot(b,b));
  if(size < 1e-12) return mat3(vec3(0), vec3(0), n);
  return mat3(t * inversesqrt(size), b * inversesqrt(size), n);
}

void prepareMaterial() {
  materialNormal = lightingNormal;
  materialWorldNormal = worldNormal;
  if((vertFlags & 0x20000) == 0 && (normalMapEnabled || roughnessMapEnabled) && (vertFlags & 0x1) != 0 && (vertFlags & 0x8) == 0 && !uiLayer) {
    // Derivatives precede transparency/scissor discards; exact alpha/STP lookup stays separate.
    detailUv = vertUv + uvOffset;
    if(defaultSurfaceMaps) detailUv *= vertBpp == 3 ? 8.0 : 1.0/32.0;
    detailGradientX = dFdx(detailUv);
    detailGradientY = dFdy(detailUv);
    if(normalMapEnabled && (vertBpp == 3 || defaultSurfaceMaps) && normalMapStrength > 0.0 && dot(worldNormal,worldNormal) > 1e-8 && dot(lightingNormal,lightingNormal) > 1e-8) {
      vec3 detail = textureGrad(normalMapTex, detailUv, detailGradientX, detailGradientY).xyz * 2.0 - 1.0;
      detail.xy *= normalMapStrength;
      detail.z = max(detail.z, 0.01);
      materialNormal = detailFrame(normalize(lightingNormal), dFdx(localPosition), dFdy(localPosition), detailGradientX, detailGradientY) * detail;
      materialWorldNormal = detailFrame(normalize(worldNormal), dFdx(worldPosition), dFdy(worldPosition), detailGradientX, detailGradientY) * detail;
    }
  }
}

vec2 faceResponse() {
  vec2 response = surfaceResponse;
  if((vertFlags & 0x20) != 0) {
    int kind = (vertFlags >> 6) & 7;
    float roughness = max(0.05, float((vertFlags >> 9) & 255) / 255.0);
    response.x = mix(128.0, 4.0, roughness * roughness);
    response.y = kind == 1 ? 0.008 : kind == 2 || kind == 4 ? 0.035 : kind == 3 ? 0.16 : 0.018;
  }
  if(roughnessMapEnabled && (vertBpp == 3 || defaultSurfaceMaps)) {
    float roughness = clamp(textureGrad(roughnessMapTex, detailUv, detailGradientX, detailGradientY).r, 0.05, 1.0);
    if(defaultSurfaceMaps) roughness = clamp(sqrt(clamp((128.0-response.x)/124.0,0.0,1.0)) * (0.96 + roughness*0.08), 0.05, 1.0);
    response.x = mix(128.0, 4.0, roughness * roughness);
  }
  return response;
}

vec3 environmentDiffuse(vec3 albedo, vec3 legacy) {
  float size = dot(materialWorldNormal, materialWorldNormal);
  if(environmentDirection.w <= 0.0 || size <= 1e-8) return legacy;
  float facing = max(dot(materialWorldNormal * inversesqrt(size), environmentDirection.xyz), 0.0);
  vec3 lit = albedo * (environmentAmbient + environmentColour * facing);
  return mix(legacy, lit, environmentDirection.w);
}

vec3 surfaceLight() {
  vec2 response = faceResponse();
  vec3 result = vec3(0.0);
  float n2 = dot(materialNormal, materialNormal);
  float v2 = dot(localViewDirection, localViewDirection);
  if(n2 > 1e-8 && v2 > 1e-8 && materialLighting) {
    vec3 n = materialNormal * inversesqrt(n2);
    vec3 v = localViewDirection * inversesqrt(v2);
    Light l = lights[lightingIndex];
    for(int i = 0; i < 3; i++) {
      vec3 direction = vec3(l.lightDirection[0][i], l.lightDirection[1][i], l.lightDirection[2][i]);
      float d2 = dot(direction, direction);
      if(d2 <= 1e-8) continue;
      vec3 d = direction * inversesqrt(d2);
      vec3 h = d + v;
      float h2 = dot(h, h);
      if(h2 > 1e-8) {
        float highlight = pow(max(dot(n, h * inversesqrt(h2)), 0.0), response.x);
        result += l.lightColour[i] * (highlight * response.y * max(dot(n, d), 0.0) * (1.0 - environmentDirection.w));
      }
    }
  }
  float wn2 = dot(materialWorldNormal, materialWorldNormal);
  if(wn2 > 1e-8) {
    vec3 n = materialWorldNormal * inversesqrt(wn2);
    float viewSize = dot(worldViewDirection, worldViewDirection);
    if(materialLighting && environmentDirection.w > 0.0 && viewSize > 1e-8) {
      vec3 halfDirection = environmentDirection.xyz + worldViewDirection * inversesqrt(viewSize);
      float halfSize = dot(halfDirection, halfDirection);
      if(halfSize > 1e-8) result += environmentColour * (pow(max(dot(n,halfDirection * inversesqrt(halfSize)),0.0),response.x) * response.y * max(dot(n,environmentDirection.xyz),0.0) * environmentDirection.w);
    }
    for(int i = 0; i < min(effectLightCount, 4); i++) {
      vec3 delta = effectPositions[i].xyz - worldPosition;
      float distanceSquared = dot(delta, delta);
      float radius = max(effectPositions[i].w, 0.001);
      float falloff = max(1.0 - distanceSquared / (radius * radius), 0.0);
      float facing = max(dot(n, delta * inversesqrt(max(distanceSquared, 1e-8))), 0.0);
      result += effectColours[i].rgb * (falloff * falloff * facing);
    }
  }
  return clamp(result, 0.0, 0.35);
}

// Neural detail is a bounded mixture of live palette entries, never a frozen RGB palette.
// Native index/color remain authoritative for STP, black/discard, and both render passes.
vec3 effectDetail(vec3 nativeColour, int nativeIndex) {
  if(!effectArtworkEnabled || uiLayer || vertBpp != 0 || all(equal(nativeColour, vec3(0.0)))) return nativeColour;
  vec2 local = vertUv + uvOffset;
  // Match the native shader's packed-word and nibble addressing exactly.
  ivec2 word = ivec2(vertTpage.x + local.x * widthMultiplier, vertTpage.y + local.y);
  int nibble = int(vertTpage.x + vertUv.x) & widthMask;
  ivec2 coordinate = ivec2(word.x * 4 + nibble, word.y * 2 + int(fract(local.y) * 2.0));
  if(any(lessThan(coordinate, ivec2(0))) || any(greaterThanEqual(coordinate, textureSize(effectDetailTex, 0)))) return nativeColour;
  uint detailPair = texelFetch(effectDetailTex, coordinate, 0).r;
  uint code = (detailPair >> (int(fract(local.x) * 2.0) * 16)) & 65535u;
  if((code & 32768u) == 0u || int(code & 15u) != nativeIndex) return nativeColour;
  uint colour = texelFetch(tex15, ivec2(vertClut.x + int((code >> 4) & 15u), vertClut.y), 0).r;
  vec3 second = vec3(colour & 31u, (colour >> 5) & 31u, (colour >> 10) & 31u) / 31.0;
  vec3 result = mix(nativeColour, second, float((code >> 8) & 127u) / 254.0);
  return any(notEqual(result, vec3(0.0))) ? result : nativeColour;
}

void main() {
  prepareMaterial();
  vec3 materialAlbedo = lightingColour;
  // Older Intel iGPUs are buggy and don't implement scissoring properly, causing the Shirley fight to lock up when
  // she transforms into another character. This is a workaround and reimplements scissoring at the shader level.
  if(gl_FragCoord.x < scissorX || gl_FragCoord.x >= scissorX + scissorW || gl_FragCoord.y < scissorY || gl_FragCoord.y >= scissorY + scissorH) {
    discard;
  }

  // Linearize depth for perspective transforms so that we can render ortho models at specific depths
  if(projectionMode == 2) {
    gl_FragDepth = (depth - znear) * zdiffInv + depthOffset;
  } else {
    gl_FragDepth = gl_FragCoord.z;
  }

  bool translucent = (vertFlags & 0x8) != 0;
  bool textured = (vertFlags & 0x2) != 0;
  outColour = vertColour;
  if(smoothLighting && (vertFlags & 0x1) != 0 && !translucent) {
    float normalLengthSquared = dot(materialNormal, materialNormal);
    // Degenerate normals retain the legacy result instead of producing NaNs.
    if(normalLengthSquared > 1e-8) {
      vec3 normal = materialNormal * inversesqrt(normalLengthSquared);
      Light l = lights[lightingIndex];
      float range = textured ? 2.0 : 1.0;
      vec3 diffuse = (l.lightDirection * vec4(normal, 1.0)).rgb;
      // A small wrap softens the terminator using the scene's own light colors.
      // Zero-color lights stay dark; unit-facing highlights keep their authored intensity.
      if(sceneLighting) {
        diffuse = max(diffuse, (diffuse + vec3(0.08)) / 1.08);
      }
      vec3 direct = l.lightColour * clamp(diffuse, 0.0, 8.0);
      vec3 ambient = l.backgroundColour.rgb;
      if(sceneLighting) {
        direct *= sceneKeyTint;
        ambient *= sceneAmbientTint;
      }
      outColour.rgb = clamp(clamp(direct + ambient, 0.0, 8.0) * lightingColour, 0.0, range);
    }
  }

  int translucencyMode = translucency + 1;
  if(translucent && !textured) {
    translucencyMode = tmdTranslucency + 1;
  }

  // Textured
  if(textured) {
    vec4 texColour;
    if((vertFlags & 0x20000) != 0) {
      texColour = texture(faceDetailTex, vertUv);
      texColour.a = 0.0; // Opaque authored face paint, independent of PSX STP.
    } else if(vertBpp == 0 || vertBpp == 1) {
      // Calculate CLUT index
      ivec2 uv = ivec2(vertTpage.x + (vertUv.x + uvOffset.x) * widthMultiplier, vertTpage.y + vertUv.y + uvOffset.y);
      ivec4 indexVec = ivec4(texelFetch(tex15, uv, 0));
      int p = (indexVec.r >> ((int(vertTpage.x + vertUv.x) & widthMask) << indexShift)) & indexMask;

      // Pull actual pixel colour from CLUT
      uint pixel = texelFetch(tex15, ivec2(vertClut.x + p, vertClut.y), 0).r;
      texColour.a = float(pixel >> 15 & 0x1fu) / 31.0;
      texColour.b = float(pixel >> 10 & 0x1fu) / 31.0;
      texColour.g = float(pixel >>  5 & 0x1fu) / 31.0;
      texColour.r = float(pixel       & 0x1fu) / 31.0;
      texColour.rgb = effectDetail(texColour.rgb, p);
    } else if(vertBpp == 2) {
      ivec2 uv = ivec2(vertTpage.x + (vertUv.x + uvOffset.x), vertTpage.y + vertUv.y + uvOffset.y);
      uint pixel = texelFetch(tex15, uv, 0).r;
      texColour.a = float(pixel >> 15 & 0x1fu) / 31.0;
      texColour.b = float(pixel >> 10 & 0x1fu) / 31.0;
      texColour.g = float(pixel >>  5 & 0x1fu) / 31.0;
      texColour.r = float(pixel       & 0x1fu) / 31.0;
    } else {
      vec2 uv = vertUv + uvOffset;
      if(hdTexture) {
        vec2 uvDx = dFdx(uv), uvDy = dFdy(uv);
        ivec2 size = textureSize(tex24, 0);
        vec4 source = texelFetch(tex24, clamp(ivec2(uv * vec2(size)), ivec2(0), size - 1), 0);
        // Full-canvas HD foregrounds contain large empty regions: reject them before filtered reads.
        if(all(equal(source, vec4(0.0)))) discard;
        texColour = textureGrad(tex24, uv, uvDx, uvDy);
        texColour.a = source.a;
      } else {
        texColour = texture(tex24, uv);
      }
    }

    // Discard if (0, 0, 0)
    if((vertFlags & 0x20000) == 0 && texColour.a == 0 && texColour.r == 0 && texColour.g == 0 && texColour.b == 0) {
      discard;
    }

    // If translucent primitive and texture pixel translucency bit is set, pixel is translucent so we defer rendering
    if(discardTranslucency == 1 && translucent && texColour.a != 0 || discardTranslucency == 2 && (!translucent || texColour.a == 0)) {
      discard;
    }

    materialAlbedo *= texColour.rgb;
    outColour = clamp(outColour * texColour, 0.0, 1.0);
  } else {
    // Untextured translucent primitives don't have a translucency bit so we always discard during the appropriate discard modes
    if(discardTranslucency == 1 && translucent || discardTranslucency == 2 && !translucent) {
      discard;
    }
  }

  if((vertFlags & 0x1) != 0 && !translucent && !uiLayer) {
    outColour.rgb = environmentDiffuse(materialAlbedo, outColour.rgb);
    outColour.rgb += surfaceLight() * materialAlbedo;
  }

  outColour.rgb *= recolour;

  if(translucent && translucencyMode == 1) { // (B+F)/2 translucency
    outColour.a = 0.5;
  } else {
    outColour.a = 1.0;
  }
  outEmission = vec4(uiLayer ? vec3(0.0) : outColour.rgb * emission, outColour.a);
  outInterface = vec4(uiLayer ? 1.0 : 0.0);
}
