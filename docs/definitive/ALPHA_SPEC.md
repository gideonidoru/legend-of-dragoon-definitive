# Ordered alpha implementation — 2026-10-09

Owner order: (2) simple Deck installation and safe updates, (1) Definitive/Faithful presets, (3) small model-texture upscaling pilot. Start revision: b9671558b9253015f58ac09c8f4d62c138c33789. Assumed prior scene/Deck checks are planning assumptions, not new verification evidence.

## Installation/update acceptance

A small transferable installer linked as the primary README download. On Deck Desktop Mode it obtains our platform release package, falling back to a recursive source clone / supported build when no release exists. A polished three-step workflow installs our build and HD mods, selects raw images or ZIP/RAR/7z archives, then optionally adds a backed-up non-Steam shortcut. Unrelated archive entries are left out. Separate Play-first launcher automatically checks our releases in the background; mod/artwork choices and restore remain secondary.

An installable, allowlisted Linux x64 package and guided Desktop Mode manager; no terminal typing in normal setup. Select a destination and privately import supported four-disc inputs with actionable errors. JDK 25 bootstrap must verify the official checksum, preserve an existing runtime and allow offline reuse. Stable Steam target. Each package records platform, upstream/source revisions, paired mod sources and per-file SHA256; corrupted, extra, traversal and unsupported-platform content rejected before activation. Never merge a new package over private data. Serialize operations and prevent updating while the managed game runs. Rollback pairs the prior engine with a verified pre-update copy of saves/settings/custom mods; newer data retained separately. Extracted files isolated per engine version. The owner subsequently requested a downloadable primary installer; publish an alpha installer and paired platform packages after checks, with clear physical Deck validation limits. No fabricated Deck validation.

## Preset acceptance

Built-in selectable Definitive and Faithful campaign presets through existing APIs. Explicit audited values for timing, rewards, encounters, inventory and convenience settings. Presentation independently selectable; no silent rewriting of existing campaigns or irreversible reward claims. Preserve other built-in presets and user/global settings. Tests establish detached preset construction, correct values and save/config round trips. Retail-fidelity limits stated honestly.

## Upscaling pilot acceptance

Offline reproducible 2x texture tool with original/nearest comparison and a licensed synthetic fixture, source/output hashes, limits and alpha/palette handling. Small private character/enemy inputs; no derived retail textures in Git. Runtime pilot through the existing field-object texture event, with exact mapping, validated dimensions/hashes, opt-in and missing/invalid-pack fallback. No geometry/renderer rewrite or automatic AI enhancement claims; battle texture coverage and Deck performance only claimed with evidence.
