"""Shared pixel motifs for tabletop textures and all age models' held drawings.
Run after generate_furniture.py. Python + Pillow; outputs are ordinary mod assets.
"""
from pathlib import Path
from PIL import Image,ImageDraw
import json,copy
root=Path(__file__).resolve().parents[1];a=root/'src/main/resources/assets/dudunka'
# Twelve by eight; dots are the paper background. P=pink G=green K=black Y=gold T=tan B=brown.
PATTERNS=[[
'.....PP.....','...PPPPPP...','.....YY.....','...PPPPPP...','.....PP.....','.....G......','...GGGGG....','.....G......'],[
'...K....K...','...KK..KK...','..KKKKKKKK..','..KYYKKYYK..','..KKKKKKKK..','...KPPPPK...','....KKKK....','............'],[
'.....BBBB...','....BTTTTB..','...BTBBTTB..','...BTBTBTB..','...BTTBBTB..','....BBBBB...','..TTTTTTTT..','.TTTTTTTTTT.']]
COLORS={'P':'#dc80ac','G':'#73994c','K':'#25212a','Y':'#e8c45c','T':'#d7b786','B':'#9b643a'}
for variant,pattern in enumerate(PATTERNS):
 assert len(pattern)==8 and all(len(row)==12 for row in pattern)
 im=Image.new('RGB',(16,16),'#f3e8d1');dr=ImageDraw.Draw(im)
 for y,row in enumerate(pattern):
  for x,c in enumerate(row):
   if c!='.':dr.point((x+2,y+4),fill=COLORS[c])
 name=['cozy_drawing','cozy_drawing_marusya','cozy_drawing_syusya'][variant];im.save(a/f'textures/block/{name}.png')
base=json.loads((a/'models/block/dudunka_furniture.json').read_text())
variants={}
for picture in range(3):
 name='dudunka_furniture'+('' if picture==0 else '_picture_'+str(picture))
 model=copy.deepcopy(base);model['textures']['drawing']='dudunka:block/'+['cozy_drawing','cozy_drawing_marusya','cozy_drawing_syusya'][picture]
 if picture:(a/f'models/block/{name}.json').write_text(json.dumps(model,indent=2)+'\n')
 for direction,rotation in [('north',0),('east',90),('south',180),('west',270)]:variants['facing='+direction+',picture='+str(picture)]={'model':'dudunka:block/'+name,'y':rotation,'uvlock':False}
(a/'blockstates/dudunka_furniture.json').write_text(json.dumps({'variants':variants},indent=2)+'\n')
# Small colored notes keep each friend recognizable in inventory; no recipes.
for kind,pattern in [('marusya',PATTERNS[1]),('syusya',PATTERNS[2])]:
 for page in range(1,4):
  im=Image.new('RGBA',(16,16),(0,0,0,0));dr=ImageDraw.Draw(im);dr.rectangle((2,1,13,14),fill='#ece0bf',outline='#b8996b');dr.rectangle((11,1,13,3),fill='#d1bc8e')
  for y,row in enumerate(pattern):
   for x,c in enumerate(row):
    if c!='.' and 2+x<13:dr.point((2+x,3+y),fill=COLORS[c])
  for n in range(page):dr.rectangle((4+n*3,12,5+n*3,12),fill='#7a5638')
  (a/'textures/item').mkdir(exist_ok=True);im.save(a/f'textures/item/{kind}_note_{page}.png')
  (a/f'models/item/{kind}_note_{page}.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'dudunka:item/{kind}_note_{page}'}},indent=2)+'\n')
# A single definition builds three hidden motif groups on the held sheet, using the unchanged atlases.
java='''package io.github.golovinss.dudunka.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from the same 12x8 motifs as the table's textures; regenerate with tools/generate_character_moments.py. */
public final class DrawingMotifs {
    private DrawingMotifs(){}
    private static final String[][] PIXELS={
'''
for pattern in PATTERNS:java+='        {'+','.join(json.dumps(row) for row in pattern)+'},\n'
java+='''    };
    public static void add(PartDefinition sheet,float width,float y,float z,boolean baby){
        float unit=width/12;
        for(int v=0;v<3;v++){
            var group=sheet.addOrReplaceChild("motif"+v,CubeListBuilder.create(),PartPose.ZERO);
            for(int row=0;row<8;row++)for(int col=0;col<12;col++){
                char c=PIXELS[v][row].charAt(col);if(c=='.')continue;
                int tile=switch(c){case 'P'->baby?2:9;case 'G'->baby?6:12;case 'K'->3;case 'Y'->7;case 'T'->6;default->5;};
                group.addOrReplaceChild("pixel_"+row+"_"+col,CubeListBuilder.create().texOffs(tile%4*64,tile/4*64)
                    .addBox(-width/2+col*unit,y+.08f+row*unit,z,unit,unit,.035f),PartPose.ZERO);
            }
        }
    }
}
'''
(root/'src/main/java/io/github/golovinss/dudunka/client/DrawingMotifs.java').write_text(java)
board=Image.new('RGB',(840,380),'#f5f0e7');dr=ImageDraw.Draw(board)
from PIL import ImageFont
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',24)
for i,name in enumerate(['cozy_drawing','cozy_drawing_marusya','cozy_drawing_syusya']):
 board.paste(Image.open(a/f'textures/block/{name}.png').resize((224,224),Image.Resampling.NEAREST),(28+i*280,80));dr.text((140+i*280,40),['Цветок','Маруся','Сюся'][i],font=font,fill='#493225',anchor='mm')
dr.text((420,345),'Три рисунка · игровые пиксельные текстуры',font=font,fill='#493225',anchor='mm');board.save(root/'docs/DRAWINGS-PREVIEW.png')
print('Generated three matching drawings, six note icons, 12 table variants and held motif geometry')
