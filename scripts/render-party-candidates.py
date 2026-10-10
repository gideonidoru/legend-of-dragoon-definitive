#!/usr/bin/env python3
"""Matched private full actor comparisons with native part poses. AGPL v3.
Offline orthographic preview only; no native gameplay or Deck acceptance.
Original actor pixels and unchanged controls must remain outside Git.
"""
from pathlib import Path
import argparse,importlib.util,json,copy,hashlib
import numpy as np
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('party_study',ROOT/'experiments/dart-modelshd-charhd/build-study.py')
study=importlib.util.module_from_spec(spec);spec.loader.exec_module(study)
materials=study.poses.materials
packed_coordinates=study.poses.packed_coordinates
def render(parts, positions, tim, candidate, bounds, size=(640,640), material_map=None, normal_faces=None, detail_parts=None):
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
    detail_pixels = {index: np.asarray(texture) for index, texture in (detail_parts or {}).items()}
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
                    if detail_parts and part_index in detail_parts:
                        texture = detail_parts[part_index]
                        pixels = detail_pixels[part_index]
                        ux = np.clip((uv[..., 0]*texture.width).astype(int),0,texture.width-1)
                        vy = np.clip((uv[..., 1]*texture.height).astype(int),0,texture.height-1)
                        tex_rgb = pixels[vy,ux,:3]
                    elif candidate_pixels is None:
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

def compare(files,character,candidate,output,forms=('field','combat'),angles=(0,35,90,180),last_pose=True,previous=None):
    if output.exists() or output.resolve().is_relative_to(ROOT):raise ValueError('Use new private output')
    audit=json.loads((ROOT/'integrations/charhd/production/party-reconstruction/source-audit.json').read_text())
    actor=next(a for a in audit['characters'] if a['character']==character)
    fit=json.loads((candidate/'fit-receipt.json').read_text())
    output.mkdir(parents=True); reports=[]
    font=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf',20)
    for record in actor['models']:
        form=record['form'];data=(files/record['source']).read_bytes()
        if form not in forms:continue
        if hashlib.sha256(data).hexdigest()!=record['sourceSha256']:raise ValueError('Changed model')
        old,native,identity,ids,baseline=study.baseline(data)
        # Compare current ModelsHD geometry, exact source material routes and native poses.
        before=copy.deepcopy(baseline);after=copy.deepcopy(before)
        row=next(f for f in fit['forms'] if f['form']==form);h=row['sourcePartIndex']
        after[h]=json.loads((candidate/'parts'/(ids[h]+'.json')).read_text())['parts'][0]
        uv=np.asarray(json.loads((candidate/(form+'-head-uv.json')).read_text())['uvs'])
        if previous:
            previous_fit=json.loads((previous/'fit-receipt.json').read_text())
            previous_head=next(f for f in previous_fit['forms'] if f['form']==form)['sourcePartIndex']
            if previous_head!=h:raise ValueError('Previous head uses a different source part')
            before[h]=json.loads((previous/'parts'/(ids[h]+'.json')).read_text())['parts'][0]
        meshbefore=[study.unpack(p,source) for p,source in zip(before,old)]
        meshafter=[study.unpack(p,source) for p,source in zip(after,old)]
        meshafter[h]=(np.asarray(after[h]['vertices']),[(face['vertices'],uv[face['vertices']],0,np.full((3,3),127.5)) for face in after[h]['faces']])
        texture=Image.open(candidate/'head-basecolour.png').convert('RGB')
        previous_texture=None
        if previous:
            previous_uv=np.asarray(json.loads((previous/(form+'-head-uv.json')).read_text())['uvs'])
            meshbefore[h]=(np.asarray(before[h]['vertices']),[(face['vertices'],previous_uv[face['vertices']],0,np.full((3,3),127.5)) for face in before[h]['faces']])
            previous_texture=Image.open(previous/'head-basecolour.png').convert('RGB')
        if form=='combat':timpath=Path('characters')/character/'textures/combat'
        else:
            catalog=json.loads((ROOT/'docs/definitive/model-catalog/model-catalog.json').read_text())
            match=next(a for m in catalog['models'] for a in m['appearances'] if a['source']==record['source'])
            routes={m['texture'] for m in match['mappings'] if 'texture' in m}
            if len(routes)!=1:raise ValueError('Field texture route is ambiguous')
            timpath=Path(next(iter(routes)))
        tim=(files/timpath).read_bytes()
        model=files/record['source'];anim=model.with_name(str(int(model.name)+1) if form=='field' else '0').read_bytes()
        keys=study.poses.read_keyframes(anim,len(old))
        # Single stable frame for front/side/rear, plus the last stored pose.
        for frame,angle in [(0,angle) for angle in angles]+([(len(keys)-1,0)] if last_pose else []):
            key=keys[frame];rot=study.poses.rotation(0,np.deg2rad(angle),0)
            pbefore=study.poses.posed_vertices(meshbefore,key,np.deg2rad(angle));pafter=study.poses.posed_vertices(meshafter,key,np.deg2rad(angle))
            ns=[]
            for packs in [before,after]:
                variant=[]
                for pack,transform in zip(packs,key):
                    bone=study.poses.rotation(*(transform[:3]*(2*np.pi/4096)))
                    normal=np.asarray(pack['normals'])@bone.T@rot.T
                    variant.append([normal[face['normals']] for face in pack['faces']])
                ns.append(variant)
            bounds=study.poses.framing_bounds([pbefore,pafter])
            board=Image.new('RGB',(1200,970),(26,30,34));draw=ImageDraw.Draw(board)
            for i,(meshes,positions) in enumerate([(meshbefore,pbefore),(meshafter,pafter)]):
                details={h:texture} if i else ({h:previous_texture} if previous else None)
                image=render(meshes,positions,tim,None,bounds,size=(600,880),normal_faces=ns[i],detail_parts=details)
                base=Image.new('RGB',image.size,(26,30,34));base.paste(image,mask=image.getchannel('A'))
                label=('Before: previous head fit' if previous else 'Before: ModelsHD / source texture') if i==0 else 'After: fitted head candidate'
                board.paste(base,(i*600,44));draw.text((i*600+24,12),label,font=font,fill='#e5e1da')
            draw.text((24,934),f'{character.title()} / {form} / frame {frame} / {angle}° — offline native part pose; not gameplay',font=font,fill='#acb1b8')
            filename=f'{form}-{frame}-{angle}-comparison.jpg';board.save(output/filename,quality=95)
            reports.append({'path':filename,'sha256':hashlib.sha256((output/filename).read_bytes()).hexdigest()})
    (output/'manifest.json').write_text(json.dumps({'character':character,'scope':'Offline orthographic native-part geometry/UV inspection only; not gameplay or Steam Deck','images':reports},indent=2)+'\n')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--files',type=Path,required=True);p.add_argument('--character',required=True);p.add_argument('--candidate',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--form',choices=['field','combat']);p.add_argument('--angles',type=float,nargs='+',default=[0,35,90,180]);p.add_argument('--no-last-pose',action='store_true');p.add_argument('--previous',type=Path);a=p.parse_args();compare(a.files,a.character,a.candidate,a.output,(a.form,) if a.form else ('field','combat'),a.angles,not a.no_last_pose,a.previous)
