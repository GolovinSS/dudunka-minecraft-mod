"""Offline pixel-textured isometric landmark preview; not Minecraft acceptance."""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/dudunka'
OUT = ROOT / 'build/reports/mushroom-vale-stage25'
OUT.mkdir(parents=True, exist_ok=True)


def render(color):
    config = json.loads((ROOT / f'src/main/resources/data/dudunka/worldgen/configured_feature/mushroom_vale/{color}_landmark.json').read_text())['config']
    faces = []
    for piece in config['blocks']:
        name = piece['state']['Name'].split(':')[1]
        profile = piece['state']['Properties']['profile']
        model = json.loads((ASSETS / f'models/block/{name}_{profile}.json').read_text())
        ox, oy, oz = [n * 16 for n in piece['offset']]
        for element in model['elements']:
            x, y, z = element['from']; X, Y, Z = element['to']
            for side, points, shade in [
                ('up',[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],1),
                ('north',[(x,Y,z),(X,Y,z),(X,y,z),(x,y,z)],.8),
                ('west',[(x,Y,Z),(x,Y,z),(x,y,z),(x,y,Z)],.65)]:
                texture = model['textures'][element['faces'][side]['texture'][1:]].split(':')[1]
                tex = Image.open(ASSETS / ('textures/' + texture + '.png')).convert('RGB')
                points = [(a+ox,b+oy,c+oz) for a,b,c in points]
                faces.append((points,tex,shade))
    def project(p): return ((p[0]-p[2])*math.sqrt(3)/2, -(p[0]+p[2])*.5-p[1])
    projected = [project(p) for face,_,_ in faces for p in face]
    minx=min(p[0] for p in projected); miny=min(p[1] for p in projected)
    maxx=max(p[0] for p in projected); maxy=max(p[1] for p in projected)
    scale=min(570/(maxx-minx),610/(maxy-miny))
    im=Image.new('RGB',(600,660),'#202431');draw=ImageDraw.Draw(im)
    def screen(p):
        a,b=project(p); return (15+(a-minx)*scale,15+(b-miny)*scale)
    for points,tex,shade in sorted(faces,key=lambda f:sum(p[0]+p[2]-p[1] for p in f[0])/4,reverse=True):
        q=[screen(p) for p in points]
        def interp(u,v):return (q[0][0]+u*(q[1][0]-q[0][0])+v*(q[3][0]-q[0][0]),q[0][1]+u*(q[1][1]-q[0][1])+v*(q[3][1]-q[0][1]))
        for v in range(16):
            for u in range(16):
                rgb=tuple(int(c*shade) for c in tex.getpixel((u,v)))
                draw.polygon([interp(u/16,v/16),interp((u+1)/16,v/16),interp((u+1)/16,(v+1)/16),interp(u/16,(v+1)/16)],fill=rgb)
    draw.text((15,640),color + ' / offline geometry preview',fill='white')
    return im


sheet=Image.new('RGB',(1800,660),'#202431')
for n,color in enumerate(['turquoise','violet','golden']):
    sheet.paste(render(color),(n*600,0))
sheet.save(OUT / 'landmarks.png')
print(OUT / 'landmarks.png')
