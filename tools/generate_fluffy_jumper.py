"""Original deterministic wool/face pixel atlases for the code-native Fluffy Jumper model."""
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
for i,base in enumerate(((244,185,186),(181,147,224),(154,213,228))):
 im=Image.new('RGBA',(128,64),(*base,255))
 def tile(x0,y0,w,h,color,noise=0):
  for y in range(y0,y0+h):
   for x in range(x0,x0+w):
    shade=(((x//2)*7+(y//2)*11)%7-3)*noise
    im.putpixel((x,y),tuple(max(0,min(255,c+shade)) for c in color)+(255,))
 tile(0,0,32,24,base,3)
 tile(32,0,24,24,tuple(min(255,c+14) for c in base),2)
 tile(0,24,32,20,tuple(min(255,c+22) for c in base),2)
 tile(64,0,8,8,(39,30,49),1)
 tile(64,8,8,8,(91,53,76),0)
 tile(72,0,8,8,(255,252,243),0)
 tile(80,0,12,8,(239,132,162),1)
 im.save(ROOT/f'src/main/resources/assets/dudunka/textures/entity/fluffy_jumper_{i}.png')
