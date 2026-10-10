#!/usr/bin/env python3
"""Experimental source-bound volumetric Dart locks; AGPL v3. No original controls emitted."""
import copy
import numpy as np

def rebuild(part,source,form,hair):
 p=copy.deepcopy(part);v,polys=source;q=v if form=='field' else np.column_stack((v[:,2]/15,(70-v[:,1])/15,-v[:,0]/15))
 neighbor={};owners={}
 for i,(refs,uv,clut,c) in enumerate(polys):
  if i not in hair:continue
  for a in refs:neighbor.setdefault(a,set()).update(b for b in refs if a!=b);owners.setdefault(a,[]).append(i)
 radii=np.linalg.norm(q-np.array([0,-1.8,0]),axis=1)
 tips=sorted(i for i in neighbor if radii[i]>5.5 and all(radii[i]>=radii[j] for j in neighbor[i]))
 if not tips:return p,{},0
 p['faces']=[face for face in p['faces'] if face['sourceFace'] not in hair]
 used=set();mapping={}
 for tip_id in tips:
  faceid=next((i for i in owners[tip_id] if i not in used),owners[tip_id][0]);used.add(faceid)
  tip=q[tip_id];neighbors=q[list(neighbor[tip_id])];root=neighbors.mean(0)
  center=np.array([0,-1.4,1.2]);radii=np.array([5.5,4.8,5.5]);radial=root-center
  root=center+radial/max(np.linalg.norm(radial/radii),.001)
  direction=tip-root;length=np.linalg.norm(direction);direction/=length
  z=np.array([0,0,1.]);side=np.cross(direction,z)
  if np.linalg.norm(side)<.1:side=np.cross(direction,np.array([0,1.,0]))
  side/=np.linalg.norm(side);depth=np.cross(side,direction)
  width=np.clip(np.max(np.abs((neighbors-root)@side))*.75,.9,2.4)
  mapping[str(faceid)]=[*list(side/(width*2)),float(.5-root@side/(width*2)),*list(direction/length),float(-root@direction/length)]
  count=len(polys[faceid][0]);weights=[1/count]*count
  ring_ids=[]
  for t in np.linspace(0, .96,9):
   center=root+(tip-root)*t+depth*(.42*np.sin(np.pi*t))
   radius=width*(.9+.35*np.sin(np.pi*t))*(1-t)**.85
   ring=[]
   for angle in np.linspace(0,2*np.pi,12,endpoint=False):
    point=center+side*np.cos(angle)*radius+depth*np.sin(angle)*radius*.48
    normal=side*np.cos(angle)+depth*np.sin(angle)/.48;normal/=np.linalg.norm(normal)
    native=point if form=='field' else np.array([-point[2]*15,70-point[1]*15,point[0]*15])
    native_normal=normal if form=='field' else np.array([-normal[2],-normal[1],normal[0]])
    ring.append(len(p['vertices']));p['vertices'].append(native.tolist());p['normals'].append(native_normal.tolist())
   ring_ids.append(ring)
  end=len(p['vertices']);p['vertices'].append((tip if form=='field' else np.array([-tip[2]*15,70-tip[1]*15,tip[0]*15])).tolist());p['normals'].append((direction if form=='field' else np.array([-direction[2],-direction[1],direction[0]])).tolist())
  for ra,rb in zip(ring_ids,ring_ids[1:]):
   for k in range(12):
    a,b,c,d=ra[k],ra[(k+1)%12],rb[k],rb[(k+1)%12]
    for refs in ((a,c,b),(b,c,d)):p['faces'].append({'sourceFace':faceid,'vertices':list(refs),'normals':list(refs),'sourceWeights':[weights]*3})
  for k in range(12):
   refs=[ring_ids[-1][k],end,ring_ids[-1][(k+1)%12]];p['faces'].append({'sourceFace':faceid,'vertices':refs,'normals':refs,'sourceWeights':[weights]*3})
 # Rounded connected crown under the locks, behind the bandana/face surfaces.
 scalp_id=next((i for i in sorted(hair) if i not in used),min(hair))
 mapping[str(scalp_id)]=[.09,0,0,.5,0,.14,0,.85]
 count=len(polys[scalp_id][0]);weight=[1/count]*count
 center=np.array([0,-1.4,1.2]);radii=np.array([5.5,4.8,5.5]);rings=[]
 for theta in np.linspace(.02,np.pi-.02,17):
  ring=[]
  for phi in np.linspace(0,2*np.pi,32,endpoint=False):
   direction=np.array([np.sin(theta)*np.cos(phi),-np.cos(theta),np.sin(theta)*np.sin(phi)])
   point=center+direction*radii;normal=direction/radii;normal/=np.linalg.norm(normal)
   ring.append(len(p['vertices']));p['vertices'].append((point if form=='field' else np.array([-point[2]*15,70-point[1]*15,point[0]*15])).tolist());p['normals'].append((normal if form=='field' else np.array([-normal[2],-normal[1],normal[0]])).tolist())
  rings.append(ring)
 for ra,rb in zip(rings,rings[1:]):
  for k in range(32):
   for refs in ((ra[k],ra[(k+1)%32],rb[k]),(ra[(k+1)%32],rb[(k+1)%32],rb[k])):
    p['faces'].append({'sourceFace':scalp_id,'vertices':list(refs),'normals':list(refs),'sourceWeights':[weight]*3})
 # Match the original renderer's native winding convention for each source surface.
 orientation={}
 for face in part['faces']:
  if face['sourceFace'] not in hair:continue
  a,b,c=np.asarray(part['vertices'])[face['vertices'][:3]]
  value=np.dot(np.cross(b-a,c-a),np.asarray(part['normals'])[face['normals']].mean(0))
  if abs(value)>1e-8:orientation.setdefault(face['sourceFace'],np.sign(value))
 for face in p['faces']:
  if face['sourceFace'] not in hair:continue
  a,b,c=np.asarray(p['vertices'])[face['vertices']]
  value=np.dot(np.cross(b-a,c-a),np.asarray(p['normals'])[face['normals']].mean(0))
  if value*orientation.get(face['sourceFace'],1)<0:
   face['vertices'].reverse();face['normals'].reverse();face['sourceWeights'].reverse()
 # Hair shader/detail identifies the originating polygon; original CPU controls remain untouched.
 return p,mapping,len(tips)
