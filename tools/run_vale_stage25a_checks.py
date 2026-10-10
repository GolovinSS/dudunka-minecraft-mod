"""Offline Java 17 validation in fresh worlds; never reuses or deletes a world.

Examples:
  python3 tools/run_vale_stage25a_checks.py focused
  python3 tools/run_vale_stage25a_checks.py survey --seeds 0 42 -71021
  python3 tools/run_vale_stage25a_checks.py full
  python3 tools/run_vale_stage25a_checks.py build
Survey/helper Java is excluded from normal builds and the production JAR.
"""
from pathlib import Path
import argparse
import datetime
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'build/reports/mushroom-vale-stage25a'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('mode', choices=['focused', 'survey', 'full', 'build'])
parser.add_argument('--seeds', nargs='+', type=int, default=[0,42,-71021])
parser.add_argument('--trace', action='store_true')
parser.add_argument('--label', default=datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
args = parser.parse_args()
REPORT.mkdir(parents=True, exist_ok=True)
env = os.environ.copy()
env['JAVA_HOME'] = '/usr/lib/jvm/java-17-openjdk-amd64'
env['PATH'] = env['JAVA_HOME'] + '/bin:' + env['PATH']


def run(name, init=None, tasks=None):
    command = ['./gradlew', '--offline', '-Dnet.minecraftforge.gradle.check.certs=false']
    if init:
        command += ['-I', str(init.relative_to(ROOT))]
    command += tasks or ['runGameTestServer']
    command += ['-x', 'downloadAssets']
    log = REPORT / (name + '.log')
    print('Running', name, '->', log.relative_to(ROOT), flush=True)
    with log.open('w') as output:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
    print(name, 'exit', result.returncode, flush=True)
    if result.returncode:
        print('\n'.join(log.read_text().splitlines()[-45:]), flush=True)
        raise SystemExit(result.returncode)


def init_script(name, seed, extra=''):
    # Paths/seed are controlled test inputs, not shell commands.
    directory = ROOT / 'run' / ('vale-stage25a-' + name)
    if (directory / 'world/level.dat').exists():
        raise SystemExit('Refusing to reuse world: ' + str(directory))
    directory.mkdir(parents=True, exist_ok=True)
    (directory / 'server.properties').write_text(f'level-seed={seed}\n')
    init = REPORT / (name + '.gradle')
    init.write_text(extra + f'''
gradle.taskGraph.whenReady {{ graph ->
 def task = graph.allTasks.find {{ it.name == 'runGameTestServer' }}
 task.doFirst {{
  def dir = task.project.file('run/vale-stage25a-{name}')
  assert !new File(dir, 'world/level.dat').exists() : 'Requires unused test directory'
  task.runConfig.get().workingDirectory(dir)
 }}
}}
''')
    return init


def resources(namespace):
    res = REPORT / (namespace + '-res')
    target = res / ('data/' + namespace + '/structures/empty.nbt')
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / 'src/main/resources/data/dudunka/structures/empty.nbt', target)
    return res.relative_to(ROOT).as_posix()


if args.mode == 'build':
    run('build-' + args.label, tasks=['build', 'verifyModels'])
elif args.mode == 'full':
    name = 'full-' + args.label
    run(name, init_script(name, args.seeds[0]))
elif args.mode == 'survey':
    res = resources('dudunka_survey')
    extra = f'''allprojects {{ afterEvaluate {{
 sourceSets.main.java.srcDir('tools/validation')
 sourceSets.main.resources.srcDir('{res}')
 minecraft.runs.gameTestServer.property 'forge.enabledGameTestNamespaces', 'dudunka_survey'
}} }}
'''
    for seed in args.seeds:
        name = f'survey-{args.label}-{seed}'
        run(name, init_script(name, seed, extra))
else:
    res = resources('dudunka_25a')
    src = REPORT / 'checks-src'
    java = src / 'io/github/golovinss/dudunka/test/Vale25aFocused.java'
    java.parent.mkdir(parents=True, exist_ok=True)
    methods = [
        ('MushroomValeTests','valeLanternPropagatesBlockLightAndRemovalClearsIt',200),
        ('MushroomValeTests','valeBiomesDecodeTheRegisteredNativeMusic',40),
        ('MushroomValeTests','valeFinishedChunksContainNaturallyGeneratedBenches',240),
        ('MushroomValeTests','valeSignatureFeaturesPlaceWithLightClearanceAndSurvival',120),
        ('MushroomValeTests','valeDecorBlueprintsRotateAndRejectUnsafeSites',200),
        ('MushroomValeTests','valeCottagePreviewRequiresOptInAndPlacesSelectedOrientation',400),
        ('MushroomValeTests','valeBenchesSeatOnePlayerAndCleanUpEveryLifecycle',120),
        ('FamilyTests','welcomeWalksOffLowFurnitureBeforeFinalArrival',100),
        ('FamilyTests','allThreeActuallyWalkToRainInterestsAndResumeHomeMode',360),
        ('FamilyTests','arrivalReadinessBridgesOneSecondParticipantSamplingWithoutDuplicateWelcome',100),
    ] + [('FamilyTests','realExcursionGetsAllThreeWalkingGreetingsWithoutChangingModes',540)] * 6
    text = 'package io.github.golovinss.dudunka.test;\nimport net.minecraft.gametest.framework.*;\nimport net.minecraftforge.gametest.*;\n@GameTestHolder("dudunka_25a") @PrefixGameTestTemplate(false)\npublic class Vale25aFocused {\n'
    for i,(cls,method,timeout) in enumerate(methods):
        text += f' @GameTest(template="empty",templateNamespace="dudunka_25a",timeoutTicks={timeout},batch="isolated_check{i}")\n public static void check{i}(GameTestHelper h) {{ {cls}.{method}(h); }}\n'
    java.write_text(text + '}\n')
    extra = f'''allprojects {{ afterEvaluate {{
 sourceSets.main.java.srcDir('{src.relative_to(ROOT).as_posix()}')
 sourceSets.main.resources.srcDir('{res}')
 minecraft.runs.gameTestServer.property 'forge.enabledGameTestNamespaces', 'dudunka_25a'
 minecraft.runs.gameTestServer.property 'dudunka.traceHomecoming', '{str(args.trace).lower()}'
}} }}
'''
    name = 'focused-' + args.label
    run(name, init_script(name,args.seeds[0],extra), ['verifyValeMusic','runGameTestServer'])
