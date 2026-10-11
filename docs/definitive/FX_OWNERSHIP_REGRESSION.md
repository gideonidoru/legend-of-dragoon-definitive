# Indexed FX artwork ownership

A battle combatant can sample a private `Gpu.vramTexture15` on texture unit1
(`BattleEntity27c.renderBttlModel`). Global FX artwork is stored in a companion
texture owned by `GameEngine.GPU`. Previously the base queued model enabled that
global companion whenever unit0 was empty. Matching native palette indices could
therefore apply unrelated global interpolation weights to a private image/CLUT.

The queue now enables and binds global indexed detail only when its native VRAM
owner is the default global GPU or an explicit reference to that same GPU texture.
Private indexed overrides retain native artwork. Both binding and shader selection
use the same predicate; UI and explicit unit0 artwork remain excluded as before.
The guard adds pointer comparisons, with no driver queries, new textures, uploads,
passes or frame buffering. It does not add FX artwork to private VRAM owners.

## Opt-in windowless regression

`NativeFxOwnershipProbe` uses original synthetic TIMs and a fixed textured quad.
It exercises the published `Gpu.uploadEffectTim`/reselection, actual pooled
`QueuedModelBattleTmd.acquire`, shader/texture selection and draw, plus the shipping
battle vertex/geometry/fragment shaders. The scene uses the shader's orthographic
projection; no real battle entity, camera sequence, extracted disc data or live
game is loaded.

The same queue object is reused across14 draws. Controls cover default global and
explicit global owners, matching and differing private indices, private/global UI,
cached global/private transitions and FX disable/re-enable. Native unit1 bindings
are verified against the selected texture object. Full-frame pixel comparisons
check preserved intended detail, private native RGBA and unchanged native words
and CLUTs. Reports and exit status are decided after GL resource/error cleanup and
both CGL unbind/destroy statuses are checked.

```sh
clang -dynamiclib -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/darwin" \
  -framework OpenGL scripts/headless-fx-ownership-context.c \
  -o /tmp/libfx-ownership-context.dylib
./gradlew -I gradle/native-fx-ownership-probe.gradle nativeFxOwnershipProbe \
  -PfxOwnershipContextPath=/tmp/libfx-ownership-context.dylib \
  -PfxOwnershipReportPath=/tmp/fx-ownership-candidate.json
```

The executable is separate from ordinary tests. A supported accelerated macOS CGL
context is required; unavailable context is a failure, not acceptance. It creates
no window. JDK25 and the host-native dependency JARs are required.

## Published negative and isolated overlay

Compile this same probe against the exact published engine and matching libraries;
run it without the fix first. Then compile only `QueuedModel.java` into an overlay
directory and prepend that directory to the same classpath. Keep current shaders
on disk pinned and unchanged for both runs. `DEPS` denotes macOS native libraries
when running CGL on macOS, even if the engine bytecode came from a Linux package.

```sh
mkdir -p /tmp/fx-owner-probe /tmp/fx-owner-overlay
"$JAVA_HOME/bin/javac" -cp "$PUBLISHED_ENGINE:$DEPS/*" -d /tmp/fx-owner-probe \
  src/test/java/legend/definitive/effects/NativeFxOwnershipProbe.java
"$JAVA_HOME/bin/java" -Djava.awt.headless=true --enable-native-access=ALL-UNNAMED \
  -cp "/tmp/fx-owner-probe:$PUBLISHED_ENGINE:$DEPS/*" \
  legend.definitive.effects.NativeFxOwnershipProbe \
  /tmp/libfx-ownership-context.dylib /tmp/fx-owner-published.json
# Expected nonzero exit: unrelated FX changes512 private pixels from green to cyan.
"$JAVA_HOME/bin/javac" -cp "$PUBLISHED_ENGINE:$DEPS/*" -d /tmp/fx-owner-overlay \
  src/main/java/legend/core/renderer/QueuedModel.java
"$JAVA_HOME/bin/java" -Djava.awt.headless=true --enable-native-access=ALL-UNNAMED \
  -cp "/tmp/fx-owner-overlay:/tmp/fx-owner-probe:$PUBLISHED_ENGINE:$DEPS/*" \
  legend.definitive.effects.NativeFxOwnershipProbe \
  /tmp/libfx-ownership-context.dylib /tmp/fx-owner-candidate.json
# Expected zero exit: private pixels remain native; intended global detail persists.
```

The 2026-10-11 published negative uses source
`0af4f997e27f49783180cb2ff1c5a04012467289`, engine SHA-256
`33009ed625ca54e9581425e29aa612c608d4e548a1c5c2c9dcd7630668f53dc7`.
It changes all512 private pixels when native indices coincide, while differing
indices and UI controls change zero. The isolated queued-model overlay keeps all
private pixels native, while preserving512 intended global changes. Both reports
have zero GL/cleanup/native errors on Apple M5 Max, OpenGL4.1 Metal91.7.
The full compiled candidate and documented Gradle entry point also pass all14
draw controls. Compilation and the headless delivery suite pass:450 cases, zero
failures/errors, ten skips; the dependent character suite passes all12 cases.

This is a separate colour/ownership defect. It does not establish the cause of
Steam Deck scene scaling, missing faces or sluggish battle transitions. The
windowless Mac fixture is not GLES GPU, full battle/campaign visual, or Deck
performance acceptance. Existing native/FX visibility and STP paths remain
subject to their dedicated tests and physical-device checks.
