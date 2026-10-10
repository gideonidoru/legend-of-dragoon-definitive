# Dart promotion to CharHD 0.3.0

The owner explicitly approved normal CharHD delivery on October 10, 2026 after reviewing the accepted experimental candidate. Reference commit: `29c33e32926f0aaf3d465044a532dd77ce38d2c9`. Integration engine baseline: `f9320b5ef` (current main's native texture/resource fixes). The historical reconstruction directory and receipts retain their original experimental status; this approval selects the exact custom runtime assets, not every abandoned variation.

## Selected assets

Twelve custom part packs retain the preferred body shapes and the reconstructed field and battle head. Two exact UV bindings share the accepted 2048 × 2048 texture, SHA-256 `8e75f9c5297bca7981b9be3a77c3210636fa8a80f2304768c109954536fb4f38`. No geometry or pixel tweak was made during promotion. `runtime-assets/charhd/characters/characters.json` pins every shipped pack, geometry identity, original source identity, ModelsHD baseline handoff, UV and material checksum.

The head has 5,748 triangles / 5,211 vertices. The accepted reference actor has 7,202 field and 8,408 battle triangles. CharHD activates the model and its texture in one optional appearance transaction; disabling CharHD restores the selected ModelsHD or original presentation. It works without ModelsHD enabled. The body atlas retains its separate texture ownership. The face sampler now uses unit 7 to coexist with current FxHD's integer detail sampler on unit 6.

## Verification environment and commands

Mac Studio `Mac17,14`, Apple M5 Max; macOS 27.0.1 (`26A434`); Corretto Java 25, supported Gradle 9.1 wrapper. Original sources, test runtime and all native captures remain outside Git.

```sh
./gradlew --no-daemon --console=plain charHdTests modelsHdTests charhdJar
./gradlew --no-daemon --console=plain charHdReconstructionProbe \
  -PcharFiles=/PRIVATE/game/files -PcharReport=/PRIVATE/bundled-probe.json
./gradlew --no-daemon --console=plain \
  -I /PRIVATE/party-aa-live-01/live.init.gradle dartLiveTrial
./gradlew --no-daemon --console=plain build deliveryTest stageDefinitivePackage
python3 scripts/verify-charhd-package.py build/definitive/package
```

- Twelve CharHD tests and 32 ModelsHD tests pass. New regressions cover atomic geometry/material composition, indexed and mapped body textures, exact source identity, declared baseline handoff, unknown custom owner priority, native index fallback, malformed/corrupt resources and unchanged source packets.
- Twenty-four bundled CPU combinations pass: field/battle × indexed/simulated RGBA/mapped × CharHD off/on × ModelsHD off/on. These use bundled resources with no external override directory or experimental JAR. Source CPU geometry and packets remain unchanged; data are finite and the exact 2K head material activates only when CharHD is on.
- Six native Mac game checks pass: fresh state, battle load, Guard HP behavior, campaign preset behavior and actual battle capture. The rendered body-atlas head and source mesh both assert the 2048 texture. The isolated runtime contains only ordinary ModelsHD and CharHD packages and no external model packs. This verifies the promoted activation route rather than the old experimental route.
- Supported build, package staging and 345 delivery checks pass (338 passed, seven opt-in checks skipped); no failures.
- The normal installer package is checked against the selected resource inventory, exact bytes/hashes, production adapter and all license notices. Authoring inputs, GLBs, weights, experiment resources and original controls are excluded from the runtime JAR.

The source probe initially logged a missing font in its empty working directory; CPU checks still passed. Native test logs retain existing unused-shader-uniform, Discord/native-access and integer-texture diagnostics. This is not a claim of warning-free logs.

## Limits and continuing work

The accepted head is a meaningful visual reference, not a finished full-body AA character. The crown/rear topology, complete costume, fingers/grips, footwear and weapon remain targets. Native attack/damage/victory/death coverage, field/world gameplay, broad third-party texture compatibility and physical Deck performance are not proved by these checks. A 2K RGBA base texture is 16 MiB before GPU overhead; measure actual multiple-actor residency before applying that size to the entire roster.

The remaining eight heads and all nine complete body/weapon upgrades are active authoring scope in [the party plan](../../../../docs/definitive/PARTY_RECONSTRUCTION.md). No new release is published for this source iteration; a downloadable installer update requires exact-source CI and the complete existing publication gate.
