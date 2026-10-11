"""Reproduce Stage 2.4a polished blueprints and unchanged biome-local placements.
Run generate_vale_polish.py first to regenerate the original model/texture palette.
Run after generate_vale_vegetation.py if regenerating both stages.
"""
from pathlib import Path
import json

RES = Path(__file__).resolve().parents[1] / 'src/main/resources/data/dudunka/worldgen'
PREFIX = 'dudunka:mushroom_vale/'
NAMES = ['pufik_cottage', 'wise_fly_agaric_cottage', 'lantern_post', 'direction_marker',
         'bench', 'mushroom_picnic', 'flower_bed', 'cozy_cluster']


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


class Build:
    def __init__(self):
        self.blocks = {}

    def put(self, x, y, z, block, **props):
        state = {'Name': block if ':' in block else 'minecraft:' + block}
        if props:
            state['Properties'] = {k: str(v).lower() for k, v in props.items()}
        self.blocks[x, y, z] = state

    def prop(self, x, y, z, name, facing='north'):
        self.put(x, y, z, 'dudunka:vale_' + name, facing=facing)

    def bench(self, x, z):
        for dx in range(-1, 2):
            self.prop(x + dx, 0, z, 'bench')

    def lantern(self, x, z):
        self.put(x, 0, z, 'stripped_spruce_log', axis='y')
        self.put(x, 1, z, 'spruce_fence')
        self.put(x, 2, z, 'spruce_fence')
        self.prop(x, 3, z, 'lantern')

    def table(self, x, z):
        self.prop(x, 0, z, 'mushroom_table')
        for dx in (-2, 2):
            self.prop(x + dx, 0, z, 'mushroom_stool')

    def flowers(self, x, z):
        for dx in (-1, 0, 1):
            self.prop(x + dx, 0, z, 'planter')
        for dz in (-1, 1):
            for dx in (-1, 0, 1):
                self.put(x + dx, 0, z + dz, 'dudunka:glowing_moss')

    def save(self, name, radius, height, rarity):
        write(RES / 'configured_feature/mushroom_vale' / (name + '.json'), {
            'type': 'dudunka:mushroom_vale_decor', 'config': {
                'radius': radius, 'height': height,
                'blocks': [{'offset': list(p), 'state': s} for p, s in sorted(self.blocks.items())]}})
        write(RES / 'placed_feature/mushroom_vale' / (name + '.json'), {
            'feature': PREFIX + name, 'placement': [
                {'type': 'minecraft:rarity_filter', 'chance': rarity},
                {'type': 'minecraft:in_square'},
                {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR_WG'},
                {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': 0},
                {'type': 'minecraft:biome'}]})


b = Build(); b.lantern(0, 0)
b.prop(-1, 0, 0, 'planter'); b.save('lantern_post', 1, 4, 64)
b = Build()
b.put(0, 0, 0, 'stripped_spruce_log', axis='y')
b.prop(0, 1, 0, 'signpost'); b.prop(0, 2, 0, 'lantern')
b.put(-1, 0, 0, 'dudunka:small_golden_mushroom'); b.save('direction_marker', 1, 3, 48)
b = Build(); b.bench(0, 0); b.save('bench', 1, 2, 8)
b = Build(); b.table(0, 0); b.prop(0, 0, 2, 'planter'); b.save('mushroom_picnic', 2, 2, 48)
b = Build(); b.flowers(0, 0); b.save('flower_bed', 1, 2, 32)
b = Build(); b.bench(-1, 0); b.lantern(2, 1)
b.prop(-2, 0, -1, 'planter'); b.prop(1, 0, -1, 'mushroom_stool')
b.prop(2, 0, -1, 'book_stack'); b.save('cozy_cluster', 3, 4, 64)


def cottage(scholarly):
    b = Build()
    r = 3 if scholarly else 2
    wall_height = 4 if scholarly else 3
    # Pufik: compact clipped circle. Wise: long library with an asymmetric study bay.
    footprint = ({(x, z) for x in range(-2, 3) for z in range(-3, 4)} |
                 {(3, z) for z in (0, 1)}) if scholarly else {
                     (x, z) for x in range(-2, 3) for z in range(-2, 3)
                     if not (abs(x) == 2 and abs(z) == 2)}
    boundary = {p for p in footprint if any((p[0]+dx, p[1]+dz) not in footprint
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    for x, z in sorted(footprint):
        b.put(x, 0, z, 'birch_planks')
        if (x, z) not in boundary:
            continue
        for y in range(1, wall_height + 1):
            # Exactly one two-block door aperture; jambs and lintel are solid.
            if x == 0 and z == -r and y <= 2:
                continue
            timber = scholarly and (y == wall_height or abs(x) == 2 and abs(z) == 3)
            b.put(x, y, z, 'stripped_spruce_wood' if timber else 'stripped_birch_wood', axis='y')
    # Full glass backs the ornamental windows: the thin frame is never the wall.
    for x, z, facing, dx, dz in [(0, r, 'south', 0, 1), (-2, 0, 'west', -1, 0),
                                  (3 if scholarly else 2, 0, 'east', 1, 0)]:
        b.put(x, 2, z, 'glass')
        b.prop(x+dx, 2, z+dz, 'cottage_window', facing)
        b.prop(x+dx, 0, z+dz, 'planter', facing)
    # Broad low dome versus a tall, elongated cap with an off-centre crown.
    for tier in range(4):
        y = wall_height + 1 + tier
        rx = max(0, 3-tier)
        rz = max(0, (4 if scholarly else 3)-tier)
        cx = 1 if scholarly and tier >= 2 else 0
        for x in range(cx-rx, cx+rx+1):
            for z in range(-rz, rz+1):
                if rx > 0 and rz > 0 and abs(x-cx) == rx and abs(z) == rz:
                    continue
                b.prop(x, y, z, 'cottage_eave' if tier == 0 else
                       ('cottage_crown' if tier == 3 else 'cottage_cap'))
    # Seal the entire stem with a solid ceiling beneath the decorative roof.
    for x, z in footprint:
        b.put(x, wall_height+1, z, 'red_mushroom_block')
    for half, y in [('lower', 1), ('upper', 2)]:
        b.put(0, y, -r, 'spruce_door', facing='north', half=half, hinge='left', open=False, powered=False)
    for x in range(-1, 2):
        for z in range(-r-2, -r):
            b.put(x, 0, z, 'birch_slab', type='bottom')
    for x in (-2, 2):
        b.prop(x, 0, -r-1, 'planter')
        b.put(x, 1, -r-1, 'spruce_fence')
        b.prop(x, 2, -r-1, 'lantern')
    # Interior furniture leaves the central door-to-back walking route clear.
    if scholarly:
        for z in (0, 1, 2):
            b.put(-1, 1, z, 'bookshelf')
            b.put(-1, 2, z, 'bookshelf')
        b.put(1, 1, 1, 'lectern', facing='west', has_book=False, powered=False)
        b.prop(1, 1, 2, 'book_stack')
        b.prop(1, 3, 2, 'lantern')
        # High transom and paired side windows distinguish the scholar's facade.
        b.put(0, 3, -r, 'glass')
        b.prop(0, 3, -r-1, 'cottage_window')
        b.put(-2, 3, 1, 'glass')
        b.prop(-3, 3, 1, 'cottage_window', 'west')
        # Timber porch, separate from the cream library shell.
        for x in (-2, 2):
            for y in (1, 2, 3):
                b.put(x, y, -r-2, 'stripped_spruce_log', axis='y')
        for x in range(-2, 3):
            b.put(x, 4, -r-2, 'spruce_slab', type='bottom')
        b.bench(0, r+2)
        b.save('wise_fly_agaric_cottage', 6, 9, 32)
    else:
        b.prop(-1, 1, 0, 'mushroom_stool')
        b.prop(1, 1, 1, 'mushroom_table')
        b.prop(-1, 0, r+1, 'planter')
        b.save('pufik_cottage', 4, 8, 24)


cottage(False); cottage(True)
for path in (RES / 'biome').glob('*.json'):
    biome = json.loads(path.read_text())
    step = biome['features'][9]
    step[:] = [f for f in step if f not in [PREFIX + n for n in NAMES]]
    local = ([NAMES[0]] if path.stem == 'pink_meadows' else
             [NAMES[1]] if path.stem == 'memory_forest' else [])
    step[:0] = [PREFIX + n for n in local + NAMES[2:]]
    write(path, biome)
print('Generated 8 decor/exterior blueprint pairs and updated 3 Vale biome lists.')
