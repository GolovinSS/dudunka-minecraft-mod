"""Original code-native 32x16 pixel atlases for the small voxel firefly model."""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/dudunka/textures/entity'
for name, dark, mid, bright in [
    ('golden', (155, 89, 22), (249, 186, 53), (255, 242, 156)),
    ('turquoise', (20, 104, 119), (58, 210, 216), (180, 255, 247)),
    ('violet', (91, 47, 137), (184, 114, 232), (245, 205, 255)),
]:
    base = Image.new('RGBA', (32, 16), (*dark, 255))
    glow = Image.new('RGBA', (32, 16))
    for y in range(16):
        for x in range(32):
            if x < 12 and y < 11:
                color = bright if (x + y * 2) % 7 < 2 else mid
                base.putpixel((x, y), (*color, 255))
                glow.putpixel((x, y), (*color, 255))
            elif 12 <= x < 22 and y < 5:
                base.putpixel((x, y), (*(bright if (x+y) % 5 == 0 else mid), 255))
            elif 12 <= x < 22 and 6 <= y < 12:
                base.putpixel((x, y), (*(bright if (x+y) % 3 == 0 else mid), 150 if (x+y) % 3 else 220))
            elif x >= 22 and y < 3:
                base.putpixel((x, y), (255,255,239,255) if y == 0 and x % 3 == 0 else (26,22,35,255))
    base.save(OUT / f'vale_firefly_{name}.png')
    glow.save(OUT / f'vale_firefly_{name}_glow.png')
