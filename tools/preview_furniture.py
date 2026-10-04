"""Render the actual six block JSON models with their pixel textures, without a game client.
The preview is an orthographic asset inspection, not an in-game screenshot.
Python + Pillow + NumPy. Also validates JSON bounds, textures, orientations and recipes.
"""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,math,numpy as np
root=Path(__file__).resolve().parents[1];a=root/'src/main/resources/assets/dudunka'
names=['dudunka_home','marusya_home','syusya_home','dudunka_furniture','marusya_furniture','syusya_furniture']
labels=['Кровать Дюдюньки','Подушка Маруси','Домик Сюси','Столик Дюдюньки','Лежанка с когтеточкой','Домик с листочком']
canvas=Image.new('RGB',(1500,1080),'#f5f0e7');draw=ImageDraw.Draw(canvas)
fontpath='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf';font=ImageFont.truetype(fontpath,23);title=ImageFont.truetype(fontpath,30);small=ImageFont.truetype(fontpath,17)
draw.text((750,35),'Уютный дом · реальные модели 0.21.0',font=title,fill='#493225',anchor='mm')
textures={}
for i,name in enumerate(names):
 draw=ImageDraw.Draw(canvas)
 model=json.loads((a/f'models/block/{name}.json').read_text());cx=250+(i%3)*500;cy=355+(i//3)*460
 draw.text((cx,95+(i//3)*460),labels[i],font=font,fill='#493225',anchor='mm')
 pixels=np.asarray(canvas).copy();depths=np.full((1080,1500),-np.inf)
 def triangle(verts,uv,image,shade):
  pts=np.array([(cx+(x+z-16)*12,cy+(x-z)*6-y*13.714,x-z+.875*y) for x,y,z in verts])
  xmin=max(0,int(np.floor(pts[:,0].min())));xmax=min(1499,int(np.ceil(pts[:,0].max())))
  ymin=max(0,int(np.floor(pts[:,1].min())));ymax=min(1079,int(np.ceil(pts[:,1].max())))
  if xmax<xmin or ymax<ymin:return
  xx,yy=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
  x0,y0=pts[0,:2];x1,y1=pts[1,:2];x2,y2=pts[2,:2];den=(y1-y2)*(x0-x2)+(x2-x1)*(y0-y2)
  if abs(den)<1e-7:return
  w0=((y1-y2)*(xx-x2)+(x2-x1)*(yy-y2))/den;w1=((y2-y0)*(xx-x2)+(x0-x2)*(yy-y2))/den;w2=1-w0-w1
  dep=w0*pts[0,2]+w1*pts[1,2]+w2*pts[2,2];view=depths[ymin:ymax+1,xmin:xmax+1];mask=(w0>=-1e-6)&(w1>=-1e-6)&(w2>=-1e-6)&(dep>view)
  u=np.clip((w0*uv[0][0]+w1*uv[1][0]+w2*uv[2][0])*16,0,15).astype(int);v=np.clip((w0*uv[0][1]+w1*uv[1][1]+w2*uv[2][1])*16,0,15).astype(int)
  colors=(np.asarray(image)[v,u]*shade).astype('uint8');pixels[ymin:ymax+1,xmin:xmax+1][mask]=colors[mask];view[mask]=dep[mask]
 for e in model['elements']:
  lo,hi=e['from'],e['to'];assert all(0<=x<y<=16 for x,y in zip(lo,hi)),name
  for face in ['up','north','east']:
   f=e['faces'][face];tex=model['textures'][f['texture'][1:]].split(':')[1];path=a/f'textures/{tex}.png';assert path.exists(),path
   if tex not in textures:textures[tex]=Image.open(path).convert('RGB')
   image=textures[tex];assert image.size==(16,16)
   if face=='up':
    xyz=lambda u,v:(lo[0]+(hi[0]-lo[0])*u,hi[1],hi[2]-(hi[2]-lo[2])*v);shade=1.
   elif face=='north':
    xyz=lambda u,v:(lo[0]+(hi[0]-lo[0])*u,hi[1]-(hi[1]-lo[1])*v,lo[2]);shade=.85
   else:
    xyz=lambda u,v:(hi[0],hi[1]-(hi[1]-lo[1])*v,hi[2]-(hi[2]-lo[2])*u);shade=.67
   uv=[(0,0),(1,0),(1,1),(0,1)];verts=[xyz(*point) for point in uv]
   for ids in [(0,1,2),(0,2,3)]:triangle([verts[j] for j in ids],[uv[j] for j in ids],image,shade)
 canvas=Image.fromarray(pixels);draw=ImageDraw.Draw(canvas)
 # Texture tiles are the exact PNG assets.
 mats=['wood','pink','cream'] if i==0 else ['plum','paw','cream'] if i==1 else ['wood','moss'] if i==2 else ['wood','drawing','pencil'] if i==3 else ['plum','sisal','wood'] if i==4 else ['wood','leaf','water']
 for j,mat in enumerate(mats):canvas.paste(Image.open(a/f'textures/block/cozy_{mat}.png').resize((64,64),Image.Resampling.NEAREST),(cx-len(mats)*40+j*80,cy+100))
 state=json.loads((a/f'blockstates/{name}.json').read_text());assert len(state['variants'])==4
 assert all(v['model']=='dudunka:block/'+name for v in state['variants'].values())
 if name.endswith('furniture'):
  recipe=json.loads((root/f'src/main/resources/data/dudunka/recipes/{name}.json').read_text());assert recipe['result']['item']=='dudunka:'+name
out=root/'docs/FURNITURE-PREVIEW.png';canvas.save(out)
print('PASS six furniture models, texture references, bounds, orientations and recipes; saved',out)
