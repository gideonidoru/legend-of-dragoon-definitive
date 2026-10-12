#version 330 core

smooth in vec2 vertUv;
flat in vec2 vertTpage;
flat in vec2 vertClut;
flat in int vertBpp;
smooth in vec4 vertColour;
flat in int vertFlags;

flat in float widthMultiplier;
flat in int widthMask;
flat in int indexShift;
flat in int indexMask;

smooth in float depth;
smooth in float depthOffset;

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

uniform vec3 recolour;
uniform vec2 uvOffset;
uniform float translucency;
uniform float discardTranslucency;
uniform float alpha;
uniform float useTextureAlpha;
uniform sampler2D tex24;
uniform usampler2D tex15;

layout(location = 0) out vec4 outColour;
layout(location = 1) out vec4 outEmission;
layout(location = 2) out vec4 outInterface;
uniform bool uiLayer;
uniform float emission;
uniform bool hdTexture;
uniform bool effectArtworkEnabled;
uniform usampler2D effectDetailTex;
uniform bool uiArtworkEnabled;
uniform bool uiPaletteDetailEnabled;
uniform vec4 uiArtworkBounds;
uniform sampler2D uiArtworkTex;
uniform sampler2D uiSourceTex;
uniform bool spriteFontEnabled;
uniform int spriteFontBlend;

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

  bool translucent = (vertFlags & 0x8) != 0 || translucency != 0;
  bool textured = (vertFlags & 0x2) != 0;
  outColour = vertColour;

  int translucencyMode = int(translucency);
  bool spriteGlyph = spriteFontEnabled && uiLayer && vertBpp == 0;
  float spriteCoverage = 1.0;
  bool spriteStp = false;

  // Textured
  if(textured) {
    vec4 texColour;
    int nativeIndex = -1;
    if(vertBpp == 0 || vertBpp == 1) {
      // Calculate CLUT index
      ivec2 uv = ivec2(vertTpage.x + (vertUv.x + uvOffset.x) * widthMultiplier, vertTpage.y + vertUv.y + uvOffset.y);
      ivec4 indexVec = ivec4(texelFetch(tex15, uv, 0));
      int p = (indexVec.r >> ((int(vertTpage.x + vertUv.x) & widthMask) << indexShift)) & indexMask;
      nativeIndex = p;

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

    if(spriteGlyph) {
      vec2 local = vertUv + uvOffset - uiArtworkBounds.xy;
      if(any(lessThan(local, vec2(0.0))) || any(greaterThanEqual(local, uiArtworkBounds.zw))) discard;
      int expectedIndex = int(round(texelFetch(uiSourceTex, ivec2(local), 0).r * 255.0));
      // An overwritten native nibble uses its current native colour and coverage, never old artwork.
      if(nativeIndex == expectedIndex) {
        vec2 uv = local / uiArtworkBounds.zw;
        ivec2 size = textureSize(uiArtworkTex, 0);
        int authoredIndex = int(round(texelFetch(uiArtworkTex, clamp(ivec2(uv * vec2(size)), ivec2(0), size - 1), 0).r * 255.0));
        spriteCoverage = texture(uiArtworkTex, uv).a;
        uint pixel = texelFetch(tex15, ivec2(vertClut.x + authoredIndex, vertClut.y), 0).r;
        texColour = vec4(vec3(pixel & 31u, (pixel >> 5) & 31u, (pixel >> 10) & 31u) / 31.0, float(pixel >> 15 & 1u) / 31.0);
      }
      spriteStp = texColour.a != 0.0;
      if(useTextureAlpha != 0.0 && !spriteStp) discard;
      if(spriteCoverage <= 0.0 || texColour.a == 0.0 && all(equal(texColour.rgb, vec3(0.0)))) discard;
      bool nativeBlend = spriteFontBlend != 0 && spriteStp;
      // Fully covered authored opaque interiors keep native depth/order. Smooth edges never
      // write an old binary silhouette; they blend later, alongside the caller's STP pixels.
      if(discardTranslucency == 1.0) {
        if(nativeBlend || spriteCoverage < 1.0 || translucencyMode != 0) discard;
      } else {
        if(!nativeBlend && spriteCoverage >= 1.0) discard;
        int requiredMode = nativeBlend ? spriteFontBlend : 1;
        if(translucencyMode != requiredMode) discard;
      }
    }

    // Keep live palette changes and original STP/discard authoritative. The reference
    // is decoded locally from the owner's TIM, never shipped as original game data.
    if(!spriteGlyph && uiLayer && uiArtworkEnabled && vertBpp == 0) {
      vec2 local = vertUv + uvOffset - uiArtworkBounds.xy;
      if(all(greaterThanEqual(local, vec2(0.0))) && all(lessThan(local, uiArtworkBounds.zw))) {
        ivec2 pixel = ivec2(local);
        vec4 source = texelFetch(uiSourceTex, pixel, 0);
        bool visible = texColour.a != 0.0 || any(notEqual(texColour.rgb, vec3(0.0)));
        bool unchanged = !uiPaletteDetailEnabled && all(lessThanEqual(abs(texColour.rgb - source.rgb), vec3(1.0 / 255.0 + 0.000001)))
          && (texColour.a != 0.0) == (source.a != 0.0);
        bool indexedDetail = uiPaletteDetailEnabled && nativeIndex == int(round(source.r * 255.0)) && source.g > 0.0;
        if(visible && (unchanged || indexedDetail)) {
          vec3 restored = texture(uiArtworkTex, local / uiArtworkBounds.zw).rgb;
          if(indexedDetail) {
            // Reuse the authored marker's shading while the live encounter CLUT owns hue,
            // black/discard and STP. An overwritten index cannot borrow old detail.
            float brightness = max(restored.r, max(restored.g, restored.b));
            texColour.rgb = clamp(texColour.rgb * clamp(brightness / source.g, 0.0, 2.0), 0.0, 1.0);
          } else if(any(notEqual(restored, vec3(0.0)))) texColour.rgb = restored;
        }
      }
    }

    // Discard if (0, 0, 0, 0), or if alpha is 0 and we're using texture alpha mode
    if(!spriteGlyph && texColour.a == 0 && (useTextureAlpha != 0 || texColour.r == 0 && texColour.g == 0 && texColour.b == 0)) {
      discard;
    }

    // If translucent primitive and texture pixel translucency bit is set, pixel is translucent so we defer rendering
    if(!spriteGlyph && (discardTranslucency == 1 && translucent && texColour.a != 0 || discardTranslucency == 2 && (!translucent || texColour.a == 0))) {
      discard;
    }

    outColour = clamp(outColour * texColour, 0.0, 1.0);
  } else {
    // Untextured translucent primitives don't have a translucency bit so we always discard during the appropriate discard modes
    if(discardTranslucency == 1 && translucent || discardTranslucency == 2 && !translucent) {
      discard;
    }
  }

  outColour.rgb *= recolour;

  if(spriteGlyph) {
    // Texture alpha remains native even when the primitive has no blend override.
    float nativeAlpha = useTextureAlpha != 0.0 ? (spriteStp ? 1.0 / 31.0 : 0.0) * (alpha != -1.0 ? alpha : 1.0)
      : (alpha != -1.0 ? alpha : (spriteFontBlend == 1 ? 0.5 : 1.0));
    outColour.a = spriteCoverage * nativeAlpha;
    if(translucencyMode == 2 || translucencyMode == 3 || translucencyMode == 4) {
      // Add/subtract hardware blends use ONE: coverage belongs in RGB there. Explicit
      // caller alpha only multiplied RGB in the original texture-alpha path.
      outColour.rgb *= spriteCoverage * (translucencyMode != 4 && alpha != -1.0 && useTextureAlpha != 0.0 ? nativeAlpha : 1.0);
    }
  } else if(alpha != -1) {
    if(useTextureAlpha == 0) {
      outColour.a = alpha;
    } else {
      outColour.a *= alpha;

      if(translucencyMode == 2 || translucencyMode == 3) {
        outColour.rgb *= outColour.a;
      }
    }
  } else if(useTextureAlpha == 0) {
    if(translucent && translucencyMode == 1) { // (B+F)/2 translucency
      outColour.a = 0.5;
    } else {
      outColour.a = 1.0;
    }
  }
  outEmission = vec4(uiLayer ? vec3(0.0) : outColour.rgb * emission, outColour.a);
  outInterface = vec4(uiLayer ? 1.0 : 0.0);
}
