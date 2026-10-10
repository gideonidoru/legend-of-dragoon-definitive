#!/usr/bin/env python3
"""Export only the custom fitted field head and its material as an editable GLB. AGPL v3."""
from pathlib import Path
import argparse,hashlib,json
import numpy as np,trimesh
from PIL import Image
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--pack',type=Path,required=True)
p.add_argument('--binding',type=Path,required=True)
p.add_argument('--texture',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
if a.output.exists():p.error('Use a new output file')
binding=json.loads(a.binding.read_text());pack=json.loads(a.pack.read_text())
assert binding['sourceGeometrySha256']==pack['sourceGeometrySha256']
assert hashlib.sha256(a.texture.read_bytes()).hexdigest()==binding['textureSha256']
part=pack['parts'][0];v=np.asarray(part['vertices']);n=np.asarray(part['normals']);uv=np.asarray(binding['uvs'])
assert len(pack['parts'])==1 and v.shape==n.shape and uv.shape==(len(v),2)
assert np.isfinite(v).all() and np.isfinite(n).all() and (uv>=0).all() and (uv<=1).all()
# Native field axes: +Y down, -Z forward. Export +X forward, +Z up.
v=np.column_stack((-v[:,2],v[:,0],-v[:,1]));n=np.column_stack((-n[:,2],n[:,0],-n[:,1]))
faces=np.asarray([f['vertices'][::-1] for f in part['faces']]);assert faces.min()>=0 and faces.max()<len(v)
m=trimesh.Trimesh(vertices=v,faces=faces,vertex_normals=n,process=False)
m.visual=trimesh.visual.TextureVisuals(uv=uv*[1,-1]+[0,1],material=trimesh.visual.material.PBRMaterial(baseColorTexture=Image.open(a.texture),metallicFactor=0,roughnessFactor=1))
m.export(a.output)
print(f'Exported {len(m.faces)} custom triangles and embedded 2K texture to {a.output}')
