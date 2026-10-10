"""Offline resource preflight; Forge startup remains the authoritative codec check."""
from pathlib import Path
import argparse
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--vanilla-jar', type=Path, default=Path.home() / '.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar')
parser.add_argument('--mod-jar', type=Path, help='Also verify every source resource against the built JAR')
args = parser.parse_args()
vanilla = zipfile.ZipFile(args.vanilla_jar)
entries = set(vanilla.namelist())
resources = list(RES.rglob('*.json'))
for path in resources:
    json.loads(path.read_text())


def resource(prefix, identifier, suffix='.json'):
    namespace, name = identifier.split(':', 1) if ':' in identifier else ('minecraft', identifier)
    category, directory = prefix.split('/', 1)
    path = f'{category}/{namespace}/{directory}/{name}{suffix}'
    local = RES / path
    if local.exists():
        return local.read_bytes()
    assert path in entries, f'Missing resource: {path}'
    return vanilla.read(path)


source = (ROOT / 'src/main/java/io/github/golovinss/dudunka/MushroomValeVegetation.java').read_text()
blocks = set(re.findall(r'(?:cap|plant)\("([a-z_]+)"', source))
assert len(blocks) == 12
polish_source = (ROOT / 'src/main/java/io/github/golovinss/dudunka/MushroomValeDecor.java').read_text()
polish = set(re.findall(r'decor\("([a-z_]+)"', polish_source))
assert len(polish) == 12
blocks |= polish
landmark_source = (ROOT / 'src/main/java/io/github/golovinss/dudunka/MushroomValeLandmarks.java').read_text()
landmark_blocks = set(re.findall(r'part\("([a-z_]+)"', landmark_source))
assert len(landmark_blocks) == 4
blocks |= landmark_blocks


def block(identifier):
    if identifier.startswith('dudunka:'):
        assert identifier.split(':')[1] in blocks, f'Unregistered Vale block: {identifier}'
    else:
        resource('assets/blockstates', identifier)


visited = set()


def configured(value):
    if isinstance(value, str):
        key = ('configured_feature', value)
        if key in visited:
            return
        visited.add(key)
        value = json.loads(resource('data/worldgen/configured_feature', value))
    walk(value)


def placed(value):
    if isinstance(value, str):
        key = ('placed_feature', value)
        if key in visited:
            return
        visited.add(key)
        value = json.loads(resource('data/worldgen/placed_feature', value))
    configured(value['feature'])
    walk(value.get('placement', []))


def walk(value):
    if isinstance(value, list):
        for item in value:
            walk(item)
    elif isinstance(value, dict):
        if 'placement' in value and 'feature' in value:
            placed(value)
            return
        for key, item in value.items():
            if key == 'Name':
                block(item)
            elif key == 'feature':
                placed(item)
            elif key == 'features':
                for child in item:
                    placed(child) if isinstance(child, str) else walk(child)
            else:
                walk(item)


biomes = list((RES / 'data/dudunka/worldgen/biome').glob('*.json'))
edges = {}
for path in biomes:
    biome = json.loads(path.read_text())
    for step in biome['features']:
        for identifier in step:
            placed(identifier)
        # Check ordering constraints shared across biomes (vanilla FeatureSorter).
        for a, b in zip(step, step[1:]):
            edges.setdefault(a, set()).add(b)


def acyclic(node, active, done):
    assert node not in active, f'Feature order cycle at {node}'
    if node in done:
        return
    active.add(node)
    for child in edges.get(node, []):
        acyclic(child, active, done)
    active.remove(node)
    done.add(node)


done = set()
for node in edges:
    acyclic(node, set(), done)

# Stage 2.4 blueprint bounds, duplicate coordinates and registered feature wiring.
decor = list((RES / 'data/dudunka/worldgen/configured_feature/mushroom_vale').glob('*.json'))
decor = [p for p in decor if json.loads(p.read_text())['type'] == 'dudunka:mushroom_vale_decor']
feature_source = (ROOT / 'src/main/java/io/github/golovinss/dudunka/DudunkaMod.java').read_text()
assert 'FEATURES.register("mushroom_vale_decor",MushroomValeDecorFeature::new)' in feature_source
assert len(decor) == 8
for path in decor:
    config = json.loads(path.read_text())['config']
    assert 1 <= config['radius'] <= 6 and 1 <= config['height'] <= 10
    assert 1 <= len(config['blocks']) <= 1024
    positions = set()
    for piece in config['blocks']:
        x, y, z = piece['offset']
        assert abs(x) <= config['radius'] and abs(z) <= config['radius'] and 0 <= y < config['height'], path
        assert (x, y, z) not in positions, path
        positions.add((x, y, z))
        block(piece['state']['Name'])
    placement = json.loads(resource('data/worldgen/placed_feature', 'dudunka:mushroom_vale/' + path.stem))
    assert placement['placement'][0]['chance'] >= (8 if path.stem == 'bench' else (28 if path.stem == 'lantern_post' else 32))
    assert placement['placement'][-1]['type'] == 'minecraft:biome'
for path in biomes:
    refs = sum(json.loads(path.read_text())['features'], [])
    assert ('dudunka:mushroom_vale/pufik_cottage' in refs) == (path.stem == 'pink_meadows')
    assert ('dudunka:mushroom_vale/wise_fly_agaric_cottage' in refs) == (path.stem == 'memory_forest')

# Stage 2.5a contract: bounded mushroom attempts, recognizable layered geometry,
# distinct bench/flower-bed content and streamed, registered native biome music.
chances = {'giant_red':6, 'giant_brown':8, 'giant_turquoise':4, 'giant_violet':4,
           'giant_golden':6, 'turquoise_landmark':48, 'violet_landmark':56, 'golden_landmark':96}
for name, chance in chances.items():
    value = json.loads(resource('data/worldgen/placed_feature', 'dudunka:mushroom_vale/' + name))
    assert value['placement'][0] == {'type':'minecraft:rarity_filter', 'chance':chance}
for path in biomes:
    value = json.loads(path.read_text())
    refs = sum(value['features'], [])
    attempts = sum(1/chances[name.rsplit('/',1)[-1]] for name in refs if name.rsplit('/',1)[-1] in chances)
    assert 0 < attempts < .8, (path, attempts)
    assert value['effects']['music'] == {'sound':'dudunka:music.mushroom_vale_theme',
            'min_delay':1200, 'max_delay':3600, 'replace_current_music':True}
    assert 'dudunka:mushroom_vale/bench' in refs and 'dudunka:mushroom_vale/flower_bed' in refs
for color in ['turquoise', 'violet', 'golden']:
    value = json.loads(resource('data/worldgen/configured_feature', 'dudunka:mushroom_vale/' + color + '_landmark'))
    assert value['type'] == 'dudunka:mushroom_vale_landmark'
    pieces = value['config']['blocks']
    positions = [tuple(piece['offset']) for piece in pieces]
    assert len(positions) == len(set(positions)) and len(pieces) <= 1024
    caps = [piece for piece in pieces if piece['state']['Name'].endswith('_cap')]
    assert {piece['offset'][1] for piece in caps} == ({4,5,6,7,8,9} if color == 'violet' else {4,5,6,7,8})
    assert {piece['state']['Properties']['profile'] for piece in caps} == {'0','1','2','3','4'}
    assert max(abs(piece['offset'][0]) for piece in caps) == 4
    assert all(abs(x)<=4 and abs(z)<=4 and 0<=y<value['config']['height'] for x,y,z in positions)
for color in ['turquoise', 'violet', 'golden']:
    value = json.loads(resource('data/worldgen/configured_feature', 'dudunka:mushroom_vale/giant_' + color))
    assert value['type'] == 'dudunka:mushroom_vale_landmark'
    assert value['config']['radius'] == 3 and value['config']['height'] == 7
    assert any(p['state']['Properties']['profile'] == '4' for p in value['config']['blocks'])
for mood in ['glowing','memory','pink']:
    value = json.loads(resource('data/worldgen/configured_feature', 'dudunka:mushroom_vale/' + mood + '_small_mushrooms'))
    assert value['config']['tries'] == (30 if mood == 'pink' else 40)
assert json.loads(resource('data/worldgen/placed_feature', 'dudunka:mushroom_vale/bench'))['placement'][0]['chance'] == 8
bench = json.loads(resource('data/worldgen/configured_feature', 'dudunka:mushroom_vale/bench'))['config']['blocks']
bed = json.loads(resource('data/worldgen/configured_feature', 'dudunka:mushroom_vale/flower_bed'))['config']['blocks']
assert len(bench) == 3 and all(p['state']['Name']=='dudunka:vale_bench' for p in bench)
assert not any(p['state']['Name']=='dudunka:vale_bench' for p in bed)
sounds = json.loads((RES / 'assets/dudunka/sounds.json').read_text())
assert sounds['music.mushroom_vale_theme'] == {'sounds':[{'name':'dudunka:music/mushroom_vale_theme','stream':True}]}
assert (RES / 'assets/dudunka/sounds/music/mushroom_vale_theme.ogg').read_bytes().startswith(b'OggS')
assert 'SOUNDS.register("music.mushroom_vale_theme"' in feature_source and 'SOUNDS.register(bus)' in feature_source
assert 'FEATURES.register("mushroom_vale_landmark",MushroomValeLandmarkFeature::new)' in feature_source
if args.mod_jar:
    with zipfile.ZipFile(args.mod_jar) as jar:
        assert jar.testzip() is None
        assert not any('Vale25Focused' in name or 'Vale25GreetingReplay' in name or 'dudunka_25/' in name or 'dudunka_25_regression/' in name or 'Vale25aFocused' in name or 'ValeDistributionSurvey' in name or 'dudunka_survey/' in name or 'dudunka_25a/' in name for name in jar.namelist()), 'Focused harness leaked into production JAR'
        count = 0
        for path in RES.rglob('*'):
            if path.is_file() and path.suffix in ('.json', '.png', '.ogg'):
                assert jar.read(str(path.relative_to(RES))) == path.read_bytes(), path
                count += 1
        print(f'PASS: {count} JSON/PNG/OGG resources byte-match packaged JAR')
print('PASS: bounded mushroom frequencies, layered landmarks, distinct benches, streamed music and biome scheduling')

models = set()


def model(identifier):
    if identifier in models:
        return
    models.add(identifier)
    value = json.loads(resource('assets/models', identifier))
    if 'parent' in value and not value['parent'].startswith(('builtin/', 'minecraft:builtin/')):
        model(value['parent'])
    for element in (value.get('elements', []) if identifier.startswith('dudunka:block/') else []):
        assert all(0 <= lo < hi <= 16 for lo, hi in zip(element['from'], element['to'])), identifier
        for face in element['faces'].values():
            texture = face['texture']
            assert not texture.startswith('#') or texture[1:] in value.get('textures', {}), identifier
            if 'uv' in face:
                assert all(0 <= v <= 16 for v in face['uv']), identifier
    for texture in value.get('textures', {}).values():
        if not texture.startswith('#'):
            resource('assets/textures', texture, '.png')


def find_models(value):
    if isinstance(value, dict):
        for key, child in value.items():
            model(child) if key == 'model' else find_models(child)
    elif isinstance(value, list):
        for child in value:
            find_models(child)


for name in blocks:
    find_models(json.loads(resource('assets/blockstates', 'dudunka:' + name)))
    model('dudunka:item/' + name)
    loot = json.loads(resource('data/loot_tables/blocks', 'dudunka:' + name))
    assert loot['pools'][0]['entries'][0]['name'] == 'dudunka:' + name
langs = [json.loads((RES / 'assets/dudunka/lang' / (lang + '.json')).read_text()) for lang in ('en_us', 'ru_ru')]
assert langs[0].keys() == langs[1].keys(), 'Localization keys differ'
for name in blocks:
    assert all('block.dudunka.' + name in lang for lang in langs)
print(f'PASS: {len(resources)} JSON resources; {len(biomes)} biomes; {len(visited)} referenced worldgen resources; '
      f'{len(decor)} bounded decor blueprints; {len(blocks)} block/item/loot/localization sets; {len(models)} model references; acyclic feature ordering.')
