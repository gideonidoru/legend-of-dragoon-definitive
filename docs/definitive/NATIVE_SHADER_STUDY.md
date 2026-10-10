# Native shader material study

The test-only `nativeBattleShaderProbe` runs the checked-out battle vertex, geometry and fragment shaders in a hidden SDL/OpenGL context. It does not start the game, create queued models, install character assets or change engine behavior. The default build runs only the CPU oracle fixtures; GPU execution is explicit. Linux CI runs the GPU cases under Xvfb with software OpenGL, which is a correctness check rather than Steam Deck performance evidence.

```sh
./gradlew nativeBattleShaderProbe -PshaderProbeOutput=/absolute/new/report-directory
```

The report directory must be new and its parent must exist. Reports retain the GPU vendor/renderer/version, exact shader hashes, uniform block sizes, pixel comparisons and PNGs. The macOS invocation requests background-only SDL startup, uses a hidden window and runs on the first native thread. No window is shown or swapped.

## Original controls

Eight original synthetic cases cover two conflicting palettes, transparent zero, visible black with the PSX translucency bit, opaque and translucent pass selection, lit/unlit textures and untextured lighting. Both indexed VRAM and RGBA atlas paths are compared with a separate bounded CPU oracle. Acceptance requires identical coverage and no more than one 8-bit channel step from the 5-bit-to-RGBA conversion. Four CPU tests verify known coverage/black counts, lighting, pass alpha, and detection of lost black, wrong colors and malformed readbacks.

The local hidden-context run on October 10 used Corretto 25, Gradle 9.1.0, LWJGL 3.4.3 and Apple M5 Max OpenGL `4.1 Metal - 91.7` on macOS 27.0.1. All eight cases passed. Indexed samples matched the oracle exactly; RGBA samples differed by at most one channel step. Coverage and visible-black counts matched exactly. Cleanup completion reports only the probe's owned-resource teardown, not game-actor lifecycle or leak-free runtime behavior.

## Private character comparison

Haschel's unchanged 791-triangle model was captured with original per-corner TMD normals, primitive flags, colors, quad adjacency and palettes. Recorded source poses supplied two states (combat motion 0/tick 0 and motion 5/tick 5), each at front, side and rear yaw. A controlled orthographic camera and three light directions were shared by the indexed original, nearest-packed control and stronger 4× artwork. These are GPU harness captures, not in-game lighting or gameplay validation. Derived payloads and images stay outside Git.

The first unchanged-shader comparison held one of six views: one pixel in the raised-arm rear view differed by 11 channel steps. Coverage remained identical. Diagnostic GPU variants identified primitive 138 and a source V of `49.99998474121094`, just below row 50. The normalized atlas lookup crossed the corresponding row boundary. This initial result is retained; the acceptance tolerance was not widened.

A private fragment-lookup prototype retains the original source UV and computes `floor(sourceUV × scale)` before adding the integer atlas origin, with bounds limited to the material's tile. It uses all 4× texel positions rather than quantizing back to source resolution. All six controls then passed: 524,348 covered pixel samples, identical coverage and no more than one channel step. The stronger atlas also retained coverage in all six captures. Pixel difference alone is not evidence of better art.

The prototype changes shader strings only in the private harness. Checked-out engine shaders remain byte-identical. It is not a native character importer or a shipped material feature. Later integration needs an explicit validated material packet, bounded palette mapping, actor-owned meshes/textures, original-actor fallback, blend ordering, shader/projection compatibility and physical Deck performance/input acceptance. The [native material integration plan](NATIVE_MATERIAL_INTEGRATION.md) and [Deck protocol](DECK_TEST_PLAN.md) remain controlling gates.

## Reconstructed head and refined body

The next private run captured 30 images: the three original-model material controls and two reconstructed-mesh treatments at the same six pose/view combinations. The candidate combines the textured 12,240-triangle head, joint-pinned body refinement and two bound rear ribbons, totaling 18,068 triangles. The original skirt remains because previous refinement increased its intersections. Original versus painted arm colors are separate experiments. The reconstructed head is explicitly opaque; its original translucent polygons have not been mapped to the new surface.

All six original-model controls still pass the strict comparison: 524,348 covered samples, zero coverage differences and at most one channel step. This establishes the material control under the shared camera, not equivalence of the changed geometry. Candidate captures completed on the Mac Studio GPU with no reported GL errors and explicit owned-resource teardown. A first restricted-context attempt could not initialize a display; its failed report is retained separately from the successful host-context run.

The side-by-side review shows clearly defined eyes, brows, nose, ears, moustache and hair, including a 180 × 320 full-body sample. The arms are smoother, but the shoulder outlines and clothing still expose the original segmented construction. Uniform arm paint is too simple to match the face's material detail. Hair, collar transitions, facial proportions and costume treatment remain art-review work. These observations are a direction check, not community approval or commercial art acceptance; no candidate is installed or shipped.

The combined 2,048 × 2,432 RGBA atlas occupies 19,922,944 bytes before driver overhead. The deliberately expanded adjacency stream occupies 6,938,112 bytes, excluding auxiliary GPU buffers. These are payload sizes, not measured residency, frame times or a Deck budget. The private reader retains a 32 MiB scene-file cap and permits a bounded 24 MiB texture payload for this study. Shipping requires independent texture sizing, efficient mesh storage, animation/lifetime correctness and same-scene Deck measurements. The head bake, input matrices, private helper hashes, payload identities and captured-pixel comparisons remain outside Git.

The test source at [`bee207319cead455afc925f29c9a4032a2105314`](https://github.com/gideonidoru/legend-of-dragoon-definitive/commit/bee207319cead455afc925f29c9a4032a2105314) passed all three jobs in [run 38034609351](https://github.com/gideonidoru/legend-of-dragoon-definitive/actions/runs/38034609351), including the Linux software-OpenGL controls. Test helpers are excluded from the shipped engine JAR. This did not require a new installer release.

| Checked shader | SHA256 |
| --- | --- |
| `battle_tmd.vsh` | `466cff5a65ac167baddf639da1f34539d9d4005140e4617fc27f8a2d32d5883f` |
| `tmd.gsh` | `96b7ba7f5dfad00947f9780feffecacf98479f81b4afe3d70329b2cd48f6ee34` |
| `battle_tmd.fsh` | `23e8531f98fa898678d3059eee3ea2fe7dd719ea8bf9bbf9a54bf669981b4d03` |
