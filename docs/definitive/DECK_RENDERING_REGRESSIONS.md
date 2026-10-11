# Camera scenery loss and retained-frame scaling

The public engine at source `0af4f997e27f49783180cb2ff1c5a04012467289`
was checked against the rendering fixes based on `696cea631db511041ed7ab6466f03da355eebb7c`.
The native regressions use a windowless macOS CGL context, actual production shaders
and the production renderer draw listener. They do not run the game or establish
physical Steam Deck acceptance.

## Retained simulation frames must restore the scene viewport

The final presentation pass sets the viewport to the output window dimensions.
Before this fix, the scene viewport was restored only by `pre()`, which runs when
a gameplay tick advances. A presentation with zero gameplay steps instead reused
the window viewport while drawing into the internal render target. Different
internal/output dimensions made a stationary scene alternate scale and position.

The published engine fails the real renderer regression at presentation 3 with
zero gameplay steps, with SMAA disabled:

- Internal target: 2304x1440; output: 1280x800.
- Inherited scene viewport: `[0, 0, 1280, 800]`.
- Reference scene: 1555200 colored pixels, bounds `0,108..1781,1439`.
- Faulty retained redraw: 480000 colored pixels, bounds `0,60..989,799`.

`RenderEngine` now restores the scene viewport immediately before binding and
drawing the scene target on every presentation. The existing legacy forced-4:3
viewport calculation is shared with `pre()` rather than replaced. The simulation,
FMV, audio and frame-history clocks are unchanged.

The optional regression covers 480p, 1440p and native internal resolution at
1280x800 output, modern and legacy forced-4:3 projection, SMAA off/on, 300 queued
placement markers across the 128-entry transform boundary, zero-step redraws,
normal updates and catch-up updates. Scene and final output bytes must remain
identical for the same stationary scene. The corrected renderer passes 12 cases,
960 presentations and 271 zero-step presentations, with zero GL errors.

Run explicitly on macOS with the existing windowless JNI context helper:

```sh
./gradlew nativeViewportReplayProbe -I gradle/native-renderer-probe.gradle \
  -PrendererContextPath=/absolute/path/to/librenderer-context.dylib
```

The context library can be built from `scripts/headless-renderer-context.c` using
the JNI headers from the local JDK and the macOS OpenGL framework. The probe prints
the engine code source so a negative control can be checked against a published JAR.
It is never launched by ordinary builds or gameplay tests.

## Preserve visible faces in modern projection

The shared TMD geometry shader rejected an entire face if any corner crossed the
PS1 GTE threshold H/2, including modern perspective and orthographic projection.
Modern perspective has its own much nearer GPU clip plane. During close attack
camera movement, still-visible floor and prop faces could therefore disappear.

The whole-face GTE rejection now applies only to PS1 perspective mode 1. Modern
mode 2 and orthographic mode 0 leave visible portions to homogeneous GPU clipping.
The adjacent fourth corner retains the same legacy rejection behavior. Texture
sampling, palette/STP rules, fragment depth and mesh data are unchanged.

```sh
clang -framework OpenGL scripts/headless-tmd-clipping-probe.c -o /tmp/tmd-clipping-probe
/tmp/tmd-clipping-probe gfx/shaders /absolute/path/to/published/gfx/shaders
```

The regression uses both field and battle production vertex/geometry/fragment
pipelines, native 4-bit indexed textures and actual RGB HD textures, all three
projection modes, triangles and an adjacent quad corner, visible faces above/below
the old threshold, hardware-near-plane crossings and fully hidden faces. Legacy
cases and unaffected visible/hidden controls are byte-identical to the published
shaders. Published shaders fail 36 of 72 cases; corrected shaders pass all 72.

## Acceptance limits and remaining work

Both failures are reproduced production defects with matching fixes. The camera
control is synthetic geometry rather than a captured Dart attack, and the viewport
control is stationary placement markers rather than a recorded field dialogue.
The physical Deck must still confirm that stationary scenes/dialogue no longer
shrink/flicker and battle floor/props stay visible through Dart and enemy attack
camera moves. Cold battle entrance responsiveness still requires a measured
real-scene load/profile; these fixes do not establish its performance improvement.

The public FMV fix was separately confirmed working by the player on Steam Deck.
Shader GLES transpilation and headless simulation/resource/cinematic cadence tests
remain separate from actual GLES or Deck GPU execution. Independent depth-clear
work is owned by the rendering integration stream and is not part of this patch.

## Battle artwork loading uses the existing image cache

Battle atlas selection calls `ArtworkResources.image` on the renderer thread.
It previously decoded PNGs with ImageIO and converted pixels individually, bypassing
the engine's existing bounded native decoder/cache. The loader now uses `PngAssets`,
respects the image-cache setting, releases its decode lease, and returns a separate
owned RGBA array. Header/dimension bounds and the source/payload/coverage validation
performed by each caller remain in place. No new cache budget is introduced.

Private measurements used three actual published battle atlases and matching local
source data. Each full-load trial began with an empty decoded cache and an already
initialized native decoder. Median CPU wall times for trials 1–3 were:

| Stage directory | Published full atlas load | Corrected full atlas load |
| --- | ---: | ---: |
| 2498 | 52.211 ms | 21.667 ms |
| 2499 | 95.196 ms | 24.638 ms |
| 2500 | 36.445 ms | 9.203 ms |

These are CPU asset-loading measurements on an Apple M5 Max, including integrity
checks, rather than Deck frame times or GPU upload times. Native decoder startup
was measured separately and is not represented as a first-launch speedup. Repeated
loads can reuse the shared decode; the first cache-reuse regression fails on the
old loader and passes on the corrected one. Synthetic RGB/RGBA pixel oracles cover
transparent RGB, exact alpha/STP, malformed input, independent result ownership,
cache disabling and lease cleanup. All 71 published battle atlases pass their complete source/payload/coverage checks.
All 190 embedded EnvHD PNGs (345641472 pixels across battle atlases, skies and world
artwork) are additionally byte-identical to the previous decoder's RGBA output. Full cold battle entry—including
mesh construction, first GPU upload and scripted transition—still needs physical
Deck profiling and visual acceptance.
