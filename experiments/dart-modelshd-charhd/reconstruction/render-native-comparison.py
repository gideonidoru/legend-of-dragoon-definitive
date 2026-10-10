#!/usr/bin/env python3
"""Matched native head mesh/UV studio comparisons. AGPL v3; not game screenshots."""
from pathlib import Path
import argparse,importlib.util,json,copy
import numpy as np
from PIL import Image,ImageDraw,ImageFont
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--files',type=Path,required=True)
parser.add_argument('--study',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
a=parser.parse_args();root=Path(__file__).resolve().parents[3]
if a.output.resolve().is_relative_to(root) or a.output.exists():parser.error('Use new private review output')
a.output.mkdir(parents=True)
spec=importlib.util.spec_from_file_location('study',root/'experiments/dart-modelshd-charhd/build-study.py')
study=importlib.util.module_from_spec(spec);spec.loader.exec_module(study)
materials=study.poses.materials
packed_coordinates=study.poses.packed_coordinates
def render(parts, positions, tim, candidate, bounds, size=(640,640), material_map=None, normal_faces=None):
    width, height, cw, ch, colors, indices = materials.texture(tim)
    palette = np.array(colors, dtype=np.uint16)
    packed = np.frombuffer(indices[:width * height // 2], dtype=np.uint8)
    index_map = np.column_stack((packed & 15, packed >> 4)).reshape(height, width).astype(np.uint16)
    candidate_pixels = np.array(candidate) if candidate else None
    canvas = np.zeros((size[1], size[0], 3), dtype=np.uint8)
    canvas[:] = (31, 39, 36)
    zbuffer = np.full((size[1], size[0]), np.inf)
    min_x, max_x, min_y, max_y = bounds
    fit = min((size[0]-48) / max(1, max_x-min_x), (size[1]-48) / max(1, max_y-min_y))
    center = np.array(((size[0]-(min_x+max_x)*fit)/2, (size[1]-(min_y+max_y)*fit)/2))
    for part_index, ((_, polygons), vertices) in enumerate(zip(parts, positions)):
        projected = vertices[:, :2] * fit + center
        for face_index,(references, uvs, clut, colors_rgb) in enumerate(polygons):
            triangles = [(0, 1, 2), (1, 3, 2)] if len(references) == 4 else [(0, 1, 2)]
            for triangle in triangles:
                ref = np.array(references)[list(triangle)]
                coords = projected[ref]
                low = np.maximum(np.floor(coords.min(axis=0)).astype(int), 0)
                high = np.minimum(np.ceil(coords.max(axis=0)).astype(int), np.array(size)-1)
                if np.any(high < low): continue
                xx, yy = np.meshgrid(np.arange(low[0], high[0]+1)+0.5, np.arange(low[1], high[1]+1)+0.5)
                a, b, c = coords
                divisor = (b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(divisor) < 1e-8: continue
                wa = ((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/divisor
                wb = ((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/divisor
                weights = np.stack((wa, wb, 1-wa-wb), axis=-1)
                depth = weights @ vertices[ref, 2]
                old_depth = zbuffer[low[1]:high[1]+1, low[0]:high[0]+1]
                valid = (weights.min(axis=-1) >= -1e-8) & (depth < old_depth)
                rgb = weights @ colors_rgb[list(triangle)]
                if uvs is not None:
                    uv = weights @ uvs[list(triangle)]
                    clut_index = ((clut >> 6) & 15)*(cw//16)+(clut & 3)
                    if candidate_pixels is None:
                        # Original colors follow each polygon's own CLUT, not a flattened atlas.
                        ux = np.clip(uv[..., 0].astype(int), 0, width-1)
                        vy = np.clip(uv[..., 1].astype(int), 0, height-1)
                        value = palette[clut_index*16+index_map[vy, ux]]
                        tex_rgb = np.stack((value & 31, (value >> 5) & 31, (value >> 10) & 31), axis=-1).astype(float)*255/31
                        valid &= value != 0
                    else:
                        if material_map is not None:
                            ux, vy = packed_coordinates(uv, material_map[clut_index])
                        else:
                            ux = np.clip((uv[..., 0]*candidate.width/width).astype(int), 0, candidate.width-1)
                            vy = np.clip((uv[..., 1]*candidate.height/height).astype(int), 0, candidate.height-1)
                        tex = candidate_pixels[vy, ux]
                        tex_rgb = tex[..., :3]
                        valid &= np.any(tex != 0, axis=-1)
                    rgb = rgb * tex_rgb * (2/255)
                if normal_faces is not None:
                    light=np.array([-.35,-.65,-1.]);light/=np.linalg.norm(light)
                    ns=weights@normal_faces[part_index][face_index][list(triangle)]
                    ns/=np.maximum(np.linalg.norm(ns,axis=-1)[...,None],1e-8)
                    rgb*= (.52+.48*np.maximum(0,ns@light))[...,None]
                canvas[low[1]:high[1]+1, low[0]:high[0]+1][valid] = np.clip(rgb[valid], 0, 255).astype(np.uint8)
                old_depth[valid] = depth[valid]
    coverage = np.where(np.isfinite(zbuffer), 255, 0).astype(np.uint8)
    return Image.fromarray(np.dstack((canvas, coverage)))

font=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf',23)
for form,model,timpath in [('field','SECT/DRGN21.BIN/101/0','SECT/DRGN21.BIN/101/textures/0'),('combat','characters/dart/models/combat/32','characters/dart/textures/combat')]:
 data=(a.files/model).read_bytes();tim=(a.files/timpath).read_bytes();source,_,_,ids,current=study.baseline(data)
 before=json.loads((root/'experiments/dart-modelshd-charhd/models/parts'/(ids[7]+'.json')).read_text())['parts'][0]
 after=json.loads((a.study/'parts'/(ids[7]+'.json')).read_text())['parts'][0]
 atlas,mapping,_=study.packing.validate_pack(root/'experiments/dart-modelshd-charhd/charhd/restoration'/form,data,tim)
 atlas,mapping,clut=study.paint_atlas(atlas,mapping,tim)
 study.HAIR_FACES[form]=set()
 old=study.paint_face(before,source[7],form,clut)
 binding=json.loads((a.study/(form+'-head-uv.json')).read_text());uv=np.asarray(binding['uvs'])
 newpoly=[(f['vertices'],uv[f['vertices']],0,np.full((3,3),127.5)) for f in after['faces']]
 new=(np.asarray(after['vertices']),newpoly)
 texture=Image.open(root/'experiments/dart-modelshd-charhd/resources/charhd-experiment/reconstruction/dart-head-aa-v1.png')
 # Work in the same common head coordinates and use the same inspection camera/light.
 variants=[];normal_variants=[]
 for mesh,pack in [(old,before),(new,after)]:
  points=mesh[0];normal=np.asarray(pack['normals'])
  if form=='combat':points=np.column_stack((points[:,2]/15,(70-points[:,1])/15,-points[:,0]/15));normal=np.column_stack((normal[:,2],-normal[:,1],-normal[:,0]))
  variants.append((points,mesh[1]));normal_variants.append([normal[f['normals']] for f in pack['faces']])
 for angle in [0,35,90,180]:
  rot=study.poses.rotation(0,np.deg2rad(angle),0)
  positions=[v[0]@rot.T for v in variants];bounds=study.poses.framing_bounds([[p] for p in positions])
  board=Image.new('RGB',(1280,718),(26,30,34));draw=ImageDraw.Draw(board)
  for i in range(2):
   materials_map=mapping if i==0 else {0:{'normalizedDetail':True,'atlasRect':[0,0,2048,2048]}}
   ns=[[n@rot.T for n in normal_variants[i]]]
   image=render([variants[i]],[positions[i]],tim,atlas if i==0 else texture,bounds,material_map=materials_map,normal_faces=ns)
   base=Image.new('RGB',image.size,(26,30,34));base.paste(image,mask=image.getchannel('A'));base.save(a.output/f'{form}-{angle}-{i}.png')
   board.paste(base,(i*640,42));draw.text((i*640+25,10),'Before · preferred face pass' if i==0 else 'After · reconstructed candidate',font=font,fill=(228,225,218))
  draw.text((25,686),f'{form.title()} · {angle}° · matched native geometry/UV studio view · not gameplay',font=font,fill=(159,164,169))
  board.save(a.output/f'{form}-{angle}-comparison.jpg',quality=95)
print('Rendered matched before/after native mesh comparisons')
