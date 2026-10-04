"""Exact pixel textures and cuboid block models for the approved Cozy Home concept.
Run with Python + Pillow. No model/texture runtime dependencies.
"""
from pathlib import Path
from PIL import Image,ImageDraw
import json,random
root=Path(__file__).resolve().parents[1]
a=root/'src/main/resources/assets/dudunka';d=root/'src/main/resources/data/dudunka'
(a/'textures/block').mkdir(parents=True,exist_ok=True)
palettes={'darkwood':('#4f3522','#65462c','#715035','#563a24'),'wood':('#ad783e','#bd894c','#cc9959','#986330'), 'pink':('#de82a7','#ea9abd','#efacc8','#d77ca2'), 'plum':('#653767','#79437f','#8c5393','#714175'), 'cream':('#dbceb2','#efe4cd','#e5d9bf','#f5ecdb'), 'moss':('#566b27','#69802e','#7a9439','#40591d'), 'sisal':('#c7a477','#dbc29a','#ae895d','#e9d5b2'), 'leaf':('#70a331','#86b641','#568829','#9aca4e'), 'water':('#3f999f','#54b9be','#6ccbd0','#328086')}
for name,colors in palettes.items():
 rng=random.Random(name);im=Image.new('RGB',(16,16));dr=ImageDraw.Draw(im)
 for y in range(16):
  for x in range(16):
   c=colors[rng.randrange(len(colors))]
   if name=='wood':c=colors[(y//3+rng.randrange(2))%len(colors)]
   if name=='sisal':c=colors[(y//2)%len(colors)]
   if name=='water':c=colors[(x//4+y//3)%len(colors)]
   im.putpixel((x,y),tuple(int(c[i:i+2],16) for i in (1,3,5)))
 if name in ['pink','plum']:
  dr.line((0,0,15,0),fill='#f0dac2');dr.line((0,0,0,15),fill='#f0dac2')
 if name=='pink':
  for x,y in [(4,4),(12,12)]:dr.line((x-1,y,x+1,y),fill='#f7dbe7');dr.line((x,y-1,x,y+1),fill='#f7dbe7')
 if name=='leaf':dr.line((0,8,15,8),fill='#b9d978');dr.line((3,4,7,8),fill='#b9d978');dr.line((8,8,12,12),fill='#b9d978')
 im.save(a/f'textures/block/cozy_{name}.png')
# Distinct top-face motif textures, directly mapped to model faces.
im=Image.open(a/'textures/block/cozy_plum.png');dr=ImageDraw.Draw(im)
for box in [(6,9,10,11),(7,7,9,9),(4,5,5,6),(7,4,8,5),(10,5,11,6),(12,7,13,8)]:dr.rectangle(box,fill='#e1b758')
im.save(a/'textures/block/cozy_paw.png')
im=Image.new('RGB',(16,16),'#f3e8d1');dr=ImageDraw.Draw(im);dr.line((8,7,8,13),fill='#739951',width=2)
for box in [(6,3,9,5),(4,6,6,8),(9,6,11,8),(6,9,9,11)]:dr.rectangle(box,fill='#dd7bac')
dr.rectangle((7,6,8,8),fill='#e7c465');dr.rectangle((9,11,11,12),fill='#739951');im.save(a/'textures/block/cozy_drawing.png')
im=Image.open(a/'textures/block/cozy_pink.png');ImageDraw.Draw(im).rectangle((0,0,15,1),fill='#c96494');im.save(a/'textures/block/cozy_pencil.png')
models={}
faces=('down','up','north','south','west','east')
def cube(lo,hi,texture,top=None):
 return {'from':lo,'to':hi,'faces':{face:{'uv':[0,0,16,16],'texture':'#'+(top if face=='up' and top else texture)} for face in faces}}
def make(name,elems):
 tex={n:'dudunka:block/cozy_'+n for n in list(palettes)+['paw','drawing','pencil']};tex['particle']=tex['wood']
 model={'textures':tex,'elements':elems,'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.75,.75,.75]},'ground':{'translation':[0,2,0],'scale':[.4,.4,.4]},'fixed':{'rotation':[0,180,0],'scale':[.5,.5,.5]},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]},'firstperson_righthand':{'rotation':[0,45,0],'scale':[.4,.4,.4]}}}
 models[name]=model
 (a/f'models/block/{name}.json').write_text(json.dumps(model,indent=2)+'\n')
 (a/f'models/item/{name}.json').write_text(json.dumps({'parent':'dudunka:block/'+name},indent=2)+'\n')
 (a/f'blockstates/{name}.json').write_text(json.dumps({'variants':{'facing='+direction:{'model':'dudunka:block/'+name,'y':rotation,'uvlock':False} for direction,rotation in [('north',0),('east',90),('south',180),('west',270)]}},indent=2)+'\n')
make('dudunka_home',[cube([1,1,1],[15,3,15],'wood'),cube([2,3,2],[14,5,13],'pink'),cube([3,5,10],[13,6,13],'cream'),cube([1,3,13],[15,8,15],'wood')]+[cube([x,0,z],[x+2,1,z+2],'wood') for x in [2,12] for z in [2,12]])
make('marusya_home',[cube([1,0,1],[15,2,15],'plum','paw'),cube([2,2,2],[14,3,14],'plum','paw')]+[cube([x,0,z],[x+2,3,z+2],'cream') for x in [1,13] for z in [1,13]])
make('syusya_home',[cube([1,0,1],[15,1,15],'wood'),cube([1,1,1],[4,8,15],'moss'),cube([12,1,1],[15,8,15],'moss'),cube([4,1,12],[12,8,15],'darkwood'),cube([1,8,1],[15,10,15],'moss')]+[cube([x,3,1],[x+1,8,2],'wood') for x in [1,14]])
make('dudunka_furniture',[cube([1,8,1],[15,10,15],'wood'),cube([3,10,4],[11,10.125,12],'cream','drawing'),cube([12,10,3],[13,10.5,11],'pencil'),cube([13,10,5],[14,10.5,12],'leaf')]+[cube([x,0,z],[x+2,8,z+2],'wood') for x in [2,12] for z in [2,12]])
make('marusya_furniture',[cube([1,0,1],[15,2,15],'wood'),cube([2,2,3],[10,4,14],'plum','paw'),cube([1,2,2],[2,4,15],'wood'),cube([2,2,14],[11,4,15],'wood'),cube([11,2,3],[14,13,6],'sisal'),cube([10,13,2],[15,14,7],'wood')])
make('syusya_furniture',[cube([1,0,1],[15,1,15],'wood','moss'),cube([3,1,8],[5,8,14],'wood'),cube([11,1,8],[13,8,14],'wood'),cube([5,1,13],[11,8,14],'darkwood'),cube([3,8,8],[13,10,14],'wood'),cube([5,10,9],[11,12,14],'wood'),cube([7,12,10],[9,13,13],'wood'),cube([3,1,2],[9,1.25,6],'leaf'),cube([2,1,3],[3,1.25,5],'leaf'),cube([9,1,3],[10,1.25,5],'leaf'),cube([11,1,2],[14,1.5,5],'water')])
# Decorative props have no inventory and are not food resources.
recipes={'dudunka_furniture':(['PPP','IPI','S S'],{'P':'minecraft:oak_planks','I':'minecraft:paper','S':'minecraft:stick'}),'marusya_furniture':(['  S','W S','PPP'],{'S':'minecraft:string','W':'minecraft:purple_wool','P':'minecraft:oak_planks'}),'syusya_furniture':(['PPP','P P','MLM'],{'P':'minecraft:oak_planks','M':'minecraft:moss_block','L':'minecraft:oak_leaves'})}
for name,(pattern,keys) in recipes.items():
 (d/f'recipes/{name}.json').write_text(json.dumps({'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v} for k,v in keys.items()},'result':{'item':'dudunka:'+name}},indent=2)+'\n')
 (d/f'loot_tables/blocks/{name}.json').write_text(json.dumps({'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'dudunka:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]},indent=2)+'\n')
print('Generated six models and 12 pixel textures')
