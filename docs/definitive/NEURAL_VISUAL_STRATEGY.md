# Neural visuals and the modern retro target

Research checked on 2026-10-09 against primary vendor documentation, research papers and local source at `224497b2f31ba4262d454e920daa5cea9211c1cf`. Vendor support below is a dated snapshot, not a permanent compatibility promise. This document changes no renderer behavior or installer payload.

The owner's target is a **2026 AA game with a polished, intentional retro style**: recognizable original characters, much clearer faces, convincing differences between skin/fabric/metal, controlled smoother silhouettes, painted environments and crisp readable interfaces. The earlier subtle texture studies did not meet that target. A neural technique is useful when it produces a visibly better, faithful result; its name or scale factor is not acceptance.

## Recommended direction

**Proposal:** use neural methods aggressively in the offline authoring pipeline, review and correct their results, then ship coherent conventional assets. Pair those assets with small, measured native rendering improvements. Keep real-time neural image synthesis as a separate research option rather than a dependency for the Deck edition. This preserves the user's visual ambition while avoiding a renderer rewrite as the first step.

These are different jobs:

| Method | What it can change | Project decision |
| --- | --- | --- |
| Offline neural super-resolution | Enlarges/restores texture imagery using a learned model | Already studied privately; useful foundation, insufficient by itself for the requested faces and forms |
| Offline neural-assisted reconstruction | Proposes new painted details/material maps or mesh forms, followed by deliberate correction | Highest priority for a visibly modern character; candidate detail must match the original design across views |
| Runtime spatial/temporal upscaling | Reconstructs output resolution from a cheaper rendered frame | Evaluate after profiling; it does not author missing geometry or guarantee a new material aesthetic |
| Runtime generative neural rendering | Generates displayed appearance, including inferred lighting/material detail, conditioned on engine data | Real technology, but no supported Deck/OpenGL integration established here; optional bounded research |
| Neural texture compression | Encodes/decompresses authored texture sets | A storage/memory technique; it does not restore missing design detail |

The existing offline tool is based on the [Real-ESRGAN ncnn Vulkan implementation](https://github.com/xinntao/Real-ESRGAN-ncnn-vulkan), which accepts image files, model selection, scale and tiling. Its use of Vulkan for offline processing does **not** convert Severed Chains into a Vulkan game. Our [palette-aware packer](../../scripts/pack-model-materials.py) produces ordinary PNG assets with pinned processing provenance and preserved original STP/discard semantics; no neural network executes in the game for those candidates.

[Material Anything](https://arxiv.org/abs/2411.15138) is a primary research example of diffusion-based, multi-view PBR material generation with UV-space refinement. It demonstrates a relevant class of offline methods, not a tested replacement for our pipeline. We have not checked its code/weight redistribution terms, run it on these characters, or established faithful identity, suitable UVs or performance. Any selected model must undergo that evaluation before adoption.

## Current vendor boundaries

Valve specifies the Deck GPU as **8 RDNA 2 compute units**. That is the target hardware, not an RTX GPU or an RX 7000/9000 discrete card. [Valve specifications](https://www.steamdeck.com/en/tech/deck).

| Technology | Verified primary-source requirements and role | Consequence for this project |
| --- | --- | --- |
| DLSS 5 | NVIDIA's September release describes 3D-guided generative rendering on GeForce RTX 50 Series. Its research description conditions inference on rendered color, engine motion vectors, temporal state and artistic controls. | No officially supported local Deck path. This is a useful reference for controlled, temporally stable appearance enhancement, not a Deck feature we can promise. [NVIDIA release](https://www.nvidia.com/en-us/geforce/news/dlss-5-3d-guided-neural-rendering/), [NVIDIA research](https://research.nvidia.com/labs/adlr/DLSS5/) |
| DLSS 4/4.5 family | NVIDIA distinguishes reconstruction, ray reconstruction and frame-generation features with different RTX compatibility. | Do not describe every DLSS feature as RTX 50-only; the Deck still is not a supported RTX device. [NVIDIA technology overview](https://www.nvidia.com/en-us/geforce/technologies/dlss/) |
| ML AMD FSR Upscaling 4.1.1 | Current GPUOpen page lists RX 7000/9000 discrete GPUs, HLSL/Shader Model requirements, DirectX 12 and Windows. AMD's product FAQ announces RX 6000 support in 2027. | No official Deck APU/SteamOS/OpenGL support established. A future RX 6000 announcement is not evidence of Deck support. Recheck before any adoption. [GPUOpen requirements](https://gpuopen.com/amd-fsr-upscaling/), [AMD compatibility FAQ](https://www.amd.com/en/products/graphics/technologies/fidelityfx/super-resolution.html) |
| Analytical FSR 2/3 | FSR 3's official implementation lists DirectX 12 and Vulkan. FSR 2's integration requires color/depth/motion information, projection jitter and temporal history, with special handling for transparency/reactivity and cuts. | A capability investigation, not an OpenGL drop-in. Correct motion/depth, reset behavior, layer composition and a compatible API path would be prerequisites. [FSR 3](https://gpuopen.com/fidelityfx-super-resolution-3/), [FSR 2 integration](https://gpuopen.com/manuals/fidelityfx_sdk/techniques/super-resolution-temporal/) |
| FSR 1 EASU/RCAS | Spatial upscaling/sharpening; AMD provides GLSL code examples and recommends placement before UI composition and grain. | A smaller adaptation experiment is conceivable; OpenGL 3.3/GLES portability still must be proved. It is not neural rendering and should compete against native/high-resolution rendering, not automatically replace it. [Official shader source](https://github.com/GPUOpen-Effects/FidelityFX-FSR/blob/master/ffx-fsr/ffx_fsr1.h) |
| Intel XeSS-SR | Current guide lists Windows x64, D3D11/12 and Vulkan routes, including non-Intel GPU paths with feature requirements. It needs color, jitter and high-resolution motion vectors, or low-resolution vectors plus depth. | Neither Vulkan availability nor non-Intel support establishes native Linux/OpenGL Deck integration. API, deployment, feature checks and measurements remain open. [Intel guide](https://github.com/intel/xess/blob/main/doc/xess_sr_developer_guide_english.md) |
| RTX Neural Texture Compression | SDK has Vulkan/D3D12 and fallback inference paths. It warns that sampling inference is costly on older hardware; on-load decompression/transcoding produces conventional BCn residency. | Do not dismiss all neural inference as NVIDIA-only, but do not infer fast Deck sampling. This is not an OpenGL integration or an art upgrade. Ordinary compressed textures deserve comparison first. [NVIDIA SDK](https://github.com/NVIDIA-RTX/RTXNTC) |

An unsupported vendor path does not prove that all real-time neural approaches are physically impossible on the APU. It means that this research has not established a production-ready implementation for this engine and device. We have no Deck inference timings or quality results. Generic image-to-image processing of independent gameplay frames is not evidence of stable interactive rendering.

## Renderer audit and integration implications

The checkout uses Java/LWJGL with **OpenGL 3.3**, falling back to **OpenGLES 3.2**, through [SdlWindow](../../src/main/java/legend/core/platform/SdlWindow.java). The [build dependencies](../../build.gradle) include those APIs. No Vulkan game renderer was found in the inspected renderer/platform implementations.

[RenderEngine](../../src/main/java/legend/core/renderer/RenderEngine.java) has two RGBA8 color render textures and one shared depth texture, then presents a screen quad. These alternating color buffers are not a complete temporal-upscaler contract. Each render batch clears depth and mixes perspective and orthographic pools; that depth resource cannot simply be assumed to represent one coherent full-scene geometry view.

The inspected [battle vertex shader](../../gfx/shaders/battle_tmd.vsh) carries current transforms, vertex-normal-based legacy lighting and palette/texture information; the [fragment shader](../../gfx/shaders/battle_tmd.fsh) writes color plus projection-dependent depth and implements PSX texture/STP/translucency behavior. Searches of `src/main/java/legend/core/renderer` and `gfx/shaders` found no motion-vector attachment/output, projection-jitter sequence, tangent-space normal-map input or roughness/metallic material pipeline. This is a bounded source audit, not a runtime buffer capture.

The [screen shader](../../gfx/shaders/screen.fsh) already has optional CRT/pixelation/noise/bloom treatment. [Presentation order](../../src/main/java/legend/core/renderer/RenderEngine.java) currently sends rendered batches, including orthographic content, through the final screen pass. Keeping menus/text out of an aggressive upscaler or neural filter would require a deliberate composition seam; the existing pass is not proof that UI can already be isolated.

**Inference:** the first visible material pilot can use the existing RGBA sampler and the [audited material seam](NATIVE_MATERIAL_INTEGRATION.md). Dynamic PBR normal/roughness/metallic response would require additional shader/material contracts; merely generating those maps would not make them active. Start with controlled painted/baked detail, retain compatible original normals and test native lighting. If a later surface-lighting experiment earns its cost, implement it locally and preserve Faithful mode.

Pre-rendered painted backgrounds also do not acquire complete relightable 3D geometry simply by adding a depth or normal estimate. Estimated maps could support an art-directed approximation, but character grounding, occlusion and camera transitions must be proved scene by scene.

## One-character vertical slice

**Proposed acceptance experiment:** use one inspected character in a representative HD-background scene. Produce a conspicuously improved face/head and a coherent costume/material pass, then prove it in motion before expanding the cast.

1. Export source parts, pivots, UV/material identities and sampled poses as editable private references. Preserve their provenance and clearly separate reference preview semantics from native game semantics.
2. Define recognizable facial proportions, eyes, mouth, hair silhouette, costume colors and emblems from the original design. Use neural-assisted candidates to propose missing detail; correct inconsistent features across front/profile/three-quarter views. Generic face restoration or unrestricted mesh generation is not identity validation.
3. Reconstruct the head deliberately and remodel selected clothing/limbs without global subdivision shrinkage. Preserve part boundaries/pivots initially, and review the original pose inputs; a new mesh is not automatically animation-compatible.
4. Paint or bake distinct skin, woven cloth, leather and metal detail into reviewed textures. Match the existing painted world rather than applying indiscriminate photorealism. Keep subtle film treatment optional and preserve clean menus.
5. Compare original, previous upscale, and the new reconstruction at normal Deck playing size and in close-ups. Require a visible gain at 1280×800, faithful identity, stable silhouettes, no UV seams or texture swimming, and correctly integrated background occlusion.
6. Integrate an original nearest control first, then the authored candidate, in an explicitly opted-in native test. Verify shader lighting, alpha/STP/blending, transitions and ownership/teardown. Record memory, decode/upload costs and p95/p99 frame times on the actual Deck before selecting budgets or defaults.

The acceptance sequence is **art quality → native correctness → measured Deck cost → player/community review**. The owner's direction is approved; the current candidates and the community's preference are not. Reference exports, offline checks and successful compilation remain separate from those later outcomes.

## Bounded real-time neural research option

If a runtime experiment is pursued, its first deliverable should be an isolated benchmark, not a dependency in the installer. Require an available implementation and acceptable model/code terms, actual target-APU/API capability checks, and a fixed-input test corpus containing motion, disocclusion, translucent attacks, HUD text and camera cuts. Compare native output, a simple spatial baseline and inference output, including transfer/synchronization costs and end-to-end latency. A cloud or desktop render is a different product path and does not validate local Deck operation.

Only continue to gameplay integration if identity, temporal stability, timing-sensitive Additions, frame pacing, power and memory demonstrably improve or stay within agreed limits. No frame rate, battery-life or AA-production-quality promise is made by this plan. The public source remains AGPL v3; retail-derived meshes, textures, extracted assets and private comparison images remain outside Git.
