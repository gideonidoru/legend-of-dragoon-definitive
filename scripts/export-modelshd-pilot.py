#!/usr/bin/env python3
"""ModelsHD private battle-pack export, AGPL v3; see LICENSE.
Native source part frames and material-face references stay intact. No images are emitted.
One conservative subdivision is a pipeline pilot, not accepted modern character art.
"""
import argparse,hashlib,importlib.util,json,struct,sys
from pathlib import Path
import numpy as np

def load(name,file):
 spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
surface=load('modelshd_surface','refine-model-surfaces.py')

def native_parts(data):
 parts=surface.poses.read_geometry(data);offset=struct.unpack_from('<I',data)[0]+12;native=[];digest=hashlib.sha256();put=lambda n:digest.update(struct.pack('>I',n&0xffffffff));put(len(parts))
 for index,(vertices,polygons) in enumerate(parts):
  vo,nv,no,nn,po,nf,scale=struct.unpack_from('<7I',data,offset+index*28);normals=np.array([struct.unpack_from('<3h',data,offset+no+j*8)for j in range(nn)],dtype=np.float32)/4096.;put(scale)
  for vectors in (vertices,normals):
   put(len(vectors));digest.update(np.asarray(vectors,dtype='>f4').tobytes())
  put(nf);pos=offset+po;normalRefs=[]
  for _ in range(nf):
   header=struct.unpack_from('<I',data,pos)[0];mode=header>>24;n=4 if mode&8 else 3;lit=not(mode&1);shaded=bool(header&0x40000);put(header&0xff040000);packet=data[pos+4:pos+4+((header>>8)&255)*4];cursor=n*4 if mode&4 else 0
   if shaded or not lit:cursor+=n*4
   elif not(mode&4):cursor+=4
   refs=[];normal=0
   for c in range(n):
    if lit and (mode&16 or c==0):normal=struct.unpack_from('<H',packet,cursor)[0];cursor+=2;put(normal)
    vertex=struct.unpack_from('<H',packet,cursor)[0];cursor+=2;put(vertex);refs.append(normal)
   normalRefs.append(refs);pos+=4+len(packet)
  native.append((normals,normalRefs))
 return parts,native,digest.hexdigest()

def export(data,control=False):
 parts,native,identity=native_parts(data)
 return export_geometry(parts,native,identity,control)

def export_geometry(parts,native,identity,control=False,strength=.7,crease_degrees=110):
 out=[];reports=[]
 for index,(original,polygons) in enumerate(parts):
  sourceNormals,normalRefs=native[index]
  if control:
   vertices=original.copy();normals=sourceNormals.copy();faces=[]
   for i,(refs,uv,clut,colour) in enumerate(polygons):faces.append(dict(sourceFace=i,vertices=refs,normals=normalRefs[i],sourceWeights=np.eye(len(refs)).tolist()))
   report=dict(changed=False,reason='native source control')
  else:
   (vertices,refined),report=surface.refine_part(parts[index],strength=strength,crease_degrees=crease_degrees)
   faces=[]
   if not report['changed']:
    normals=sourceNormals.copy()
    for i,(refs,uv,clut,colour) in enumerate(polygons):faces.append(dict(sourceFace=i,vertices=refs,normals=normalRefs[i],sourceWeights=np.eye(len(refs)).tolist()))
   else:
    lineage=[]
    for i,(refs,uv,clut,colour) in enumerate(polygons):
     for corners in (((0,1,2),(1,3,2))if len(refs)==4 else ((0,1,2),)):
      base=np.eye(len(refs))[list(corners)];six=np.concatenate((base,(base[[0,1,2]]+base[[1,2,0]])*.5))
      for child in ((0,3,5),(3,1,4),(5,4,2),(3,4,5)):lineage.append((i,six[list(child)]))
    assert len(lineage)==len(refined)
    normals=np.zeros_like(vertices)
    for (refs,uv,clut,colour),(source,weights) in zip(refined,lineage):
     points=vertices[refs];cross=np.cross(points[1]-points[0],points[2]-points[0]);originalRefs=polygons[source][0];old=original[originalRefs];oldcross=np.cross(old[1]-old[0],old[2]-old[0]);expected=sourceNormals[normalRefs[source]].mean(0) if len(sourceNormals) else oldcross
     if np.dot(oldcross,expected)<0:cross=-cross
     for ref in refs:normals[ref]+=cross
     faces.append(dict(sourceFace=source,vertices=refs,normals=refs,sourceWeights=weights.tolist()))
    lengths=np.linalg.norm(normals,axis=1);normals[lengths>1e-10]/=lengths[lengths>1e-10,None];normals[lengths<=1e-10]=[0,0,1]
  assert np.isfinite(vertices).all()and np.isfinite(normals).all()
  out.append(dict(vertices=vertices.tolist(),normals=normals.tolist(),faces=faces));reports.append(dict(part=index,vertices=len(vertices),polygons=len(faces),**report))
 return dict(version=1,sourceGeometrySha256=identity,parts=out),reports

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();source=a.source.resolve();output=a.output.resolve();repo=Path(__file__).resolve().parents[1]
 if output==repo or repo in output.parents:raise SystemExit('Private model packs must stay outside the source repository')
 output.mkdir(parents=True,exist_ok=False);data=source.read_bytes();records=[]
 for name,control in [('control',True),('pilot',False)]:
  pack,parts=export(data,control);folder=output/name;folder.mkdir();path=folder/(pack['sourceGeometrySha256']+'.json');payload=json.dumps(pack,separators=(',',':'),allow_nan=False).encode();path.write_bytes(payload);records.append(dict(variant=name,file=str(path),sha256=hashlib.sha256(payload).hexdigest(),bytes=len(payload),parts=parts))
 report=dict(scope='Private source-bound whole-actor native import pilot. Retains original part frames and source material references. No accepted sculpt, new images, gameplay, physical Deck, field-model or blanket mod compatibility proof.',source=str(source),sourceSha256=hashlib.sha256(data).hexdigest(),toolSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),outputs=records)
 (output/'export.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:report[k]for k in ('scope','sourceSha256')}));print('Exported source control and subdivision pilot for',len(pack['parts']),'animation parts')
if __name__=='__main__':main()
