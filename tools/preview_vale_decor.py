"""Offline isometric model review sheets (Pillow). Not Minecraft visual acceptance.
Custom model geometry/textures are read directly; vanilla blocks are approximate.
Outputs ignored build/reports/mushroom-vale-stage24a/*.png.
"""
from pathlib import Path
import json, math
from PIL import Image, ImageDraw
R=Path(__file__).resolve().parents[1]
A=R/'src/main/resources/assets/dudunka'
OUT=R/'build/reports/mushroom-vale-stage24a'
OUT.mkdir(parents=True,exist_ok=True)
MODELS={p.stem:json.loads(p.read_text()) for p in (A/'models/block').glob('vale_*.json') if p.stem.startswith(('vale_cottage','vale_carved','vale_book','vale_lantern','vale_bench','vale_sign','vale_mushroom','vale_planter'))}
MODELS={k:v for k,v in MODELS.items() if 'elements' in v}
TEX={p.stem:Image.open(p).convert('RGB') for p in (A/'textures/block').glob('vale_decor_*.png')}
def geometry(model,offset=(0,0,0),turn=0):
    result=[]
    for e in model['elements']:
        x,y,z=e['from']; X,Y,Z=e['to']
        faces=[('up',[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],1),
               ('north',[(x,Y,z),(X,Y,z),(X,y,z),(x,y,z)],.8),
               ('west',[(x,Y,Z),(x,Y,z),(x,y,z),(x,y,Z)],.66)]
        # Rotate the complete box; identify visible faces again from the rotated AABB.
        if turn:
            lo=[x,y,z]; hi=[X,Y,Z]
            for _ in range(turn): lo,hi=[16-hi[2],lo[1],lo[0]],[16-lo[2],hi[1],hi[0]]
            copy={'elements':[{'from':lo,'to':hi,'faces':e['faces']}],'textures':model['textures']}
            result+=geometry(copy,offset);continue
        for face,pts,shade in faces:
            tex=model.get('textures',{}).get(e['faces'].get(face,{}).get('texture','')[1:],'')
            result.append(([(p[0]+offset[0],p[1]+offset[1],p[2]+offset[2]) for p in pts],TEX.get(tex.split('/')[-1]),shade))
    return result
def render(faces,size):
    def project(p):return ((p[0]-p[2])*.866,-(p[0]+p[2])*.5-p[1])
    projected=[project(p) for face,_,_ in faces for p in face]
    minx=min(p[0] for p in projected);maxx=max(p[0] for p in projected)
    miny=min(p[1] for p in projected);maxy=max(p[1] for p in projected)
    scale=min((size[0]-30)/(maxx-minx),(size[1]-35)/(maxy-miny))
    ox=(size[0]-(maxx-minx)*scale)/2;oy=15
    im=Image.new('RGB',size,'#202431');draw=ImageDraw.Draw(im)
    def screen(p):
        x,y=project(p);return (ox+(x-minx)*scale,oy+(y-miny)*scale)
    for pts,tex,shade in sorted(faces,key=lambda f:sum(p[0]+p[2]-p[1] for p in f[0])/4,reverse=True):
        q=[screen(p) for p in pts]
        if tex is None:
            draw.polygon(q,fill=tuple(int(c*shade) for c in (193,163,116)));continue
        def interp(u,v):return (q[0][0]+u*(q[1][0]-q[0][0])+v*(q[3][0]-q[0][0]),q[0][1]+u*(q[1][1]-q[0][1])+v*(q[3][1]-q[0][1]))
        for v in range(16):
            for u in range(16):
                c=tuple(int(c*shade) for c in tex.getpixel((u,v)))
                draw.polygon([interp(u/16,v/16),interp((u+1)/16,v/16),interp((u+1)/16,(v+1)/16),interp(u/16,(v+1)/16)],fill=c)
    return im
sheet=Image.new('RGB',(1200,900),'#202431');d=ImageDraw.Draw(sheet)
for i,(name,model) in enumerate(MODELS.items()):
    x=(i%4)*300;y=(i//4)*300
    sheet.paste(render(geometry(model),(300,275)),(x,y));d.text((x+8,y+279),name,fill='white')
sheet.save(OUT/'decor-models.png')
for name in ('pufik_cottage','wise_fly_agaric_cottage'):
    data=json.loads((R/'src/main/resources/data/dudunka/worldgen/configured_feature/mushroom_vale'/(name+'.json')).read_text())
    faces=[]
    for piece in data['config']['blocks']:
        state=piece['state'];key=state['Name'].split(':')[-1];o=[v*16 for v in piece['offset']]
        turn={'north':0,'east':1,'south':2,'west':3}.get(state.get('Properties',{}).get('facing'),0)
        if key in MODELS:faces+=geometry(MODELS[key],o,turn)
        else:
            props=state.get('Properties',{});lo=[0,0,0];hi=[16,16,16]
            if key.endswith('slab'): hi[1]=8
            if key.endswith('fence'):lo=[6,0,6];hi=[10,16,10]
            if key.endswith('door'):hi[2]=3
            mock={'textures':{'t':'dudunka:block/vale_decor_'+('cream' if key in ('stripped_birch_wood','birch_planks') else 'wood')},'elements':[{'from':lo,'to':hi,'faces':{f:{'texture':'#t'} for f in ('up','north','west')}}]}
            faces+=geometry(mock,o,turn)
    render(faces,(1000,900)).save(OUT/(name+'.png'))
print('Wrote offline geometry/texture review sheets; vanilla blocks approximated.')
