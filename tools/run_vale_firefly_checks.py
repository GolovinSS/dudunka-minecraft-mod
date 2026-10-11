"""Run Stage 3.2 GameTests offline in a fresh world; helpers stay outside the JAR.

python3 tools/run_vale_firefly_checks.py focused
python3 tools/run_vale_firefly_checks.py population --seed 42
python3 tools/run_vale_firefly_checks.py full
"""
from pathlib import Path
import datetime
import os
import shutil
import subprocess
import argparse

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('mode', choices=['focused', 'population', 'full'])
parser.add_argument('--seed', type=int, default=0)
args = parser.parse_args()
mode = args.mode
if mode == 'full' and args.seed != 0:
    parser.error('--seed is supported by focused/population runs; the full suite uses its seed-0 fixtures')
name = mode + '-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
report = ROOT / 'build/reports/mushroom-vale-stage32' / name
report.mkdir(parents=True, exist_ok=False)
world = ROOT / 'run' / ('vale-stage32-' + name)
world.mkdir(parents=True, exist_ok=False)
(world / 'server.properties').write_text(f'level-seed={args.seed}\n')
extra = ''
if mode in ('focused', 'population'):
    namespace = 'dudunka_firefly_check'
    src = report / 'java'
    java = src / 'io/github/golovinss/dudunka/test/FireflyFocused.java'
    java.parent.mkdir(parents=True)
    methods = [('registrationVariantsSaveAndCompatibility', 100), ('mixedGroupPalettesAcrossSeeds', 100),
               ('naturalSpawnsRequireValeBiomeNightAndDrySurface', 100),
               ('naturalSpawnOpportunitiesAcrossSeeds', 100), ('boundedSafeFlightAndHomeReload', 440),
               ('groundTakeoffAndSustainedHover', 640), ('localDespawnHonorsNamesPersistenceForgeAndSharedCap', 100)]
    test_class = 'ValeFireflyTests'
    if mode == 'population':
        test_class = 'ValeFireflyPopulationTests'
        methods = [('glowingForestPopulation', 1500), ('pinkMeadowsPopulation', 1500), ('memoryForestPopulation', 1500)]
    text = ('package io.github.golovinss.dudunka.test;\n'
            'import net.minecraft.gametest.framework.*;\nimport net.minecraftforge.gametest.*;\n'
            f'@GameTestHolder("{namespace}") @PrefixGameTestTemplate(false)\npublic class FireflyFocused {{\n')
    for index, (method, timeout) in enumerate(methods):
        text += (f'@GameTest(template="empty", timeoutTicks={timeout}, batch="isolated_{index}")\n'
                 f'public static void check{index}(GameTestHelper h) {{ {test_class}.{method}(h); }}\n')
        if mode == 'population':
            text += (f'@AfterBatch(batch="isolated_{index}") public static void cleanup{index}(net.minecraft.server.level.ServerLevel l) '
                     '{ ValeFireflyPopulationTests.cleanup(); }\n')
        else:
            text += (f'@AfterBatch(batch="isolated_{index}") public static void cleanup{index}(net.minecraft.server.level.ServerLevel l) '
                     '{ ValeFireflyTests.cleanupFlightFixture(); }\n')
    java.write_text(text + '}\n')
    # GameTestServer hardcodes seed 0 and ignores server.properties. This test-only
    # startup hook changes WorldOptions BEFORE loadLevel creates any terrain.
    if args.seed != 0:
        (java.parent / 'FireflySeed.java').write_text(f'''package io.github.golovinss.dudunka.test;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="dudunka")
public class FireflySeed {{
 @net.minecraftforge.eventbus.api.SubscribeEvent
 public static void beforeWorld(net.minecraftforge.event.server.ServerAboutToStartEvent e) throws Exception {{
  var f=net.minecraft.world.level.storage.PrimaryLevelData.class.getDeclaredField("worldOptions");
  f.setAccessible(true);
  f.set(e.getServer().getWorldData(),new net.minecraft.world.level.levelgen.WorldOptions({args.seed}L,false,false));
 }}
}}
''')
    res = report / 'resources'
    template = res / 'data' / namespace / 'structures/empty.nbt'
    template.parent.mkdir(parents=True)
    shutil.copyfile(ROOT / 'src/main/resources/data/dudunka/structures/empty.nbt', template)
    extra = f'''allprojects {{ afterEvaluate {{
 sourceSets.main.java.srcDir('{src.relative_to(ROOT).as_posix()}')
 sourceSets.main.resources.srcDir('{res.relative_to(ROOT).as_posix()}')
 minecraft.runs.gameTestServer.property 'forge.enabledGameTestNamespaces', '{namespace}'
}} }}
'''
init = report / 'fresh-world.gradle'
init.write_text(extra + f'''
gradle.taskGraph.whenReady {{ graph ->
 def task = graph.allTasks.find {{ it.name == 'runGameTestServer' }}
 task.doFirst {{
  def dir = task.project.file('{world.relative_to(ROOT).as_posix()}')
  assert !new File(dir, 'world/level.dat').exists() : 'Requires unused test directory'
  task.runConfig.get().workingDirectory(dir)
 }}
}}
''')
env = os.environ.copy()
env['JAVA_HOME'] = '/usr/lib/jvm/java-17-openjdk-amd64'
log = report / 'gametest.log'
print(f'{mode}: {log.relative_to(ROOT)}', flush=True)
try:
    with log.open('w') as stream:
        result = subprocess.run(['./gradlew', '--offline', '-Dnet.minecraftforge.gradle.check.certs=false', '-I', str(init), 'runGameTestServer', '-x', 'downloadAssets'],
                                cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
finally:
    if mode in ('focused', 'population'):
        # ProcessResources is a Copy task; remove its isolated template explicitly.
        shutil.rmtree(ROOT / 'build/resources/main/data' / namespace, ignore_errors=True)
        compiled_dir = ROOT / 'build/classes/java/main/io/github/golovinss/dudunka/test'
        for pattern in ('FireflyFocused*.class', 'FireflySeed*.class'):
            for compiled in compiled_dir.glob(pattern):
                compiled.unlink()
print('\n'.join(log.read_text().splitlines()[-25:]), flush=True)
raise SystemExit(result.returncode)
