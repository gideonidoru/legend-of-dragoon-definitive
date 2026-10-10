# Dart hair trial: rejected for inclusion

This trial is on `experiment/dart-hair-rebuild`. It is **not an accepted improvement** and must not enter main, the installer or a release. The preferred face/bandana experiment remains at `c49ef65e6` on `experiment/dart-modelshd-charhd`. No main or release defaults changed.

## What failed visually

The owner rejected the test images. The initial displacement pass pinched existing spikes. A procedural material produced stripes; correcting atlas dimensions and replacing that material did not solve the panel-like construction. The subsequent volumetric trial adds ten field locks, thirteen battle locks and a rounded crown on the original head bone. It loads correctly, but has conspicuous root joins, excessive crown volume and an artificial wig-like silhouette. The generated strand paint also reads too much like woven fibers. Increasing triangle count has not resolved these problems.

The offline renderer is unlit and double-sided. It is useful for inspecting UV addressing and silhouette, but cannot substantiate final character appearance. Generated material images are texture inputs, not model comparisons. An actual battle trial confirms integration; its original game textures and deliberately rotated/scaled inspection pose are not a finished Definitive scene.

## Preserve the useful work

`hair-trial/parts` contains only the two changed custom head packs. `models/parts` retains the previous twelve preferred face/body candidates. The opt-in JAR on this branch includes the trial head material; use the trial packs explicitly for a coherent trial. Neither payload is included by ordinary installer/package tasks.

The shared geometry identity moved into the engine because actual mod loading isolates each mod's classes. The earlier CharHD-to-ModelsHD class import failed in a real battle despite passing a shared-classpath unit test. The isolated-loader regression and real battle trial now cover that failure. This fix is useful independently of the rejected hair art, but remains experimental until separately reviewed and approved for promotion.

## Next art candidate and review standard

Rebuild the visible locks deliberately around Dart's established silhouette, with narrower connected roots, controlled overlap and a crown fitted to his skull. Resolve the geometry/material transitions rather than adding subdivision. Preserve the red bandana, the accepted facial alignment, the head pivot and compatibility with body texture replacements. Hair must look coherent from front, three-quarter, side and rear before expanding the work.

The next presentation must compare the preferred `c49ef65e6` appearance against the new candidate. Match framing, pose, lighting and texture treatment. Include a whole-character view at game scale, a useful close-up and a short idle/attack sample in the actual renderer. Keep offline mesh inspection separate and clearly labeled. Do not present failed candidates as improvements or use a generated concept image as evidence of a working model.

Promotion still requires explicit owner approval, production CharHD material integration and physical Steam Deck performance evidence. Technical integration, visual acceptance and device validation are separate gates.
