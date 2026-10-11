"""Original, deterministic UV atlas for the code-native rounded spider model.

Ivory cloth-like pixel clusters, shaded soft paws and stitched terracotta cap;
separate dark/cream/blush tiles keep its layered facial features readable.
"""
from pathlib import Path
from PIL import Image
import random

OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/dudunka/textures/entity/vale_spider.png'
im = Image.new('RGBA', (128, 64), (244, 223, 192, 255))
rng = random.Random(3301)
def tile(x0, y0, w, h, palette):
    for y in range(y0, y0+h):
        for x in range(x0, x0+w):
            # Small coherent two-pixel clusters with sparse highlights, never photo noise.
            shade = ((x//2)*7+(y//2)*11) % 13
            index = 0 if shade < 3 else 2 if shade > 10 else 1
            if rng.randrange(29)==0: index=2
            im.putpixel((x,y), (*palette[index],255))
tile(0,0,32,24,[(226,199,169),(248,228,198),(255,240,214)])
tile(0,24,32,24,[(211,180,151),(236,211,180),(252,232,202)])
tile(32,0,32,24,[(154,65,48),(192,82,55),(226,112,68)])
tile(32,24,32,24,[(117,52,43),(153,62,46),(185,79,52)])
tile(64,24,12,8,[(136,57,44),(178,75,51),(213,98,62)])
# Thin seam and worn brim pixels belong only to the cap atlas region.
for x in range(33,63):
    im.putpixel((x,7), (236,128,80,255) if x%4 else (168,67,48,255))
for x0,y0,w,h,color in [(80,0,12,10,(49,31,29)),(96,0,8,8,(255,246,231)),
                         (80,16,12,6,(77,42,31)),(104,16,8,8,(236,133,125))]:
    for y in range(y0,y0+h):
        for x in range(x0,x0+w): im.putpixel((x,y),(*color,255))
im.save(OUT)
print(OUT)
