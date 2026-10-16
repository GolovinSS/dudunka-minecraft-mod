#!/usr/bin/env python3
"""Regenerate only Stage 4.3a.1 code-native block models (no image generation)."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/dudunka'
DIRECTIONS = ('up', 'down', 'north', 'south', 'east', 'west')

def box(a, b, texture, rotation=None, glow=False):
    part = {'from': a, 'to': b, 'faces': {d: {'texture': '#'+texture, 'uv': [0, 0, 16, 16]} for d in DIRECTIONS}}
    if rotation:
        part['rotation'] = rotation
    if glow:
        part.update(shade=False, forge_data={'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False})
    return part

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n')

def rounded_layer(parts, radius, bottom, top, texture):
    # Three overlapping strips form a stepped octagon rather than a square roof.
    inner = radius * .55
    parts.extend([box([8-radius,bottom,8-inner],[8+radius,top,8+inner],texture),
                  box([8-inner,bottom,8-radius],[8+inner,top,8+radius],texture),
                  box([8-radius*.8,bottom,8-radius*.8],[8+radius*.8,top,8+radius*.8],texture)])

for color, peak in [('turquoise',24),('violet',28),('golden',21)]:
    textures = {'particle':f'dudunka:block/{color}_landmark_cap','cap':f'dudunka:block/{color}_landmark_cap',
                'stem':'dudunka:block/vale_mushroom_stem','gill':'dudunka:block/vale_mushroom_inside',
                'glow':f'dudunka:block/{color}_landmark_glow'}
    stem = [box([5,0,6],[10,16,11],'stem'),box([4,0,5],[12,3,12],'stem'),
            box([9,8,6],[12,11,10],'gill')]
    crown = [box([5,0,6],[10,9,11],'stem')]
    for radius,lo,hi in [(16,7,10),(14,10,14),(11,14,18),(7,18,peak-1),(3,peak-1,peak)]:
        rounded_layer(crown,radius,lo,hi,'cap')
    # Open hanging gills and small luminous droplets; visible below the bell rim.
    for angle in (-45,0,45):
        crown.append(box([7,3,-4],[9,7,20],'gill',{'origin':[8,8,8],'axis':'y','angle':angle}))
    crown.extend([box([2,4,2],[14,7,14],'glow',glow=True),box([12,1,7],[15,5,10],'glow',glow=True)])
    for segment,parts in enumerate([stem,crown]):
        write(ASSETS/f'models/block/{color}_music_bell_{segment}.json',{'ambientocclusion':False,'textures':textures,'elements':parts})
    write(ASSETS/f'blockstates/{color}_music_bell.json',{'variants':{f'segment={i}':{'model':f'dudunka:block/{color}_music_bell_{i}'} for i in range(2)}})
    write(ROOT/f'src/main/resources/data/dudunka/loot_tables/blocks/{color}_music_bell.json',{'type':'minecraft:block','pools':[]})

for bloom,(color,wool,petal_count,lift) in enumerate([('turquoise','light_blue',8,22.5),('violet','pink',6,45),('golden','yellow',8,0),('violet','purple',6,-22.5)]):
    textures={'particle':f'minecraft:block/{wool}_wool','petal':f'minecraft:block/{wool}_wool',
              'stem':'dudunka:block/vale_mushroom_stem','leaf':'minecraft:block/moss_block',
              'glow':f'dudunka:block/{color}_landmark_glow'}
    parts=[box([7,0,7],[9,10,9],'stem'),box([2,3,6],[7,5,10],'leaf',{'origin':[7,4,8],'axis':'z','angle':22.5}),
           box([9,5,6],[14,7,10],'leaf',{'origin':[9,6,8],'axis':'z','angle':-22.5})]
    # Individual sloped petals, with gaps, raised tips and a central exposed lantern.
    for side in range(4):
        a=[6,9,1]; b=[10,11,6]
        def turn(v):
            x,y,z=v
            for _ in range(side): x,z=16-z,x
            return [x,y,z]
        points=[turn(a),turn(b)]
        lo=[min(v[k] for v in points) for k in range(3)]; hi=[max(v[k] for v in points) for k in range(3)]
        axis='x' if side%2==0 else 'z'
        angle=lift if side in (0,3) else -lift
        parts.append(box(lo,hi,'petal',{'origin':[8,10,8],'axis':axis,'angle':angle}))
        tiplo=[lo[0],lo[1]+2,lo[2]]; tiphi=[hi[0],hi[1]+3,hi[2]]
        parts.append(box(tiplo,tiphi,'petal'))
    for x,z in ([(3,3),(11,11),(3,11),(11,3)] if petal_count==8 else [(3,3),(11,11)]):
        parts.append(box([x,10,z],[x+2,13,z+2],'petal',{'origin':[8,10,8],'axis':'y','angle':45}))
    parts.extend([box([5.5,9,5.5],[10.5,13,10.5],'glow',glow=True),box([7,13,7],[9,15,9],'glow',glow=True)])
    write(ASSETS/f'models/block/dance_flower_{bloom}.json',{'ambientocclusion':False,'textures':textures,'elements':parts})

for lang,names in [('en_us',['Turquoise Bell Mushroom','Violet Bell Mushroom','Golden Bell Mushroom']),('ru_ru',['Бирюзовый гриб-колокольчик','Фиолетовый гриб-колокольчик','Золотистый гриб-колокольчик'])]:
    path=ASSETS/f'lang/{lang}.json'; data=json.loads(path.read_text())
    for color,name in zip(['turquoise','violet','golden'],names): data[f'block.dudunka.{color}_music_bell']=name
    write(path,data)
