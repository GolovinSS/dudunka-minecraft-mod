"""Original Stage 2.4a pixel textures and voxel block models; Python + Pillow only.
Run this, then generate_vale_decor.py. No reference image pixels are copied.
"""
from pathlib import Path
import json, random
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[1]
A = ROOT/'src/main/resources/assets/dudunka'
D = ROOT/'src/main/resources/data/dudunka'
def write(p,v):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n')
palettes = {
 'wood':['#553521','#71452a','#925e36','#bc8751'],
 'cream':['#b59b71','#d1b98c','#e6d4aa','#fff0c7'],
 'red':['#82283d','#a82e45','#ca4350','#e76b62'],
 'gold':['#69442c','#a87436','#dda84d','#ffe09a'],
 'amber':['#bb662c','#e99c39','#ffcd62','#fff1b5'],
 'leaf':['#2c5345','#427557','#679558','#a8bd70'],
 'pink':['#843c79','#b75b9c','#e58bc0','#ffe0ec'],
 'violet':['#463756','#65527c','#9582af','#d4b9dd'],
 'paper':['#a98b61','#d1b68a','#f0dfb3','#fff1cf']}
for name,colors in palettes.items():
    rng=random.Random('vale-polish-'+name)
    im=Image.new('RGB',(16,16)); d=ImageDraw.Draw(im)
    for y in range(16):
        for x in range(16):
            # Restrained grain rather than independent high-contrast noise.
            index=(x//4 + (y//7 if name=='red' else 0))%3
            if rng.randrange(9)==0: index=min(3,index+1)
            d.point((x,y),fill=colors[index])
    if name=='wood':
        for x in (0,7,15): d.line((x,0,x,15),fill=colors[0])
        d.rectangle((3,6,5,9),outline=colors[0]); d.point((4,7),fill=colors[3])
    if name=='cream':
        for x in (2,6,10,14): d.line((x,0,x,15),fill=colors[1])
    if name=='red':
        for x,y in ((3,3),(11,12)):
            d.rectangle((x,y,x+2,y+1),fill='#fff0d4'); d.point((x+1,y-1),fill='#e6d4aa')
    if name=='amber':
        d.rectangle((2,2,13,13),fill=colors[2]); d.rectangle((5,3,10,12),fill=colors[3])
    if name=='paper':
        for y in (4,6,8,10): d.line((3,y,12,y),fill=colors[1])
    p=A/'textures/block'/('vale_decor_'+name+'.png'); p.parent.mkdir(parents=True,exist_ok=True); im.save(p)
F=('down','up','north','south','west','east')
def box(lo,hi,tex,top=None):
    return {'from':lo,'to':hi,'faces':{f:{'texture':'#'+(top if f=='up' and top else tex),'uv':[0,0,16,16]} for f in F}}
models={}
def make(name,els): models[name]=els
B=box
# Lantern: foot, framed amber chamber, tiered mushroom canopy and brass finial.
make('vale_lantern',[B([3,0,3],[13,2,13],'wood'),B([5,2,5],[11,10,11],'amber'),
 B([3,10,3],[13,12,13],'gold'),B([1,12,1],[15,14,15],'red'),B([3,14,3],[13,15,13],'red'),
 B([6,15,6],[10,16,10],'gold')]+[B([x,2,z],[x+1,10,z+1],'gold') for x in (3,12) for z in (3,12)])
# Per-block bench sections form a continuous three-block seat with real slats and feet.
make('vale_bench',[B([0,6,2],[16,8,6],'wood'),B([0,6,7],[16,8,11],'wood'),
 B([0,10,13],[16,12,15],'wood'),B([0,13,13],[16,15,15],'wood')]+
 [B([x,0,z],[x+2,6,z+2],'wood') for x in (2,12) for z in (3,10)]+
 [B([x,6,12],[x+2,16,14],'gold') for x in (1,13)])
make('vale_signpost',[B([6,0,6],[10,16,10],'wood'),B([1,9,3],[13,14,6],'wood'),
 B([0,10,3],[1,13,6],'gold'),B([13,10,3],[15,13,6],'gold'),
 B([3,2,10],[15,7,13],'wood'),B([1,3,10],[3,6,13],'gold'),
 B([5,11,2],[8,13,3],'red'),B([6,10,2],[7,11,3],'cream'),B([8,4,13],[11,6,14],'violet')])
def cap(y):
    return [B([0,y,1],[16,y+2,15],'cream'),B([0,y+2,1],[16,y+4,15],'red'),
            B([1,y+4,2],[15,y+5,14],'red'),B([3,y+5,4],[13,y+6,12],'red')]
make('vale_mushroom_table',[B([5,0,5],[11,10,11],'cream'),B([3,0,3],[13,2,13],'wood')]+cap(10))
make('vale_mushroom_stool',[B([6,0,6],[10,4,10],'cream'),B([4,0,4],[12,1,12],'wood')]+cap(4))
make('vale_planter',[B([2,0,2],[14,7,14],'wood'),B([1,5,1],[15,8,15],'gold'),
 B([3,8,3],[13,9,13],'leaf')]+[B([x,9,z],[x+1,14,z+1],'leaf') for x,z in ((4,5),(9,9),(11,4))]+
 [B([x-2,13,z-1],[x+3,15,z+2],'pink') for x,z in ((4,5),(9,9),(11,4))]+
 [B([x,15,z],[x+1,16,z+1],'amber') for x,z in ((4,5),(9,9),(11,4))])
# Wall inserts stay within their block; warm opaque panes avoid translucent sorting artifacts.
make('vale_cottage_window',[B([0,0,1],[16,16,16],'cream'),B([2,2,.5],[14,14,1],'wood'),
 B([3,3,.25],[13,13,.5],'amber'),B([7,3,0],[9,13,.25],'gold'),B([3,7,0],[13,9,.25],'gold'),
 B([1,0,0],[15,2,3],'wood'),B([1,14,0],[15,16,2],'wood')])
make('vale_cottage_cap',[B([0,0,0],[16,16,16],'red')])
make('vale_cottage_crown',[B([0,0,0],[16,6,16],'red'),B([2,6,2],[14,9,14],'red'),B([4,9,4],[12,11,12],'red')])
make('vale_cottage_eave',[B([0,6,0],[16,16,16],'red'),B([1,2,1],[15,6,15],'cream')]+
 [B([x,0,2],[x+1,2,14],'cream') for x in (2,5,8,11,14)])
make('vale_carved_wood',[B([0,0,1],[16,16,16],'wood'),B([2,2,.5],[14,14,1],'gold'),
 B([3,3,.25],[13,13,.5],'wood'),B([5,8,0],[11,10,.125],'red'),B([6,10,0],[10,11,.125],'red'),
 B([7,5,0],[9,8,.125],'cream')])
make('vale_book_stack',[B([2,0,2],[14,2,13],'wood'),B([3,2,3],[13,4,12],'paper'),B([2,4,2],[14,5,13],'violet'),
 B([1,5,4],[13,6,14],'gold'),B([2,6,5],[12,8,13],'paper'),B([1,8,4],[13,9,14],'gold'),
 B([4,9,2],[14,10,12],'red'),B([5,10,3],[13,12,11],'paper'),B([4,12,2],[14,13,12],'red')])
for name,els in models.items():
    write(A/'models/block'/(name+'.json'),{'textures':{**{k:'dudunka:block/vale_decor_'+k for k in palettes},'particle':'dudunka:block/vale_decor_wood'},'elements':els,
      'display':{'gui':{'rotation':[30,225,0],'scale':[.65,.65,.65]},'ground':{'translation':[0,2,0],'scale':[.4,.4,.4]}}})
    write(A/'models/item'/(name+'.json'),{'parent':'dudunka:block/'+name})
    write(A/'blockstates'/(name+'.json'),{'variants':{'facing='+f:{'model':'dudunka:block/'+name,'y':y} for f,y in [('north',0),('east',90),('south',180),('west',270)]}})
    write(D/'loot_tables/blocks'/(name+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'conditions':[{'condition':'minecraft:survives_explosion'}],'entries':[{'type':'minecraft:item','name':'dudunka:'+name}]}]})
# Java uses the same cuboids for shape/collision as the visible model, rotated with the state.
java=ROOT/'src/main/java/io/github/golovinss/dudunka/MushroomValeDecorShapes.java'
lines=['package io.github.golovinss.dudunka;','', '/** Generated by tools/generate_vale_polish.py; model geometry is the shape source. */','final class MushroomValeDecorShapes {','    static double[][] boxes(String id) {','        return switch (id) {']
for name,els in models.items():
    boxes=', '.join('{'+', '.join(str(v) for v in e['from']+e['to'])+'}' for e in els)
    lines.append('            case "'+name+'" -> new double[][]{'+boxes+'};')
lines+=['            default -> throw new IllegalArgumentException(id);','        };','    }','    private MushroomValeDecorShapes() {}','}']
java.write_text('\n'.join(lines)+'\n')
labels={
'vale_lantern':('Vale Mushroom Lantern','Грибной фонарь долины'), 'vale_bench':('Vale Slatted Bench','Резная скамья долины'),
'vale_signpost':('Vale Mushroom Signpost','Грибной указатель долины'), 'vale_mushroom_table':('Mushroom Picnic Table','Грибной столик'),
'vale_mushroom_stool':('Mushroom Stool','Грибной табурет'), 'vale_planter':('Vale Flower Planter','Цветочная кадка долины'),
'vale_cottage_window':('Amber Cottage Window','Янтарное окно домика'), 'vale_cottage_cap':('Spotted Cottage Cap','Пятнистая шляпка домика'),
'vale_cottage_crown':('Rounded Cottage Crown','Округлая вершина домика'), 'vale_cottage_eave':('Cream Gilled Eave','Карниз с кремовыми пластинками'), 'vale_carved_wood':('Carved Mushroom Wood','Дерево с резным грибочком'),
'vale_book_stack':('Storykeeper Book Stack','Стопка книг хранителя историй')}
for i,lang in enumerate(('en_us','ru_ru')):
    p=A/'lang'/(lang+'.json'); v=json.loads(p.read_text());v.update({'block.dudunka.'+k:n[i] for k,n in labels.items()});write(p,v)
print(f'Generated {len(models)} models, matching shapes and 9 original pixel textures.')
