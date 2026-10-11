"""Audit additive Stage 3.4 compatibility and final packaged assets against ae81ad4."""
from pathlib import Path
import argparse, json, subprocess, zipfile

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);a=p.parse_args()
BASE='ae81ad4'
def old(path):return subprocess.check_output(['git','show',f'{BASE}:{path}'],cwd=ROOT)
allowed={
    'src/main/java/io/github/golovinss/dudunka/DudunkaMod.java',
    'src/main/java/io/github/golovinss/dudunka/client/ClientEvents.java',
    *[f'src/main/resources/assets/dudunka/lang/{lang}.json' for lang in ('en_us','ru_ru')],
    *[f'src/main/resources/data/dudunka/worldgen/biome/{biome}.json' for biome in ('memory_forest',)],
}
tracked=subprocess.check_output(['git','ls-tree','-r','--name-only',BASE,'src/main'],cwd=ROOT).decode().splitlines()
for path in tracked:
    assert (ROOT/path).exists(),f'Removed stable path: {path}'
    if path not in allowed: assert (ROOT/path).read_bytes()==old(path),f'Changed existing subsystem/resource: {path}'
for biome,weight in [('memory_forest',10)]:
    path=f'src/main/resources/data/dudunka/worldgen/biome/{biome}.json'
    current=json.loads((ROOT/path).read_text());baseline=json.loads(old(path))
    spiders=[entry for entry in current['spawners']['creature'] if entry['type']=='dudunka:wise_fly_agaric']
    assert spiders==[dict(type='dudunka:wise_fly_agaric',weight=weight,minCount=1,maxCount=1)]
    current['spawners']['creature']=[entry for entry in current['spawners']['creature'] if entry not in spiders]
    assert current==baseline,'Existing biome worldgen, music and inhabitants changed'
for lang in ('en_us','ru_ru'):
    path=f'src/main/resources/assets/dudunka/lang/{lang}.json';current=json.loads((ROOT/path).read_text())
    for key in ('entity.dudunka.wise_fly_agaric',*[f'message.dudunka.wise_fly_agaric.{i}' for i in range(6)]):assert current.pop(key)
    assert current==json.loads(old(path)),'Existing dialogue/locales changed'
# Normalize just the explicit additive registration lines and compare the entire files.
path='src/main/java/io/github/golovinss/dudunka/DudunkaMod.java'
normalized='\n'.join(line for line in (ROOT/path).read_text().split('\n') if 'WISE_FLY_AGARIC' not in line and 'WiseFlyAgaric::canSpawn' not in line)
assert normalized.encode()==old(path),'Existing entity registration was changed'
path='src/main/java/io/github/golovinss/dudunka/client/ClientEvents.java'
normalized=(ROOT/path).read_text().replace('e.registerLayerDefinition(WiseFlyAgaricModel.LAYER,WiseFlyAgaricModel::create);','').replace('e.registerEntityRenderer(DudunkaMod.WISE_FLY_AGARIC.get(),WiseFlyAgaricRenderer::new);','')
assert normalized.encode()==old(path),'Existing renderers were changed'
with zipfile.ZipFile(a.jar) as jar:
    for path in (ROOT/'src/main/resources').rglob('*'):
        if not path.is_file() or path.suffix not in ('.json','.png','.ogg','.nbt'):continue
        name=path.relative_to(ROOT/'src/main/resources').as_posix()
        assert jar.read(name)==path.read_bytes(),f'Packaged resource mismatch: {name}'
    assert not any('dudunka_34' in name or '/WiseSeed' in name or '/WiseEntry' in name or '/WiseSurvey' in name for name in jar.namelist()),'Survey helpers leaked into JAR'
    for name in ('WiseFlyAgaric','client/WiseFlyAgaricModel','client/WiseFlyAgaricRenderer'):
        assert f'io/github/golovinss/dudunka/{name}.class' in jar.namelist(),'Missing shipped entity/model/renderer'
print(f'PASS Stage 3.4: {len(tracked)} existing paths audited; additive registration/locales/biome entries; existing entities, UUID/save code, worldgen, music and protocol preserved; packaged resources match; no survey helpers')
