"""Author original 16px Vale textures, block models and vanilla-codec worldgen.

Run from any directory with Python + Pillow (same offline tooling as furniture).
No runtime dependency; checked-in JSON/PNG resources are consumed by Minecraft.
"""
from pathlib import Path
import json
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/dudunka'
DATA = ROOT / 'src/main/resources/data/dudunka'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


def asset(kind, name, value):
    write(ASSETS / kind / (name + '.json'), value)


def feature(kind, name, value):
    write(DATA / 'worldgen' / kind / 'mushroom_vale' / (name + '.json'), value)


PALETTES = {
    'turquoise': ('#186c78', '#228b93', '#39b6b4', '#b4fff1'),
    'violet': ('#573f80', '#7955a4', '#a578cf', '#e8ceff'),
    'golden': ('#a96828', '#ce943c', '#eebc59', '#fff1ad'),
    'pink': ('#944b80', '#c773a5', '#efa9d0', '#ffe2f1'),
}
CAPS = [c + '_mushroom_cap' for c in ('turquoise', 'violet', 'golden')] + ['vale_mushroom_stem']
PLANTS = {
    'glowing_moss': ('turquoise', 'moss'),
    'cyan_magic_flower': ('turquoise', 'flower'),
    'pink_magic_flower': ('pink', 'flower'),
    'violet_memory_flower': ('violet', 'flower'),
    'spore_bush': ('violet', 'bush'),
    **{'small_' + c + '_mushroom': (c, 'mushroom') for c in ('turquoise', 'violet', 'golden')},
}


def save_texture(name, im):
    path = ASSETS / 'textures/block' / (name + '.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)


def noise_texture(name, colors, stem=False):
    rng = random.Random(name)
    im = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(im)
    for y in range(16):
        for x in range(16):
            draw.point((x, y), fill=colors[(x // 3 + rng.randrange(2)) % 3] if stem else rng.choice(colors[:3]))
    if not stem:
        # Soft luminous flecks, deliberately different from the vanilla red cap dots.
        for x, y in [(3, 3), (11, 5), (6, 12), (14, 14)]:
            draw.rectangle((x - 1, y, x + 1, y + 1), fill=colors[3])
            draw.point((x, y - 1), fill=colors[2])
    save_texture(name, im)


for name in CAPS:
    stem = name == 'vale_mushroom_stem'
    colors = ('#c4b9ce', '#ded3df', '#eee5e4', '#f8f1e8') if stem else PALETTES[name.split('_')[0]]
    noise_texture(name, colors, stem)
    # Six face-state models preserve vanilla huge-mushroom inside/outside behavior.
    parts = []
    for face, rotation in [('north', {}), ('east', {'y': 90}), ('south', {'y': 180}),
                           ('west', {'y': 270}), ('up', {'x': 270}), ('down', {'x': 90})]:
        for exposed in (True, False):
            parts.append({'when': {face: str(exposed).lower()}, 'apply': {
                'model': 'dudunka:block/' + (name if exposed else 'vale_mushroom_inside'), **rotation}})
    asset('blockstates', name, {'multipart': parts})
    asset('models/block', name, {'parent': 'minecraft:block/template_single_face',
                                'textures': {'texture': 'dudunka:block/' + name}})
    asset('models/item', name, {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'dudunka:block/' + name}})

noise_texture('vale_mushroom_inside', ('#d1c1d8', '#e1d4e4', '#ede3ed', '#fff1fb'), True)
asset('models/block', 'vale_mushroom_inside', {'parent': 'minecraft:block/template_single_face',
                                             'textures': {'texture': 'dudunka:block/vale_mushroom_inside'}})

for name, (palette, shape) in PLANTS.items():
    colors = PALETTES[palette]
    im = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(im)
    if shape == 'moss':
        rng = random.Random(name)
        for y in range(16):
            for x in range(16):
                d.point((x, y), fill=rng.choice(('#28564e', '#346b5a', '#47866a', '#5f9b7b')))
        for x, y in [(2, 4), (10, 2), (6, 12), (14, 9)]:
            d.line((x - 1, y, x + 1, y), fill=colors[3])
            d.point((x, y - 1), fill=colors[2])
    elif shape == 'flower':
        d.line((7, 7, 7, 15), fill='#4f8a78', width=2)
        d.line((7, 13, 11, 10), fill='#83bda0', width=2)
        d.rectangle((3, 5, 12, 8), fill=colors[1])
        d.rectangle((5, 3, 10, 10), fill=colors[2])
        for x, y in [(4, 6), (10, 6), (7, 4), (7, 9)]:
            d.point((x, y), fill=colors[3])
        d.rectangle((7, 6, 8, 7), fill='#fff1ba')
    elif shape == 'mushroom':
        for cx, cy, size in [(5, 10, 4), (11, 13, 3)]:
            d.rectangle((cx, cy, cx + 1, 15), fill='#e1d4e4')
            d.rectangle((cx - size, cy - 2, cx + size, cy), fill=colors[1])
            d.rectangle((cx - size + 1, cy - 4, cx + size - 1, cy - 1), fill=colors[2])
            d.point((cx - 1, cy - 3), fill=colors[3])
            d.point((cx + 2, cy - 1), fill=colors[3])
    else:
        for cx, cy in [(3, 11), (7, 7), (12, 10)]:
            d.line((8, 15, cx, cy), fill='#6c8795', width=2)
            d.rectangle((cx - 2, cy - 2, cx + 2, cy + 1), fill=colors[1])
            d.rectangle((cx - 1, cy - 2, cx + 1, cy), fill=colors[2])
            d.point((cx, cy - 1), fill=colors[3])
    save_texture(name, im)
    model = {'parent': 'minecraft:block/carpet' if shape == 'moss' else 'minecraft:block/cross',
             'render_type': 'minecraft:cutout',
             'textures': {'wool' if shape == 'moss' else 'cross': 'dudunka:block/' + name}}
    asset('models/block', name, model)
    asset('blockstates', name, {'variants': {'': {'model': 'dudunka:block/' + name}}})
    asset('models/item', name, {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'dudunka:block/' + name}})

EN = ['Turquoise Mushroom Cap', 'Violet Memory Mushroom Cap', 'Golden Warm Mushroom Cap', 'Vale Mushroom Stem',
      'Glowing Moss', 'Cyan Magic Flower', 'Pink Magic Flower', 'Violet Memory Flower', 'Spore Bush',
      'Small Turquoise Mushroom', 'Small Violet Mushroom', 'Small Golden Mushroom']
RU = ['Шляпка бирюзового гриба', 'Шляпка фиолетового гриба памяти', 'Шляпка золотистого тёплого гриба',
      'Ножка гриба долины', 'Светящийся мох', 'Бирюзовый волшебный цветок', 'Розовый волшебный цветок',
      'Фиолетовый цветок памяти', 'Споровый кустик', 'Маленький бирюзовый гриб', 'Маленький фиолетовый гриб',
      'Маленький золотистый гриб']
for lang, labels in [('en_us', EN), ('ru_ru', RU)]:
    path = ASSETS / 'lang' / (lang + '.json')
    values = json.loads(path.read_text())
    values.update({'block.dudunka.' + name: label for name, label in zip(CAPS + list(PLANTS), labels)})
    write(path, values)
for name in CAPS + list(PLANTS):
    write(DATA / 'loot_tables/blocks' / (name + '.json'), {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
        'entries': [{'type': 'minecraft:item', 'name': 'dudunka:' + name}]}]})


def provider(name, stem=False):
    state = {'Name': name}
    if name.endswith('_cap') or name.endswith('_stem') or name.endswith('_block'):
        state['Properties'] = {face: 'false' if face == 'down' or (stem and face == 'up') else 'true'
                               for face in ['down', 'east', 'north', 'south', 'up', 'west']}
    return {'type': 'minecraft:simple_state_provider', 'state': state}


def placed(name, modifier):
    feature('placed_feature', name, {'feature': 'dudunka:mushroom_vale/' + name, 'placement': [
        modifier, {'type': 'minecraft:in_square'},
        {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': 0},
        {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR'}, {'type': 'minecraft:biome'}]})


def rarity(chance):
    return {'type': 'minecraft:rarity_filter', 'chance': chance}


def count(n):
    return {'type': 'minecraft:count', 'count': n}


for color, form, radius, modifier in [
    ('red', 'red', 2, rarity(3)), ('brown', 'brown', 3, rarity(4)),
    ('turquoise', 'brown', 3, count(2)), ('violet', 'red', 2, count(1)),
    ('golden', 'brown', 2, rarity(4)),
]:
    name = 'giant_' + color
    cap = 'minecraft:' + color + '_mushroom_block' if color in ('red', 'brown') else 'dudunka:' + color + '_mushroom_cap'
    stem = 'minecraft:mushroom_stem' if color in ('red', 'brown') else 'dudunka:vale_mushroom_stem'
    feature('configured_feature', name, {'type': 'minecraft:huge_' + form + '_mushroom', 'config': {
        'cap_provider': provider(cap), 'stem_provider': provider(stem, True), 'foliage_radius': radius}})
    placed(name, modifier)

for color, chance in [('turquoise', 12), ('violet', 16), ('golden', 24)]:
    name = color + '_landmark'
    feature('configured_feature', name, {'type': 'minecraft:huge_brown_mushroom', 'config': {
        'cap_provider': provider('dudunka:' + color + '_mushroom_cap'),
        'stem_provider': provider('dudunka:vale_mushroom_stem', True), 'foliage_radius': 4}})
    placed(name, rarity(chance))


def patch(name, blocks, tries, attempts):
    to_place = provider('dudunka:' + blocks[0][0]) if len(blocks) == 1 else {
        'type': 'minecraft:weighted_state_provider', 'entries': [
            {'data': {'Name': 'dudunka:' + block}, 'weight': weight} for block, weight in blocks]}
    feature('configured_feature', name, {'type': 'minecraft:random_patch', 'config': {
        'tries': tries, 'xz_spread': 4, 'y_spread': 2,
        'feature': {'feature': {'type': 'minecraft:simple_block', 'config': {'to_place': to_place}},
                    'placement': [{'type': 'minecraft:block_predicate_filter',
                                   'predicate': {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:air'}}]}}})
    placed(name, count(attempts))


patch('glowing_moss', [('glowing_moss', 1)], 32, 2)
patch('spore_bushes', [('spore_bush', 1)], 12, 1)
for mood, color, others, flower, tries in [
    ('glowing', 'turquoise', ['violet', 'golden'], 'cyan_magic_flower', 32),
    ('pink', 'golden', ['turquoise', 'violet'], 'pink_magic_flower', 64),
    ('memory', 'violet', ['turquoise', 'golden'], 'violet_memory_flower', 24),
]:
    patch(mood + '_small_mushrooms', [('small_' + c + '_mushroom', w) for c, w in [(color, 8)] + [(c, 1) for c in others]], 24 if mood != 'pink' else 12, 1)
    patch(mood + '_flowers', [(flower, 1)], tries, 2 if mood == 'pink' else 1)

# Shared entries have a consistent relative order to avoid feature-sort cycles at biome borders.
ORDER = ['turquoise_landmark', 'violet_landmark', 'golden_landmark', 'giant_red', 'giant_brown',
         'giant_turquoise', 'giant_violet', 'giant_golden', 'memory_trees', 'cherry_trees', 'surface_moss',
         'glowing_moss', 'spore_bushes', 'glowing_small_mushrooms', 'memory_small_mushrooms',
         'pink_small_mushrooms', 'glowing_flowers', 'memory_flowers', 'pink_flowers', 'surface_glow_lichen']
BIOMES = {
    'glowing_mushroom_forest': ['turquoise_landmark', 'giant_red', 'giant_brown', 'giant_turquoise',
                               'giant_golden', 'surface_moss', 'glowing_moss', 'spore_bushes',
                               'glowing_small_mushrooms', 'glowing_flowers', 'surface_glow_lichen'],
    'memory_forest': ['violet_landmark', 'giant_red', 'giant_brown', 'giant_violet', 'memory_trees',
                      'glowing_moss', 'spore_bushes', 'memory_small_mushrooms', 'memory_flowers', 'surface_glow_lichen'],
    'pink_meadows': ['golden_landmark', 'giant_golden', 'cherry_trees', 'pink_small_mushrooms', 'pink_flowers'],
}
for biome, names in BIOMES.items():
    path = DATA / 'worldgen/biome' / (biome + '.json')
    value = json.loads(path.read_text())
    value['features'][9] = ['dudunka:mushroom_vale/' + name for name in ORDER if name in names]
    if biome == 'pink_meadows':
        value['features'][9] += ['minecraft:flower_cherry', 'minecraft:flower_meadow', 'minecraft:patch_grass_plain']
    if biome == 'memory_forest':
        value['features'][9] += ['minecraft:patch_grass_taiga']
        value['effects'].update(fog_color=int('afa0c9', 16), sky_color=int('9189ba', 16),
                                grass_color=int('857499', 16), foliage_color=int('79688f', 16))
    if biome == 'glowing_mushroom_forest':
        value['effects'].update(fog_color=int('c2e8e1', 16), foliage_color=int('71b8ae', 16))
    write(path, value)

# The trees are supporting silhouettes; violet mushrooms define the forest.
path = DATA / 'worldgen/placed_feature/mushroom_vale/memory_trees.json'
value = json.loads(path.read_text())
value['placement'][0] = count(1)
write(path, value)
print('Generated 12 decorative blocks, 13 textures, 16 configured/placed feature pairs and biome vegetation.')
