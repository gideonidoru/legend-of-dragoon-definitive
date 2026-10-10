# SMAA 1x High

Vendored from https://github.com/iryoku/smaa at commit
`71c806a838bdd7d517df19192a20f0c61b3ca29d`, under the accompanying MIT license.
Copyright Jorge Jimenez, Jose I. Echevarria, Belen Masia, Fernando Navarro and Diego Gutierrez.

`SMAA.hlsl` is the original cross-platform implementation. Local changes affect formatting and comments only:
CP1252 text is normalized to UTF-8, line endings to LF, trailing whitespace is removed, and five ASCII-logo lines have
` [logo]` appended to prevent trailing backslashes from extending line comments under
strict shaderc preprocessing. Algorithm and constants are unchanged.

`gfx/textures/smaa/AreaTex.png` contains the exact upstream `AreaTex.h` RG bytes,
and `SearchTex.png` contains the exact upstream `SearchTex.h` R bytes. Unused channels
are zero and alpha is 255. Neither gamma metadata nor lossy compression is added.
Reproduce with `python3 scripts/import-smaa-lookups.py /path/to/pinned/smaa`.

The three GLSL 330 wrappers integrate interface coverage protection and the existing
edge-strength setting. They use spatial SMAA 1x; no temporal history or projection jitter.
The lookup textures and this license ship through the existing graphics packaging path.
