# Independent review

Reviewed base `2b2e95cb5995f7d1ca76b08f12c9a932be134c97` through experiment `1598ae4ad`, then verified corrective commit `0dd80498e`. Reviewers made no source edits and launched no game or desktop window.

## Standards

No hard documented-rule breaches found. The diff preserves license/provenance notices, versions permitted custom assets, keeps controls private, and excludes experimental payloads from normal packaging. Publication remains separate from visual and gameplay acceptance.

Concrete correctness findings in the first commit:

- **P2 — Validation listeners can disappear during collection.** Both listeners were registered without retained references; mod-loader's EventBinding stores weak references. Native reruns intermittently lost geometry refinement or face paint. This explained the committed battle result dropping from 264,064 to 205,344 floats. A temporary diagnostic retaining both listeners and using reachability fences passed all eight combinations even with collection forced before every part: field 149,312; battle 264,064. Replace the invalid report and retain listeners through each combination.
- **P2 — Persistent objects can lose owned face textures.** The ownership setter left the texture nonpersistent. Global texture clearing could retire it even when its owning object persisted.

Judgment call, not a documented-rule violation:

- **Duplicated Code.** Offline and Java code duplicated selected faces and projection constants. Independent fixtures could not establish offline/runtime mapping parity. A shared bounded resource or parity fixture would prevent approved comparisons drifting from runtime.

Follow-up verification at `0dd80498e`: **no remaining concrete findings**. Both listener reachability fences and forced collection before every part were verified. An independent headless rerun passed all eight combinations: stable counts, prepared geometry parity, unchanged source tables and exactly one face texture when enabled. The owned texture now persists through global clearing and is explicitly deleted by its object; its test checks persistence and deletion. Both offline/runtime paths consume the same face-selection/projection JSON, with bounded Java resource size, coordinates, scales and selections. Its values preserve the previous mapping; the builder hash matches the receipt.

## Spec

No concrete spec violation found. Shared framing and all six shape/texture combinations are generated; missing or duplicate outputs fail. The viewer checks required images. Source-face lineage is attached during ModelsHD reading; optional appearance failure retains prepared geometry. Face sampling isolates body UV offsets/material maps. Experimental JAR creation requires the separate init script; normal payload index and installer tasks are unchanged. Public custom assets are committed; unchanged controls are generated only outside the checkout. No original extracted models, ISOs or inference runtimes appear in the diff.

Acceptance remains partial, as explicitly disclosed: animation/material integration and measured Steam Deck performance are not fulfilled by current evidence. Native battle CharHD playback is deferred; broader animation, scene lighting/transitions and physical Deck measurements remain promotion blockers. This is consistent with the experimental scope. Hair, hands and side/ear transitions remain development quality. The reviewer reran all three headless generator fixtures successfully.

Initial Standards: two concrete findings plus one judgment call, all addressed and independently verified. Spec: zero concrete violations; artistic, gameplay and Deck acceptance remain required before promotion.
