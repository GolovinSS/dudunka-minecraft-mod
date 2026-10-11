"""Original deterministic pixel atlas for the approved elder's code-native model."""
from pathlib import Path
from PIL import Image
im=Image.new('RGBA',(128,64),(241,219,181,255))
def tile(x0,y0,w,h,palette):
 for y in range(y0,y0+h):
  for x in range(x0,x0+w):
   n=((x//2)*7+(y//2)*11)%17
   shade=0 if n<4 else 2 if n>12 else 1
   im.putpixel((x,y),(*palette[shade],255))
tile(0,0,40,32,[(207,177,136),(239,212,170),(255,231,194)])
tile(0,32,40,16,[(218,204,177),(248,237,208),(255,247,226)])
tile(0,48,40,16,[(217,192,143),(249,233,191),(255,248,215)])
tile(40,0,40,32,[(163,31,27),(210,43,32),(246,75,48)])
tile(40,32,40,16,[(47,69,35),(79,104,43),(121,140,60)])
tile(40,48,40,16,[(151,115,65),(196,157,97),(228,191,125)])
tile(80,0,32,32,[(86,47,26),(124,73,36),(163,102,49)])
tile(80,32,32,32,[(65,37,24),(93,51,27),(127,74,34)])
tile(112,0,16,16,[(27,22,19),(37,29,23),(48,37,28)])
tile(112,16,8,16,[(255,249,222)]*3)
tile(120,16,8,16,[(237,140,113),(247,157,132),(255,176,148)])
tile(112,32,16,16,[(243,146,38),(255,195,65),(255,226,126)])
tile(112,48,16,16,[(176,104,30),(222,151,47),(247,193,79)])
# Tiny ink lines on the book's upward-facing paper UVs.
for y in (50,52,54):
 for x in (3,4,5,7,8,9):im.putpixel((x,y),(139,111,72,255))
out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/dudunka/textures/entity/wise_fly_agaric.png'
im.save(out);print(out)
