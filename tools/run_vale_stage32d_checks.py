"""Stage 3.2d: isolated matched-world census and completed natural-spawn telemetry.
Baseline overlays come from pinned published 65ac290; never edits production source or existing worlds.
"""
from pathlib import Path
import argparse, datetime, os, shutil, subprocess, fcntl, sys, hashlib, json
ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('mode', choices=['survey', 'focused', 'full', 'build'])
p.add_argument('--seed', type=int, default=0)
p.add_argument('--baseline', action='store_true')
p.add_argument('--seeds', nargs='+', type=int)
a = p.parse_args()
if a.baseline and a.mode != 'survey': p.error('--baseline is only valid for survey')
if a.mode in ('full','build') and (a.seed != 0 or a.seeds): p.error('--seed/--seeds overrides are supported only by focused/survey')
if a.seeds:
    for seed in a.seeds:
        subprocess.check_call([sys.executable, __file__, a.mode, '--seed', str(seed), *(['--baseline'] if a.baseline else [])])
    raise SystemExit(0)
lock_path = ROOT / 'build/reports/mushroom-vale-stage32d/.runner.lock'
lock_path.parent.mkdir(parents=True,exist_ok=True)
lock = lock_path.open('w')
fcntl.flock(lock,fcntl.LOCK_EX)
seed_label = str(a.seed) if a.mode in ('survey','focused') else 'default'
name = f'{a.mode}-{"baseline" if a.baseline else "final"}-{seed_label}-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
report = ROOT / 'build/reports/mushroom-vale-stage32d' / name
report.mkdir(parents=True)
world = ROOT / 'run' / ('vale-stage32d-' + name)
world.mkdir(parents=True)
src = report / 'java'
pkg = src / 'io/github/golovinss/dudunka/test'
pkg.mkdir(parents=True)
namespace = 'dudunka_32d'
extra = ''
overlays = []

def original(path):
    return subprocess.check_output(['git', 'show', '65ac290:' + path], cwd=ROOT).decode() if a.baseline else (ROOT/path).read_text()

if a.mode in ('survey', 'focused'):
    shutil.copyfile(ROOT / 'tools/validation32d/PopulateValeSurvey.java', pkg / 'PopulateValeSurvey.java')
    (pkg/'PopulateValeSeed.java').write_text(f'''package io.github.golovinss.dudunka.test;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="dudunka")
public class PopulateValeSeed {{
 @net.minecraftforge.eventbus.api.SubscribeEvent public static void seed(net.minecraftforge.event.server.ServerAboutToStartEvent e) throws Exception {{
 var f=net.minecraft.world.level.storage.PrimaryLevelData.class.getDeclaredField("worldOptions"); f.setAccessible(true);
 f.set(e.getServer().getWorldData(),new net.minecraft.world.level.levelgen.WorldOptions({a.seed}L,false,false));
 }}
}}
''')
    if a.mode == 'survey':
        (pkg/'PopulateValeEntry.java').write_text('''package io.github.golovinss.dudunka.test;
import net.minecraft.gametest.framework.*; import net.minecraftforge.gametest.*;
@GameTestHolder("dudunka_32d") @PrefixGameTestTemplate(false)
public class PopulateValeEntry {
 @GameTest(template="empty",timeoutTicks=8000,batch="survey") public static void survey(GameTestHelper h) { PopulateValeSurvey.start(h); }
 @AfterBatch(batch="survey") public static void cleanup(net.minecraft.server.level.ServerLevel l) { PopulateValeSurvey.cleanup(); }
}
''')
        path = 'src/main/java/io/github/golovinss/dudunka/MushroomValeDecorFeature.java'
        code = original(path)
        code = code.replace('@Override public boolean place(FeaturePlaceContext<Blueprint> context) {', '@Override public boolean place(FeaturePlaceContext<Blueprint> context) {\n        boolean placed = placeMeasured(context);\n        io.github.golovinss.dudunka.test.PopulateValeSurvey.placement(context, placed);\n        return placed;\n    }\n    private boolean placeMeasured(FeaturePlaceContext<Blueprint> context) {')
        # Label actual preflight rejection paths; probes exist only in this isolated compilation.
        start = code.index('    public static boolean placeBlueprint')
        head, body = code[:start], code[start:]
        for index, line in enumerate(body.splitlines()):
            if 'return false;' in line:
                body = body.replace(line, line.replace('return false;', f'return io.github.golovinss.dudunka.test.PopulateValeSurvey.reject(config, origin, "check{index}");'), 1)
        body = body.replace('        return true;', '        io.github.golovinss.dudunka.test.PopulateValeSurvey.siteSuccess(config, origin, level);\n        return true;', 1)
        code = head + body
        dest = src / 'io/github/golovinss/dudunka/MushroomValeDecorFeature.java'
        dest.write_text(code)
        overlays.append(path)
        path = 'src/main/java/io/github/golovinss/dudunka/Pufik.java'
        code = original(path).replace('return inHabitat(level, pos) && safeGround(level, pos);', '''boolean habitat = inHabitat(level, pos), safe = habitat && safeGround(level, pos);
        io.github.golovinss.dudunka.test.PopulateValeSurvey.spawn(level, reason, pos, habitat, safe);
        return safe;''')
        (src/'io/github/golovinss/dudunka/Pufik.java').write_text(code)
        overlays.append(path)
    else:
        methods = [('MushroomValeTests','valeDecorBlueprintsRotateAndRejectUnsafeSites',200),
                   ('MushroomValeTests','valeCottagePreviewRequiresOptInAndPlacesSelectedOrientation',400),
                   ('MushroomValeTests','valeLanternPropagatesBlockLightAndRemovalClearsIt',200),
                   ('MushroomValeTests','valeBenchesSeatOnePlayerAndCleanUpEveryLifecycle',120),
                   ('PufikTests','registrationAttributesAndIdentity',40),
                   ('PufikTests','spawnRulesRequireValeForestAndSafeGround',180),
                   ('PufikTests','peacefulInteractionAndSaveLoad',40),
                   ('ValePopulationSafetyTests','fittedFoundationsPreserveSlopesAndRejectUnsafeSites',200),
                   ('ValePopulationSafetyTests','pufiksShareUnchangedCreatureCapWithMooshrooms',100),
                   ('ValePopulationSafetyTests','ordinaryPufikTurnoverPreservesNamesPersistenceAndForgeVeto',100)]
        entry = 'package io.github.golovinss.dudunka.test;\nimport net.minecraft.gametest.framework.*; import net.minecraftforge.gametest.*;\n@GameTestHolder("dudunka_32d") @PrefixGameTestTemplate(false) public class PopulateValeEntry {\n'
        for i,(cls,method,ticks) in enumerate(methods):
            entry += f'@GameTest(template="empty",timeoutTicks={ticks},batch="isolated_{i}") public static void check{i}(GameTestHelper h) throws Exception {{ {cls}.{method}(h); }}\n'
        (pkg/'PopulateValeEntry.java').write_text(entry+'}\n')
    res = report/'resources'
    target = res / f'data/{namespace}/structures/empty.nbt'
    target.parent.mkdir(parents=True)
    shutil.copyfile(ROOT/'src/main/resources/data/dudunka/structures/empty.nbt', target)
    excluded = ','.join("'"+str(ROOT/x)+"'" for x in overlays)
    extra = f'''allprojects {{ afterEvaluate {{
 sourceSets.main.resources.srcDir('{res}')
 tasks.named('compileJava') {{ setSource(source.filter {{ ![{excluded}].contains(it.absolutePath) }} + fileTree('{src}') {{ include '**/*.java' }}) }}
 tasks.named('processResources') {{ outputs.upToDateWhen {{ false }} }}
 minecraft.runs.gameTestServer.property 'forge.enabledGameTestNamespaces', '{namespace}'
}} }}
'''
    if a.baseline:
        base = report/'baseline-res'
        paths = [f'data/dudunka/worldgen/placed_feature/mushroom_vale/{n}.json' for n in ('bench','lantern_post','pufik_cottage','wise_fly_agaric_cottage')]
        paths += ['data/dudunka/worldgen/biome/glowing_mushroom_forest.json']
        for path in paths:
            dest = base/path; dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(original('src/main/resources/'+path))
        extra += f"allprojects {{ afterEvaluate {{ tasks.named('processResources') {{ doLast {{ copy {{ from '{base}'; into destinationDir }} }} }} }} }}\n"
init = report/'fresh-world.gradle'
init.write_text(extra + f'''
gradle.taskGraph.whenReady {{ graph ->
 def task = graph.allTasks.find {{ it.name == 'runGameTestServer' }}
 if(task != null) task.doFirst {{
  def dir = task.project.file('{world}')
  assert !new File(dir, 'world/level.dat').exists() : 'Requires unused world'
  task.runConfig.get().workingDirectory(dir)
 }}
}}
''')
env = os.environ.copy(); env['JAVA_HOME']='/usr/lib/jvm/java-17-openjdk-amd64'; env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
tasks = ['build','verifyModels','verifyPufik'] if a.mode == 'build' else ['runGameTestServer']
(report/'inputs.json').write_text(json.dumps({
    'seed':a.seed if a.mode in ('survey','focused') else None, 'baseline':'65ac290' if a.baseline else None,
    'production_sha256':{path:hashlib.sha256(original(path).encode()).hexdigest() for path in [
        'src/main/java/io/github/golovinss/dudunka/MushroomValeDecorFeature.java',
        'src/main/java/io/github/golovinss/dudunka/Pufik.java',
        'src/main/resources/data/dudunka/worldgen/biome/glowing_mushroom_forest.json',
        *['src/main/resources/data/dudunka/worldgen/placed_feature/mushroom_vale/'+n+'.json' for n in ('pufik_cottage','wise_fly_agaric_cottage','bench','lantern_post')]]}
},indent=2)+'\n')
log = report/'gametest.log' 
print(log, flush=True)
try:
    with log.open('w') as out:
        result = subprocess.run(['./gradlew','--offline','-Dnet.minecraftforge.gradle.check.certs=false','-I',str(init),*tasks,'-x','downloadAssets'],cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT)
finally:
    if a.mode in ('survey','focused'):
        shutil.rmtree(ROOT/f'build/resources/main/data/{namespace}',ignore_errors=True)
        for f in (ROOT/'build/classes/java/main/io/github/golovinss/dudunka/test').glob('PopulateVale*.class'): f.unlink()
        # Instrumented classes must never be reused by a normal build.
        for path in overlays:
            cls=ROOT/'build/classes/java/main'/Path(path).relative_to('src/main/java').with_suffix('.class')
            for f in cls.parent.glob(cls.stem+'*.class'): f.unlink()
        if a.baseline:
            for path in paths: shutil.copyfile(ROOT/'src/main/resources'/path,ROOT/'build/resources/main'/path)
print('\n'.join(log.read_text().splitlines()[-30:]),flush=True)
raise SystemExit(result.returncode)
