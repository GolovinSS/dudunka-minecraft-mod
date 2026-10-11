"""Check Stage 3.2d resource/save compatibility against published 65ac290."""
from pathlib import Path
import json, re, subprocess, zipfile, argparse
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path);args=p.parse_args()
BASE='65ac290'
def old(path): return subprocess.check_output(['git','show',f'{BASE}:{path}'],cwd=ROOT)
resources='src/main/resources/'
tracked=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,resources],cwd=ROOT).decode().splitlines()
allowed={resources+'data/dudunka/worldgen/placed_feature/mushroom_vale/'+n+'.json' for n in ('pufik_cottage','wise_fly_agaric_cottage','bench','lantern_post')}
allowed.add(resources+'data/dudunka/worldgen/biome/glowing_mushroom_forest.json')
assert set(tracked)=={str(f.relative_to(ROOT)) for f in (ROOT/resources).rglob('*') if f.is_file()},'Resource IDs/files changed'
for path in tracked:
    if path not in allowed: assert (ROOT/path).read_bytes()==old(path),path
for path in allowed:
    current=json.loads((ROOT/path).read_text());previous=json.loads(old(path))
    if '/placed_feature/' in path:
        chance=current['placement'][0]['chance'];assert chance=={'bench':8,'lantern_post':64,'pufik_cottage':24,'wise_fly_agaric_cottage':32}[Path(path).stem]
        current['placement'][0]['chance']=previous['placement'][0]['chance']
    else:
        for e in current['spawners']['creature']:
            if e['type']=='dudunka:pufik':
                assert e['minCount']==1 and e['maxCount']==2 and e['weight']==24
                e['weight']=5
    assert current==previous,path
for name in ('DudunkaMod.java','Pufik.java','ValeFirefly.java','MushroomValeLandmarks.java','MushroomValeVegetation.java'):
    path='src/main/java/io/github/golovinss/dudunka/'+name
    assert (ROOT/path).read_bytes()==old(path),path
path='src/main/java/io/github/golovinss/dudunka/MushroomValeDecorFeature.java'
current=(ROOT/path).read_text();previous=old(path).decode()
assert current[current.index('    public record Piece'):current.index('    public MushroomValeDecorFeature()')]==previous[previous.index('    public record Piece'):previous.index('    public MushroomValeDecorFeature()')], 'Blueprint codecs changed'
if args.jar:
    with zipfile.ZipFile(args.jar) as z:
        assert not any('PopulateVale' in n or n.startswith('data/dudunka_32d/') for n in z.namelist()),'Survey helpers leaked into JAR'
        assert not any(b'PopulateVale' in z.read(n) for n in z.namelist() if n.endswith('.class')),'Instrumented class references leaked into JAR'
        declared_sources=set()
        for source in (ROOT/'src/main/java').rglob('*.java'):
            package=re.search(r'^package ([^;]+);',source.read_text(),re.MULTILINE)
            if package: declared_sources.add(package[1].replace('.','/')+'/'+source.stem)
        for name in z.namelist():
            if name.endswith('.class'):
                outer=name.split('$',1)[0].removesuffix('.class')
                assert outer in declared_sources,f'Non-production class leaked: {name}'
        properties=dict(line.strip().split('=',1) for line in (ROOT/'gradle.properties').read_text().splitlines()
                        if line.strip() and not line.lstrip().startswith('#') and '=' in line)
        for path in tracked:
            expected=(ROOT/path).read_bytes()
            if path.endswith('META-INF/mods.toml'):
                # ProcessResources expands this template using gradle.properties.
                expected=re.sub(r'\$\{(\w+)\}',lambda m:properties[m[1]],expected.decode()).encode()
            assert z.read(path.removeprefix(resources))==expected,path
print(f'PASS: {len(tracked)} stable resource paths; all approved assets/blueprints, terrain, fireflies, entity registrations and blueprint codecs preserved; bounded spawn/rarity changes only.')
