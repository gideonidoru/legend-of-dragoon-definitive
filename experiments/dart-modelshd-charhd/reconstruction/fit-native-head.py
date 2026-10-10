#!/usr/bin/env python3
"""Fit only custom reconstructed heads to Dart's native rigid head pivot. AGPL v3.
Original full-actor controls are emitted only to a private external study folder.
"""
from pathlib import Path
import argparse,copy,hashlib,importlib.util,json,struct
import numpy as np,trimesh
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--mesh',type=Path,required=True)
parser.add_argument('--files',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--scale',type=float,default=17)
parser.add_argument('--yaw',type=float,default=20)
parser.add_argument('--y',type=float,default=1.3)
parser.add_argument('--z',type=float,default=-1.3)
args=parser.parse_args()
root=Path(__file__).resolve().parents[3]
assert not args.output.resolve().is_relative_to(root) and not args.output.exists()
args.output.mkdir(parents=True);(args.output/'parts').mkdir()
spec=importlib.util.spec_from_file_location('study',root/'experiments/dart-modelshd-charhd/build-study.py')
study=importlib.util.module_from_spec(spec);spec.loader.exec_module(study)
mesh=next(iter(trimesh.load(args.mesh,force='scene',process=False).geometry.values()))
texture_path=args.mesh.with_name(args.mesh.stem.replace('-textured','-basecolour')+'.png')
texture_hash=hashlib.sha256(texture_path.read_bytes()).hexdigest()
angle=np.deg2rad(args.yaw);rotate=np.array([[np.cos(angle),-np.sin(angle),0],[np.sin(angle),np.cos(angle),0],[0,0,1]])
basis=np.array([[0,1,0],[0,0,-1],[-1,0,0]])@rotate
q=np.asarray(mesh.vertices)@basis.T*args.scale+np.array([0,args.y,args.z])
normals=np.asarray(mesh.vertex_normals)@basis.T
uv=np.asarray(mesh.visual.uv)*[1,-1]+[0,1]
# Clip the extra generated neck below the original head/torso attachment region.
vertices=[];normal_out=[];uv_out=[];faces=[];lookup={}
def vertex(p,n,u):
 n=n/max(np.linalg.norm(n),1e-12)
 key=tuple(np.round(np.concatenate((p,n,u)),7))
 if key not in lookup:
  lookup[key]=len(vertices);vertices.append(p);normal_out.append(n);uv_out.append(u)
 return lookup[key]
for face in mesh.faces:
 corners=[(q[i],normals[i],uv[i]) for i in face];clipped=[]
 for a,b in zip(corners,corners[1:]+corners[:1]):
  inside_a=a[0][1]<=7.5;inside_b=b[0][1]<=7.5
  if inside_a:clipped.append(a)
  if inside_a!=inside_b:
   t=(7.5-a[0][1])/(b[0][1]-a[0][1]);clipped.append(tuple(x+(y-x)*t for x,y in zip(a,b)))
 if len(clipped)<3:continue
 ids=[vertex(*c) for c in clipped]
 for i in range(1,len(ids)-1):
  refs=[ids[0],ids[i],ids[i+1]]
  a,b,c=np.asarray(vertices)[refs]
  if np.linalg.norm(np.cross(b-a,c-a))>1e-9:faces.append(refs)
vertices=np.asarray(vertices);normal_out=np.asarray(normal_out);uv_out=np.asarray(uv_out)
assert np.isfinite(vertices).all() and np.isfinite(normal_out).all() and (uv_out>=0).all() and (uv_out<=1).all()
records=[]
for form,model in [('field','SECT/DRGN21.BIN/101/0'),('combat','characters/dart/models/combat/32')]:
 data=(args.files/model).read_bytes();original,_,identity,part_ids,current=study.baseline(data)
 for p in (root/'experiments/dart-modelshd-charhd/models/parts').glob('*.json'):
  obj=json.loads(p.read_text())
  if obj['sourceGeometrySha256'] in part_ids:current[part_ids.index(obj['sourceGeometrySha256'])]=copy.deepcopy(obj['parts'][0])
 sourceface=19 if form=='field' else 29
 old=current[7]
 signs=[]
 for f in old['faces']:
  a,b,c=np.asarray(old['vertices'])[f['vertices'][:3]]
  signs.append(np.dot(np.cross(b-a,c-a),np.asarray(old['normals'])[f['normals']].mean(0)))
 sign=np.sign(np.median(signs))
 points=vertices if form=='field' else np.column_stack((-vertices[:,2]*15,70-vertices[:,1]*15,vertices[:,0]*15))
 ns=normal_out if form=='field' else np.column_stack((-normal_out[:,2],-normal_out[:,1],normal_out[:,0]))
 points=points.astype(np.float32);ns=ns.astype(np.float32)
 count=len(original[7][1][sourceface][0]);weights=[1/count]*count
 generated=[]
 for refs in faces:
  refs=refs.copy();a,b,c=points[refs]
  if np.dot(np.cross(b-a,c-a),ns[refs].mean(0))*sign<0:refs.reverse()
  generated.append({'sourceFace':sourceface,'vertices':refs,'normals':refs,'sourceWeights':[weights]*3})
 part={'vertices':points.tolist(),'normals':ns.tolist(),'faces':generated}
 payload={'version':1,'sourceGeometrySha256':part_ids[7],'parts':[part]}
 packed=args.output/'parts'/(part_ids[7]+'.json');packed.write_text(json.dumps(payload,separators=(',',':'),allow_nan=False))
 # Canonical native geometry identity, exactly the engine's header/index/vector format.
 offset=struct.unpack_from('<I',data)[0]+12;cursor=offset+struct.unpack_from('<7I',data,offset+7*28)[4]
 for i in range(sourceface):cursor+=4+((struct.unpack_from('<I',data,cursor)[0]>>8)&255)*4
 source_header=struct.unpack_from('<I',data,cursor)[0];mode=source_header>>24;new_mode=(mode&~8)|16
 new_header=new_mode<<24|(source_header&0x40000)
 digest=hashlib.sha256();put=lambda n:digest.update(struct.pack('>I',n&0xffffffff))
 put(1);put(0)
 for rows in [points,ns]:put(len(rows));digest.update(rows.astype('>f4').tobytes())
 put(len(generated))
 for face in generated:
  put(new_header&0xff040000)
  for index in face['vertices']:put(index);put(index)
 metadata={'version':1,'sourceGeometrySha256':part_ids[7],'geometrySha256':digest.hexdigest(),'uvs':uv_out.tolist(),'texture':'dart-head-aa-v1.png','textureSha256':texture_hash}
 (args.output/(form+'-head-uv.json')).write_text(json.dumps(metadata,separators=(',',':'),allow_nan=False))
 current[7]=part
 (args.output/(form+'-native-candidate.json')).write_text(json.dumps({'version':1,'sourceGeometrySha256':identity,'parts':current},separators=(',',':')))
 records.append({'form':form,'headTriangles':len(generated),'headVertices':len(points),'sourcePartGeometrySha256':part_ids[7],'candidateGeometrySha256':metadata['geometrySha256'],'headPackSha256':hashlib.sha256(packed.read_bytes()).hexdigest(),'candidateTriangles':sum(len(study.triangulate(p)['faces']) for p in current)})
# Copy previous non-head custom packs; head packs above take precedence.
for p in (root/'experiments/dart-modelshd-charhd/models/parts').glob('*.json'):
 target=args.output/'parts'/p.name
 if not target.exists():target.write_bytes(p.read_bytes())
report={'status':'experimental-not-approved','scope':'Dart head reconstruction on previous custom body; not a complete bespoke AA body','meshSha256':hashlib.sha256(args.mesh.read_bytes()).hexdigest(),'textureSha256':texture_hash,'fit':{'scale':args.scale,'yawDegrees':args.yaw,'translation':[0,args.y,args.z],'neckCutY':7.5},'forms':records}
(args.output/'receipt.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
