"""Repeat the shared-home gathering friendship GameTest in a fresh world.

python3 tools/run_home_together_checks.py isolated --repeat 10
python3 tools/run_home_together_checks.py shared --repeat 5
"""
from pathlib import Path
import argparse
import datetime
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('mode', choices=['isolated', 'shared'])
parser.add_argument('--repeat', type=int, default=5)
args = parser.parse_args()
if not 1 <= args.repeat <= 20:
    parser.error('--repeat must be between 1 and 20')
name = args.mode + '-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
report = ROOT / 'build/reports/home-together' / name
report.mkdir(parents=True, exist_ok=False)
world = ROOT / 'run' / ('home-together-' + name)
world.mkdir(parents=True, exist_ok=False)
(world / 'server.properties').write_text('level-seed=0\n')
namespace = 'dudunka_home_together_check'
src = report / 'java'
java = src / 'io/github/golovinss/dudunka/test/HomeTogetherFocused.java'
java.parent.mkdir(parents=True)
source = ('package io.github.golovinss.dudunka.test;\n'
          'import net.minecraft.gametest.framework.*;\nimport net.minecraftforge.gametest.*;\n'
          f'@GameTestHolder("{namespace}") @PrefixGameTestTemplate(false)\npublic class HomeTogetherFocused {{\n')
for index in range(args.repeat):
    batch = f'isolated_{index}' if args.mode == 'isolated' else 'shared_home_together'
    source += (f'@GameTest(template="empty", timeoutTicks=100, batch="{batch}")\n'
               f'public static void check{index}(GameTestHelper h) {{ FamilyTests.togetherFriendshipRequiresActualSimultaneousSitting(h); }}\n')
if args.mode == 'shared':
    for index, (method, timeout) in enumerate([
        ('togetherReservesSeparateCoveredPlacesForOwnFamily', 40),
        ('togetherHonorsCommandsOwnerDepartureAndDestroyedHome', 40),
        ('togetherYieldsToSnailThreatAndUnsafeSeat', 40),
        ('togetherReallyWalksAndSitsWithLiveAI', 150),
    ]):
        source += (f'@GameTest(template="empty", timeoutTicks={timeout}, batch="shared_home_together")\n'
                   f'public static void context{index}(GameTestHelper h) {{ FamilyTests.{method}(h); }}\n')
java.write_text(source + '}\n')
res = report / 'resources'
template = res / 'data' / namespace / 'structures/empty.nbt'
template.parent.mkdir(parents=True)
shutil.copyfile(ROOT / 'src/main/resources/data/dudunka/structures/empty.nbt', template)
init = report / 'fresh-world.gradle'
init.write_text(f'''allprojects {{ afterEvaluate {{
 sourceSets.main.java.srcDir('{src.relative_to(ROOT).as_posix()}')
 sourceSets.main.resources.srcDir('{res.relative_to(ROOT).as_posix()}')
 minecraft.runs.gameTestServer.property 'forge.enabledGameTestNamespaces', '{namespace}'
}} }}
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
print(f'{args.mode}: {log.relative_to(ROOT)}', flush=True)
try:
    with log.open('w') as stream:
        result = subprocess.run(['./gradlew', '--offline', '-Dnet.minecraftforge.gradle.check.certs=false',
                                 '-I', str(init), 'runGameTestServer', '-x', 'downloadAssets'],
                                cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
finally:
    shutil.rmtree(ROOT / 'build/resources/main/data' / namespace, ignore_errors=True)
    for compiled in (ROOT / 'build/classes/java/main/io/github/golovinss/dudunka/test').glob('HomeTogetherFocused*.class'):
        compiled.unlink()
print('\n'.join(log.read_text().splitlines()[-25:]), flush=True)
raise SystemExit(result.returncode)
