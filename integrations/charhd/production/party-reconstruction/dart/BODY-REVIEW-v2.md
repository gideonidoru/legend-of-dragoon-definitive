# Dart body draft review

Reviewed changes from `ad19a817149f4393f203e3a4966b113ede871a23` through `24f3b903f25564e357c2610881db95e2eeead8ab`, plus the follow-up closest-triangle refactor. Separate read-only reviewers inspected code standards and the character requirements. This is a development milestone; the complete Dart body objective remains open.

## Standards

No documented violations or confirmed Java ownership defects were found. One P3 clarity finding in anatomical fitting was fixed by extracting `closest_native_triangles` and naming the generated-face, part-local-triangle and global-native-triangle index spaces explicitly. All 16 annotated battle packs, UVs and the texture remained byte-identical after regeneration. The follow-up review found no new issues.

## Spec

- **P1 — Complete articulated Dart remains unfinished.** The plan requires preserving joint/attachment boundaries while rebuilding hands, grips, boots and weapons. Native captures exposed open rigid partitions and detached regions in moving poses. Correct joint ownership, continuous or deliberately overlapping closed surfaces, grip anchors and rear/collar materials before presenting this as the complete approved Dart. This is an acknowledged authoring blocker.
- **P2 — Full native acceptance remains partial.** The plan requires native idle, attack, damage, victory, death and scene reloads, additional texture owners and unknown custom owners. Current evidence is a 24-combination CPU construction matrix, seven native smoke/construction tests and 21 scripted stored-pose captures. Full field gameplay, action/scene coverage, native alternate-owner combinations and physical Deck validation remain pending. Existing synthetic ownership tests do not replace those gates.

The draft preserves accepted runtime selections, distinguishes image approval from mesh acceptance and publishes custom studies without original game data. No unauthorized promotion or unrequested runtime behavior was identified.

Summary: Standards **0 unresolved findings**; Spec **2 incomplete requirements**, worst **P1 moving body continuity**. No main/release promotion.
