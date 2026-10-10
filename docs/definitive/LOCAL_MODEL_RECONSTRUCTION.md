# Local head reconstruction on Apple Silicon

Updated 2026-10-09. **The first local Haschel head reconstruction and two reduced, textured meshes now exist privately.** They are static authoring candidates, not accepted character art or an installed game feature. The approved concept supplies the design direction; the unchanged original head supplies the source reference. No cloud inference, paid generator, whole-body rig, native import or engine change was used.

The initial vendor investigation downloaded source text and metadata only. The executed experiment below records the subsequent isolated runtime, verified weights, offline inference, texture baking, failures and review checks. Vendor comparisons and the initial proposed recipe remain dated research; they do not establish other models' current availability or execution on this host.

## Decision

**Use upstream TripoSR's CPU path for the first bounded shape experiment.** It has an explicit upstream CPU fallback, an ungated 1.68 GB checkpoint, and MIT code/model licensing. A direct source API runner can export an untextured GLB without installing its optional texture-baking/UI dependencies. This is the smallest credible local setup among the inspected official routes; it is not a claim that CPU inference is the fastest or that TripoSR will produce a finished face. [Upstream CLI](https://github.com/VAST-AI-Research/TripoSR/blob/107cefdc244c39106fa830359024f6a2f1c78871/run.py), [API](https://github.com/VAST-AI-Research/TripoSR/blob/107cefdc244c39106fa830359024f6a2f1c78871/tsr/system.py), [model card](https://huggingface.co/stabilityai/TripoSR), [code license](https://github.com/VAST-AI-Research/TripoSR/blob/107cefdc244c39106fa830359024f6a2f1c78871/LICENSE).

The first output is a candidate sculpt/reference surface. Single-image reconstruction must invent the unseen back of the head and can misread painted highlights as geometry. Reject a plausible generic face if it loses Haschel's recognizable identity. Preserve the approved silhouette, features and age; compare front/profile/three-quarter views and the original source part before considering further processing.

**Next upgrade if the baseline is useful:** evaluate official Stable Fast 3D's experimental MPS/Metal route when its gated model access is already available. It offers UV/material finishing, but the gate and extra native extensions make it a less immediate first proof. Hunyuan mini shape is the next ungated higher-complexity experiment; establish Mac execution separately from its CUDA-dependent paint route.

## What the official sources establish

| Candidate | Mac/CPU boundary | Download and license implications | Decision |
| --- | --- | --- | --- |
| TripoSR | CLI forces CPU when CUDA is absent, even if `--device mps` was supplied. Its marching-cubes helper has a CPU route. No official MPS validation found. | Checkpoint 1,677,246,742 bytes, ungated; MIT model/code, with retained third-party notices. `torchmcubes` is MPL 2.0. [CLI](https://github.com/VAST-AI-Research/TripoSR/blob/107cefdc244c39106fa830359024f6a2f1c78871/run.py), [extractor](https://github.com/VAST-AI-Research/TripoSR/blob/107cefdc244c39106fa830359024f6a2f1c78871/tsr/models/isosurface.py), [supplier metadata](https://huggingface.co/api/models/stabilityai/TripoSR?blobs=true), [extension license](https://github.com/tatsy/torchmcubes/blob/879926d0ef58e6ce0ac2630fdecb5e53af7ed3ff/LICENSE) | First shape-only CPU proof; do not promise MPS acceleration. |
| Hunyuan3D 2 mini | Official README advertises macOS; shape API accepts a device/dtype. Basic `mc` extraction uses scikit-image on CPU. That supports a plausible shape-only CPU/MPS experiment, not proven M5 compatibility. CUDA rasterizer is required by the inspected texture installation. | Mini shape safetensors: 3,819,958,234 bytes; ungated. Actual combined checkpoint size is larger than “0.6B” alone suggests. Custom Tencent Community License has territory and usage restrictions; source headers still mention non-commercial wording. [README](https://github.com/Tencent-Hunyuan/Hunyuan3D-2/tree/f8db63096c8282cb27354314d896feba5ba6ff8a), [shape API](https://github.com/Tencent-Hunyuan/Hunyuan3D-2/blob/f8db63096c8282cb27354314d896feba5ba6ff8a/hy3dgen/shapegen/pipelines.py), [MC extractor](https://github.com/Tencent-Hunyuan/Hunyuan3D-2/blob/f8db63096c8282cb27354314d896feba5ba6ff8a/hy3dgen/shapegen/models/autoencoders/surface_extractors.py), [CUDA rasterizer](https://github.com/Tencent-Hunyuan/Hunyuan3D-2/blob/f8db63096c8282cb27354314d896feba5ba6ff8a/hy3dgen/texgen/custom_rasterizer/setup.py), [weight/license](https://huggingface.co/tencent/Hunyuan3D-2mini/tree/f90a0f7df7d5e6f71109cf333f6a95a0ae3194a6) | Second shape experiment. Avoid blanket installation of paint/demo dependencies and avoid `HY3DGEN_DEBUG=1`, whose timer uses CUDA events. |
| Hunyuan3D 2.1 | Shape still exposes device selection, but complete official requirements include `cupy-cuda12x`, `deepspeed`, Blender and native rasterization dependencies. No complete official Mac recipe verified. | Shape checkpoint 7,366,389,768 bytes. Listed paint weights add about 6.89 GB; dependencies can add more. Custom 2.1 Community License. [Requirements](https://github.com/Tencent-Hunyuan/Hunyuan3D-2.1/blob/82920d643c0dc2f7bfd7255f45f62d386edfe60c/requirements.txt), [metadata](https://huggingface.co/api/models/tencent/Hunyuan3D-2.1?blobs=true), [license](https://github.com/Tencent-Hunyuan/Hunyuan3D-2.1/blob/82920d643c0dc2f7bfd7255f45f62d386edfe60c/LICENSE) | Not the first setup. A community port is a separate code audit, not official support. |
| Stable Fast 3D | Official experimental MPS/Metal support, tested on M1 Max 64 GB; documented CPU fallback. Metal texture baker and native UV unwrapper must compile. README requires OpenMP for Mac. | 4,024,289,892-byte safetensors, gated access. Community License distinguishes non-commercial and commercial use, including registration/revenue terms; not MIT. [Mac/CPU instructions](https://github.com/Stability-AI/stable-fast-3d/tree/ff21fc491b4dc5314bf6734c7c0dabd86b5f5bb2), [requirements](https://github.com/Stability-AI/stable-fast-3d/blob/ff21fc491b4dc5314bf6734c7c0dabd86b5f5bb2/requirements.txt), [metadata](https://huggingface.co/api/models/stabilityai/stable-fast-3d?blobs=true), [license](https://github.com/Stability-AI/stable-fast-3d/blob/ff21fc491b4dc5314bf6734c7c0dabd86b5f5bb2/LICENSE.md) | Credible official accelerated alternative after access/native-build validation. No M5 speed claim. |
| Official TRELLIS / InstantMesh | TRELLIS documents Linux, NVIDIA GPU and CUDA-built modules, including sparse/rasterization dependencies. InstantMesh recommends CUDA and xformers; its upstream runner is CUDA-oriented. | TRELLIS-image-large's listed checkpoint set is about 3.30 GB, excluding auxiliary models; MIT. InstantMesh-large is 1.515 GB plus 1.732 GB custom diffusion weights and other dependencies; Apache 2.0. [TRELLIS](https://github.com/microsoft/TRELLIS/tree/442aa1e1afb9014e80681d3bf604e8d728a86ee7), [TRELLIS metadata](https://huggingface.co/api/models/microsoft/TRELLIS-image-large?blobs=true), [InstantMesh](https://github.com/TencentARC/InstantMesh/tree/08822c52fdc399b93ea00e4fa9e596344ed52ccc), [InstantMesh metadata](https://huggingface.co/api/models/TencentARC/InstantMesh?blobs=true) | Official routes are unsuitable for a direct Mac install. Unofficial Metal ports may be evaluated later; do not turn this head experiment into a framework port. |

Weight sizes are supplier metadata, not runtime peak memory or measured downloads. No license is folded into the game's AGPL: keep authoring tools/models separate and preserve their notices. These model licenses do not grant rights in the retail game's character design or extracted assets.

## Mac environment and dependency boundary

Initial read-only checks found ARM64 Python 3.12.0 and the Xcode Clang toolchain. `cmake`, `ninja` and `blender` were then absent from PATH; this did not prove no application existed elsewhere. The executed experiment subsequently installed its build dependencies inside a private virtual environment. Current hardware was reverified as Mac Studio `Mac17,14`, Apple M5 Max, 64 GB, macOS 27.0.1. Torch reports MPS built and available; all recorded inference used **CPU**, with no MPS operator or speed claim.

Apple documents PyTorch's MPS backend and its runtime availability check; hardware support alone does not make every model operator or CUDA extension portable. [Apple MPS guidance](https://developer.apple.com/metal/pytorch/).

PyPI metadata confirms macOS ARM64 Python 3.12 wheels for the proposed CPU baseline's `torch==2.5.1`, `Pillow==10.1.0`, `numpy==1.26.4`, and universal2 `onnxruntime==1.20.1`. `xatlas==0.0.9` has no listed Mac ARM64 wheel. Avoid the upstream all-purpose CLI's eager texture-baking imports by using `TSR` directly. These wheel checks establish availability, not successful dependency resolution or runtime compatibility. [Torch metadata](https://pypi.org/pypi/torch/2.5.1/json), [Pillow](https://pypi.org/pypi/Pillow/10.1.0/json), [NumPy](https://pypi.org/pypi/numpy/1.26.4/json), [ONNX Runtime](https://pypi.org/pypi/onnxruntime/1.20.1/json), [xatlas](https://pypi.org/pypi/xatlas/0.0.9/json).

`torchmcubes` explicitly supports CPU-only compilation without CUDA. Its current build requires C++20, CMake and an installed PyTorch, with build isolation disabled so the extension matches Torch's ABI. OpenMP is optional in its inspected CMake/source; a first CPU proof need not alter the system OpenMP installation. [Build instructions](https://github.com/tatsy/torchmcubes/blob/879926d0ef58e6ce0ac2630fdecb5e53af7ed3ff/README.md), [CMake](https://github.com/tatsy/torchmcubes/blob/879926d0ef58e6ce0ac2630fdecb5e53af7ed3ff/CMakeLists.txt), [CPU implementation](https://github.com/tatsy/torchmcubes/blob/879926d0ef58e6ce0ac2630fdecb5e53af7ed3ff/cxx/mcubes_cpu.cpp).

## Initial source-linked proposal

The following is the **historical proposed recipe**, not the executed dependency lock. The actual run used Torch 2.14.1 and the versions recorded below. Resolve/freeze dependencies and perform a synthetic CPU marching-cubes smoke test before loading the checkpoint. Stop on a native build/import failure and record the exact error; do not substitute unreviewed forks or disable certificate verification.

Use a new private tooling directory outside the checkout. The input must be one approved head image, already isolated against a neutral gray background with sensible framing. Do not feed a turnaround/contact sheet or a full-body concept to a single-image head reconstruction.

```sh
mkdir -p /PRIVATE/Definitive-head-tooling
cd /PRIVATE/Definitive-head-tooling
python3 -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install torch==2.5.1 numpy==1.26.4 Pillow==10.1.0 \
  omegaconf==2.3.0 einops==0.7.0 transformers==4.35.0 \
  huggingface-hub==0.17.3 trimesh==4.0.5 rembg==2.0.57 \
  onnxruntime==1.20.1 imageio==2.36.0 \
  scikit-build-core==1.1.1 pybind11==2.13.6 cmake==3.31.6 ninja==1.11.1.3
git clone https://github.com/VAST-AI-Research/TripoSR.git
git -C TripoSR checkout --detach 107cefdc244c39106fa830359024f6a2f1c78871
python -m pip install --no-build-isolation \
  git+https://github.com/tatsy/torchmcubes.git@879926d0ef58e6ce0ac2630fdecb5e53af7ed3ff
python -m pip check
python -m pip freeze > dependency-lock.txt
```

The baseline deliberately uses a known ARM64 wheel rather than claiming it is the newest Torch or best M5 MPS implementation. The minimal package selection is derived from [upstream imports](https://github.com/VAST-AI-Research/TripoSR/blob/107cefdc244c39106fa830359024f6a2f1c78871/tsr/utils.py) and [requirements](https://github.com/VAST-AI-Research/TripoSR/blob/107cefdc244c39106fa830359024f6a2f1c78871/requirements.txt); it omits the UI and texture baker. This selection still needs resolver/import verification.

Download only the fixed-revision `config.yaml` and `model.ckpt` from model revision `5b521936b01fbe1890f6f9baed0254ab6351c04a`, retaining the supplier model card/license. Expected checkpoint SHA256 is `429e2c6b22a0923967459de24d67f05962b235f79cde6b032aa7ed2ffcd970ee`; hash the downloaded file before loading it. That supplier digest was subsequently verified against the complete local file before inference and rechecked for this record. [Checkpoint metadata](https://huggingface.co/stabilityai/TripoSR/blob/5b521936b01fbe1890f6f9baed0254ab6351c04a/model.ckpt).

```python
from huggingface_hub import hf_hub_download
revision = "5b521936b01fbe1890f6f9baed0254ab6351c04a"
for name in ("config.yaml", "model.ckpt"):
    hf_hub_download("stabilityai/TripoSR", name, revision=revision,
                    local_dir="weights/TripoSR", local_dir_use_symlinks=False)
```

The image tokenizer additionally requests the small `facebook/dino-vitb16/config.json` via Hugging Face, not a separate DINO weight download. Cache and hash that configuration as well. Once required files are cached, use offline library settings during generation and verify no input upload occurs. [Tokenizer source](https://github.com/VAST-AI-Research/TripoSR/blob/107cefdc244c39106fa830359024f6a2f1c78871/tsr/models/tokenizers/image.py).

Run a private Python helper from `TripoSR` with the following core calls, using the exact local weight directory, matte image and new output path:

```python
from pathlib import Path
from PIL import Image
import torch
from tsr.system import TSR
torch.set_num_threads(8)
model = TSR.from_pretrained("/PRIVATE/Definitive-head-tooling/weights/TripoSR",
                            config_name="config.yaml", weight_name="model.ckpt")
model.eval().to("cpu")
model.renderer.set_chunk_size(4096)
image = Image.open("/PRIVATE/approved-haschel-head-gray.png").convert("RGB")
with torch.inference_mode():
    codes = model([image], device="cpu")
    mesh = model.extract_mesh(codes, has_vertex_color=False, resolution=128)[0]
output = Path("/PRIVATE/haschel-head-first-shape.glb")
assert not output.exists()
assert len(mesh.vertices) and len(mesh.faces)
mesh.export(output)
```

Start at 128 extraction resolution and one image. Treat 256 as a later refinement only if the coarse shape merits it. Put the process under an external wall-time/memory watchdog and preserve stage logs, input/model/source/dependency hashes, elapsed time, peak memory, vertex/triangle counts and failures. A 20-minute first-attempt ceiling is a project test budget, not a measured inference estimate. Do not launch a web UI or change the global Python environment.

## Executed local experiment

The private ARM64 Python 3.12.0 environment uses Torch 2.14.1, NumPy 1.26.4, Pillow 10.1.0, Transformers 4.35.0, Trimesh 4.0.5, fast-simplification 0.2.0 and xatlas 0.0.11. `pip check` passes. The pinned CPU marching-cubes helper produced a finite synthetic mesh before character inference. Source checkouts remain unchanged at TripoSR `107cefdc244c39106fa830359024f6a2f1c78871` and torchmcubes `879926d0ef58e6ce0ac2630fdecb5e53af7ed3ff`.

The executed dependency lock SHA256 is `1ecc364a00dc00ba2dbade66fcf5e2b0b986e49189583ed42d76716a5770889a`. Build tools, weights and dependencies stay outside the game repository and installer. TripoSR's MIT code/model notices and torchmcubes' MPL 2.0 notice were retained. The two additional ARM64 wheels were installed from locally hash-checked files; their MIT notices were read and retained. [fast-simplification](https://github.com/pyvista/fast-simplification), [xatlas Python bindings](https://github.com/mworchel/xatlas-python).

The complete checkpoint is 1,677,246,742 bytes and matches the supplier SHA256 above. Configuration SHA256 is `74ca708ce086bf68e97709ea6b3d91f14717921c04691e84043f0eb8fcc68e62`; the cached DINO configuration is `b87c0270b97db085fd82cf114a761fd0f62ae7914fbd407c752a2260646b689c`. Approved input SHA256 is `cd7f137682888e792e5fea4d9a9ff47368fe94874c617ecba0a2eafdfa18912e`.

Inference used eight CPU threads, two interop threads, seed 0, 4,096-point renderer chunks, upstream foreground ratio 0.85 and a 0.5 gray alpha matte. Hugging Face offline/telemetry settings were applied; socket connection methods reject network access during inference. The tokenizer's single known configuration request resolves to the hashed local file and rejects unexpected requests. No character image was uploaded.

Each worker ran under an external process watchdog with a 20-minute wall ceiling and a 16 GiB resident-memory ceiling. It can terminate only its own child process group. New output directories are required; logs and failed runs remain available. Commands were the isolated Python invocation of each round's `watch.py`, followed by the retained render, GLB normalization and local validator helpers. These private runners contain owner-specific asset paths and are retained alongside the evidence, rather than packaged as a public installer dependency.

| Private round | Result | External wall time | Observed peak RSS |
| --- | --- | ---: | ---: |
| 28, extraction 128 | 20,774 vertices, 41,392 triangles, vertex-color shape | 10.05 s | 3.822 GiB |
| 29, extraction 256 | 89,398 vertices, 178,568 triangles, vertex-color shape | 18.13 s | 4.931 GiB |
| 31, aggressive cleanup | Failed the fixed displacement guard; no completed candidate | 8.06 s | 3.844 GiB |
| 32, reduction and baking | 4,000 and 8,000 triangles, separate 1024² textures | 10.07 s | 3.874 GiB |

These are Mac authoring measurements, including worker startup, not Deck rendering costs or benchmark claims. The watchdog samples resident memory every two seconds; brief peaks may be missed. Worker-reported timing/memory is retained separately.

Component analysis explicitly disables Trimesh's automatic repair and checks that component triangle counts sum to the original. The fine source has 11 components: the largest contains 177,946 triangles; ten small components total 622 triangles. Their exclusion from the experimental reduction is recorded, not silently described as faithful geometry. Initial repair-enabled split counts were superseded by an unmodified topology audit.

Reduction uses boundary-preserving quadric simplification. Round 31's six-step cleanup exceeded the fixed 0.03 displacement limit and stopped; the limit was not relaxed. Round 32 uses two Taubin steps, lambda 0.10/nu 0.11, with 458 boundary vertices fixed. Maximum displacement is 0.000284 for the smaller mesh and 0.000226 for the larger in the reconstruction's normalized coordinates. This preserves the inferred open edge; it does **not** establish a match to the original neck or part pivot.

## Texture and format review

The meshes have baked neural base colors, explicit smooth normals, opaque surfaces, roughness 1 and metallic factor 0. These are preview material choices, not a skin/hair material solution. UV unwrap and packing remain unfinished:

| Candidate | Vertices including UV splits | UV charts | Covered texture pixels | Faces without a covered pixel center |
| --- | ---: | ---: | ---: | ---: |
| 4,000 triangles | 3,871 | 338 | 282,589 | 117 |
| 8,000 triangles | 7,364 | 517 | 219,254 | 156 |

Four-pixel dilation surrounds baked coverage. It does not guarantee correct filtering at subpixel islands or native seams. Each 1024² RGBA8 texture would occupy 4 MiB decoded, excluding mipmaps and driver overhead; compressed file size is not GPU residency. Neither triangle count is a measured Deck budget or selected default.

Review-ready GLBs add the missing optional vertex/index buffer-view target metadata to the raw exporter output. The entire binary geometry/texture chunk is unchanged. Both pass official Khronos glTF Validator `2.0.0-dev.3.10` locally with **zero errors, warnings, infos or hints**. Structural validity does not prove artistic quality, rig compatibility or gameplay rendering.

| Private review-ready file | Bytes | SHA256 |
| --- | ---: | --- |
| 4,000-triangle head | 673,552 | `9256df3cb2bf6ee6428d59f86814687b1a62abd5a5b3a7086d15f0d0242e1ec9` |
| 8,000-triangle head | 821,016 | `3e737dcfd28f4be7c5f2f433dd1e48fe500fb6085195b863a5d5275d62e35a08` |

The private comparison renders four views of the actual 3D meshes, both textured and geometry-only, beside unchanged original head parts 7/8/9 at source keyframe 0. Original and candidate portraits have independently normalized framing and different preview shading. They are **not equal-scene gameplay captures**. Headless comparison checks pass at 736/520/320 pixels in light and dark appearances, all 16 candidate selections, native Enter/Space activation, 44-pixel touch targets and restored host state. Visible pixels and alpha remain exact in the lossless WebP previews. These checks establish the review interface, not physical Deck controller acceptance.

Private reproduction files retain runners, logs, locks, validator reports, output hashes, preview images and the self-contained comparison. Evidence manifest SHA256: `6016c736ba3030af116a73b88bc03e1a9fad208cb2ba73ce28f2bdc5186397ea`. No images, generated meshes, checkpoints or local runtimes are committed or shipped.

## Remaining art and integration work

The face is conspicuously clearer than the original source portrait, but the candidate is held from acceptance. Hair clumps and frayed ends, facial proportions, the inferred unseen back and the neck transition need deliberate art correction. Retopology must reduce fragmented UV charts and remove faces without texture coverage. Original part-pivot/scale alignment and attached-part correspondence remain open.

Review the corrected head at handheld size and against the original design before expanding to costume/body materials or other characters. Then define and verify the native import/material contract, sampled animation, lighting, STP/blending, occlusion and teardown. Follow with physical Deck memory/frame-time/power and suspend/input checks, then community comparison. The owner's approved concept direction does not establish community acceptance.

## What a successful mesh still does not prove

Shape inference produces static vertices/faces. Texture generation/baking is a separate operation; ordinary gray mesh export proves neither materials nor a faithful face. Native animated import additionally needs deliberate retopology, a controlled neck boundary, coordinate/scale alignment, original part pivot, normals, UV/material semantics, frame poses, occlusion and ownership/teardown validation. Our source GLB is a reference contract, not an existing game import path.

First review the static candidate beside the unchanged source head. A recognizable front view with a malformed profile/back is a failed candidate. Keep every image, generated mesh, model checkpoint and local runtime private and outside Git. Only after artistic acceptance and integration correctness should a candidate enter actual Deck performance testing.
