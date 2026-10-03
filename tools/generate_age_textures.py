"""Deterministic UV color tiles for the grown Dudunka models; baby atlas is untouched.
Run with Python + Pillow. Geometry uses one 64x64 tile per material.
"""
from pathlib import Path
from PIL import Image, ImageDraw
COLORS=['#efc8a5','#77472f','#798365','#16171c','#71462d','#b56a25','#bcb293','#edc64c','#272830','#bb8592','#efdfc7','#f4ecd9','#65734f','#965732','#a95645','#58351f']
def generate(root):
    for name,adult in [('teen',False),('adult',True)]:
        atlas=Image.new('RGB',(256,256));draw=ImageDraw.Draw(atlas)
        for i,hexcolor in enumerate(COLORS):
            x,y=i%4*64,i//4*64;rgb=tuple(bytes.fromhex(hexcolor[1:]));draw.rectangle((x,y,x+63,y+63),fill=rgb)
            if i in (1,2,4,5,10,12,13):
                step=4 if adult else 8
                for dx in range(0,64,step):
                    for dy in range(0,64,step):
                        offset=((dx//step*7+dy//step*3+i)%5-2)*(3 if adult else 2)
                        shade=tuple(max(0,min(255,c+offset)) for c in rgb)
                        draw.rectangle((x+dx,y+dy,x+dx+step-1,y+dy+step-1),fill=shade)
        atlas.save(root/f'dudunka_{name}.png')
if __name__=='__main__':generate(Path(__file__).resolve().parents[1]/'src/main/resources/assets/dudunka/textures/entity')
