# Depth-clear boundary regression

OpenGL and OpenGL ES depth clears respect the depth write mask. A batch can inherit
disabled depth writes from the preceding translucent pass; `renderBatch` clears
before its opaque pool restores writes, and an empty opaque pool never restores
them. The old clear therefore leaves the preceding depth values in place.

Both backends now enable writes for a requested depth clear and restore disabled
writes if the API remains in a translucent mode. Colour-only clears leave depth
untouched. The cached translucency, blend function and blend enable state remain
unchanged. This adds no driver state queries or additional rendering passes.

## Opt-in actual GPU regression

`NativeDepthClearProbe` creates a windowless accelerated macOS CGL context. It
uses actual `GlApi` textures/framebuffers and reads depth back from the GPU. It
also invokes the real `RenderEngine.renderBatch` with empty pools, preserving the
inherited translucent state across shared-depth framebuffer/batch reuse.

The 26 checks cover ordinary opaque clears, inherited disabled writes, all three
supported translucent modes, continuing translucent draws' intended state,
colour-only clears, real batch boundaries, repeated shared-depth reuse and the
following opaque pass. Every check records depth, write mask, blend state and GL
errors to JSON; any failing check exits nonzero. The final JSON and exit verdict
are decided after all GPU resource cleanup, GL error validation and native context
teardown. Individual cleanup exceptions and both CGL unbind/destroy statuses are
recorded; remaining cleanup steps still run if an earlier step fails.

Ordinary builds/tests do not run this executable or create a graphics context.
On macOS with JDK 25:

```sh
clang -dynamiclib -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/darwin" \
  -framework OpenGL scripts/headless-depth-clear-context.c \
  -o /tmp/libdepth-clear-context.dylib
./gradlew -I gradle/native-depth-clear-probe.gradle nativeDepthClearProbe \
  -PdepthClearContextPath=/tmp/libdepth-clear-context.dylib \
  -PdepthClearReportPath=/tmp/depth-clear-candidate.json
```

To check failure reporting, add `-PdepthClearInjectCleanupFailure` (or append
`--inject-cleanup-failure` to the direct Java invocation). The native helper really
destroys its context, then substitutes `kCGLBadContext` for a successful destroy
status. This controlled fixture never passes an invalid pointer to the driver.
All 26 rendering cases still pass, but final JSON records `passed=false`,
`cleanup.passed=false`, `cleanup.injectedFailure=true` and the destroy error; the
process must exit nonzero. Normal runs record both native errors as zero. The
fixture tests propagation of a teardown failure through the real JNI return path
and final report, rather than pretending a rendering case failed.

## Exact published negative control

Compile the same probe against the published engine JAR and matching dependency
JARs; run it with that engine first on the classpath. `DEPS` denotes the host-native
library directory, not a Linux native library directory on macOS:

```sh
mkdir -p /tmp/depth-clear-probe
"$JAVA_HOME/bin/javac" -cp "$PUBLISHED_ENGINE:$DEPS/*" \
  -d /tmp/depth-clear-probe \
  src/test/java/legend/definitive/rendering/NativeDepthClearProbe.java
"$JAVA_HOME/bin/java" -Djava.awt.headless=true --enable-native-access=ALL-UNNAMED \
  -cp "/tmp/depth-clear-probe:$PUBLISHED_ENGINE:$DEPS/*" \
  legend.definitive.rendering.NativeDepthClearProbe \
  /tmp/libdepth-clear-context.dylib /tmp/depth-clear-published.json
```

The 2026-10-11 negative control used source
`0af4f997e27f49783180cb2ff1c5a04012467289` and published engine SHA-256
`33009ed625ca54e9581425e29aa612c608d4e548a1c5c2c9dcd7630668f53dc7`.
It failed 22/26 checks. Placing only the fixed `GlApi`/`GlesApi` classes ahead of
that same engine changed the result to 26/26 passing checks. Both runs reported
zero GL errors on Apple M5 Max, OpenGL `4.1 Metal - 91.7`.
The full compiled candidate also passed all 26 GPU checks. Both backends compiled;
the headless delivery suite reported 450 cases, zero failures/errors and 10 skips,
and its dependent character suite passed all 12 cases.

This is a depth-clear regression result, not proof that this defect caused the
reported Steam Deck flicker. CGL exercises desktop OpenGL; OpenGL ES compilation
and matching source changes do not establish GLES GPU behavior. Physical Steam
Deck gameplay, attack-camera transitions, stationary dialogue, and cold/hot
performance remain separate acceptance checks.
