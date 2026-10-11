"""Texture-sampled orthographic preview of verifyWiseFlyAgaric's actual baked geometry.

This is an offline asset review, not a Minecraft client screenshot.
"""
from pathlib import Path
import json, math
import numpy as np
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
models=json.loads((ROOT/'build/wise-fly-agaric-preview.json').read_text())
atlas=np.asarray(Image.open(ROOT/'src/main/resources/assets/dudunka/textures/entity/wise_fly_agaric.png').convert('RGB'))
size=300
sheet=Image.new('RGB',(size*3,size*4),(247,237,219));draw=ImageDraw.Draw(sheet)
for index,model in enumerate(models):
    angle={'front':0,'side':math.pi/2,'back':math.pi,'three-quarter':-.6}.get(model['pose'],-.35)
    elevation=.17 if model['pose']=='front' else .35
    c,s=math.cos(angle),math.sin(angle);ce,se=math.cos(elevation),math.sin(elevation)
    image=np.full((size,size,3),(247,237,219),dtype=np.uint8);depth=np.full((size,size),-np.inf)
    for face in np.asarray(model['vertices']).reshape(-1,4,5):
        xyz=face[:,:3]*16;xyz[:,1]=24-xyz[:,1]
        xx=xyz[:,0]*c+xyz[:,2]*s;zz=-xyz[:,0]*s+xyz[:,2]*c
        yy=xyz[:,1]*ce+zz*se;dd=-zz*ce+xyz[:,1]*se
        xy=np.column_stack((size/2+xx*13,252-yy*13));uv=face[:,3:]
        normal=np.cross(xyz[1]-xyz[0],xyz[2]-xyz[0]);norm=np.linalg.norm(normal)
        brightness=.88+.12*abs(normal[1]/norm) if norm else 1
        for tri in [(0,1,2),(0,2,3)]:
            pts=xy[list(tri)];tuv=uv[list(tri)];td=dd[list(tri)]
            left,top=np.maximum(0,np.floor(pts.min(axis=0)).astype(int));right,bottom=np.minimum(size-1,np.ceil(pts.max(axis=0)).astype(int))
            if left>right or top>bottom:continue
            a,b,cpt=pts;det=(b[1]-cpt[1])*(a[0]-cpt[0])+(cpt[0]-b[0])*(a[1]-cpt[1])
            if abs(det)<1e-7:continue
            ys,xs=np.mgrid[top:bottom+1,left:right+1];px=xs+.5;py=ys+.5
            w0=((b[1]-cpt[1])*(px-cpt[0])+(cpt[0]-b[0])*(py-cpt[1]))/det
            w1=((cpt[1]-a[1])*(px-cpt[0])+(a[0]-cpt[0])*(py-cpt[1]))/det;w2=1-w0-w1
            z=w0*td[0]+w1*td[1]+w2*td[2];mask=(w0>=-1e-6)&(w1>=-1e-6)&(w2>=-1e-6)&(z>depth[ys,xs])
            u=np.clip(((w0*tuv[0,0]+w1*tuv[1,0]+w2*tuv[2,0])*128).astype(int),0,127)
            v=np.clip(((w0*tuv[0,1]+w1*tuv[1,1]+w2*tuv[2,1])*64).astype(int),0,63)
            image[ys[mask],xs[mask]]=(atlas[v[mask],u[mask]]*brightness).astype(np.uint8);depth[ys[mask],xs[mask]]=z[mask]
    x,y=(index%3)*size,(index//3)*size
    sheet.paste(Image.fromarray(image),(x,y));draw.text((x+12,y+14),model['pose'],fill=(74,44,31))
out=ROOT/'docs/WISE-FLY-AGARIC-PREVIEW.png';sheet.save(out);print(out)
