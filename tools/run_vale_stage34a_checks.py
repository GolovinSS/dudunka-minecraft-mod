"""Stage 3.4a residency, elder and firefly checks, full suite and build.

Fresh worlds only. Opt-in survey sources/templates are removed from build outputs.
No production source overlays, competitor removal, custom spawning or cap overrides.
"""
from pathlib import Path
import argparse, datetime, fcntl, hashlib, json, os, re, shutil, subprocess, sys

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('mode', choices=['focused', 'survey', 'full', 'build', 'residency', 'firefly'])
p.add_argument('--seed', type=int, default=0)
p.add_argument('--seeds', type=int, nargs='+')
p.add_argument('--method', help='One targeted method for residency or firefly diagnosis')
a = p.parse_args()
if a.method and a.mode not in ('residency','firefly'): p.error('--method applies only to residency/firefly')
if a.mode in ('full', 'build') and (a.seed != 0 or a.seeds): p.error('Seed overrides apply to focused, survey, residency and firefly only')
if a.seeds:
    results=[subprocess.call([sys.executable,__file__,a.mode,'--seed',str(seed)]) for seed in a.seeds]
    raise SystemExit(1 if any(results) else 0)
base = ROOT/'build/reports/mushroom-vale-stage34a';base.mkdir(parents=True,exist_ok=True)
lock = (base/'.runner.lock').open('w');fcntl.flock(lock,fcntl.LOCK_EX)
name = f'{a.mode}-{a.seed if a.mode in ("focused","survey","residency","firefly") else "default"}-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
report=base/name;report.mkdir();world=ROOT/'run'/('vale-stage34a-'+name);world.mkdir()
(report/'inputs.json').write_text(json.dumps({
    'mode':a.mode,'seed':a.seed if a.mode in ('focused','survey','residency','firefly') else None,
    'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip(),
    'production_sha256':{str(path.relative_to(ROOT)):hashlib.sha256(path.read_bytes()).hexdigest()
        for path in (ROOT/'src/main').rglob('*') if path.is_file()},
    'survey_sha256':hashlib.sha256((ROOT/'tools/validation34/WiseSurvey.java').read_bytes()).hexdigest(),
},indent=2)+'\n')
extra='';namespace='dudunka_34'
if a.mode in ('focused','survey','residency','firefly'):
    src=report/'java';pkg=src/'io/github/golovinss/dudunka/test';pkg.mkdir(parents=True)
    (pkg/'WiseSeed.java').write_text(f'''package io.github.golovinss.dudunka.test;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="dudunka")
public class WiseSeed {{
 @net.minecraftforge.eventbus.api.SubscribeEvent public static void seed(net.minecraftforge.event.server.ServerAboutToStartEvent e) throws Exception {{
  var f=net.minecraft.world.level.storage.PrimaryLevelData.class.getDeclaredField("worldOptions");f.setAccessible(true);
  f.set(e.getServer().getWorldData(),new net.minecraft.world.level.levelgen.WorldOptions({a.seed}L,false,false));
 }}
}}
''')
    entry='''package io.github.golovinss.dudunka.test;
import net.minecraft.gametest.framework.*;import net.minecraftforge.gametest.*;
@GameTestHolder("dudunka_34") @PrefixGameTestTemplate(false) public class WiseEntry {
'''
    if a.mode=='survey':
        shutil.copyfile(ROOT/'tools/validation34/WiseSurvey.java',pkg/'WiseSurvey.java')
        entry+='''@GameTest(template="empty",timeoutTicks=10000,batch="survey") public static void survey(GameTestHelper h){WiseSurvey.start(h);}
@AfterBatch(batch="survey") public static void cleanup(net.minecraft.server.level.ServerLevel level){WiseSurvey.cleanup();}
'''
    else:
        test_class = 'WiseCottageTests' if a.mode=='residency' else 'ValeFireflyTests' if a.mode=='firefly' else 'WiseFlyAgaricTests'
        methods = ['recognitionUniquenessAndDurableIssuance','nearbyMultiplayerActivation','existingWorldAndUnloadedBoundaries','liveCottageNavigationAndHomePauses','actualChunkUnloadAndDiskReload','liveEastNavigation','liveSouthNavigation','liveWestNavigation'] if a.mode=='residency' else ['groundTakeoffAndSustainedHover'] if a.mode=='firefly' else ['registrationAndAssetReferences','habitatAndSafeSpawnRestrictions','greetingAndVanillaSaveReload','peacefulEvenWhenSurprised','sharedCreatureCapAndOrdinaryDespawn','safeActiveMovement']
        if a.method: methods=[a.method]
        for i,method in enumerate(methods):
            entry+=f'@GameTest(template="empty",timeoutTicks={2300 if a.mode=="residency" else 640 if a.mode=="firefly" else 900},batch="isolated_{i}") public static void check{i}(GameTestHelper h) throws Exception {{{test_class}.{method}(h);}}\n'
        if a.mode=='focused': entry+='@AfterBatch(batch="isolated_3") public static void cleanup(net.minecraft.server.level.ServerLevel level){WiseFlyAgaricTests.cleanupSocial(level);}\n'
        if a.mode=='focused': entry+='@AfterBatch(batch="isolated_5") public static void cleanupGround(net.minecraft.server.level.ServerLevel level){WiseFlyAgaricTests.cleanupGround(level); }\n'
    if a.mode=='residency':
        for i in range(len(methods)): entry+=f'@AfterBatch(batch="isolated_{i}") public static void cleanup{i}(net.minecraft.server.level.ServerLevel level){{WiseCottageTests.after(level);}}\n'
    if a.mode=='firefly': entry+='@AfterBatch(batch="isolated_0") public static void cleanupFirefly(net.minecraft.server.level.ServerLevel level){ValeFireflyTests.cleanupFlightFixture();}\n'
    (pkg/'WiseEntry.java').write_text(entry+'}\n')
    res=report/'resources';target=res/f'data/{namespace}/structures/empty.nbt';target.parent.mkdir(parents=True)
    shutil.copyfile(ROOT/'src/main/resources/data/dudunka/structures/empty.nbt',target)
    extra=f'''allprojects {{ afterEvaluate {{
 sourceSets.main.java.srcDir('{src}')
 sourceSets.main.resources.srcDir('{res}')
 tasks.named('processResources') {{ outputs.upToDateWhen {{ false }} }}
 minecraft.runs.gameTestServer.property 'forge.enabledGameTestNamespaces','{namespace}'
}} }}
'''
init=report/'fresh-world.gradle'
init.write_text(extra+f'''
gradle.taskGraph.whenReady {{ graph ->
 def task=graph.allTasks.find {{ it.name=='runGameTestServer' }}
 if(task!=null) task.doFirst {{
  def dir=task.project.file('{world}')
  assert !new File(dir,'world/level.dat').exists() : 'Requires unused world'
  task.runConfig.get().workingDirectory(dir)
 }}
}}
''')
env=os.environ.copy();env['JAVA_HOME']='/usr/lib/jvm/java-17-openjdk-amd64';env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
tasks=['build','verifyModels','verifyPufik','verifyValeSpider','verifyWiseFlyAgaric'] if a.mode=='build' else ['runGameTestServer']
log=report/'gametest.log';print(log,flush=True)
try:
    with log.open('w') as out:
        result=subprocess.run(['./gradlew','--offline','-Dnet.minecraftforge.gradle.check.certs=false','-I',str(init),*tasks,'-x','downloadAssets'],cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT)
finally:
    if a.mode in ('focused','survey','residency','firefly'):
        shutil.rmtree(ROOT/f'build/resources/main/data/{namespace}',ignore_errors=True)
        for helper in ('WiseSeed','WiseEntry','WiseSurvey'):
            for f in (ROOT/'build/classes/java/main/io/github/golovinss/dudunka/test').glob(helper+'*.class'): f.unlink()
data=log.read_text()
print('\n'.join(data.splitlines()[-25:]),flush=True)
# Forge can return exit 0 after a startup exception: require completed GameTests.
completed=a.mode=='build' or re.search(r'All \d+ required tests passed',data) is not None
if result.returncode==0 and not completed: print('ERROR: server did not report completed passing GameTests',flush=True)
raise SystemExit(result.returncode or (0 if completed else 1))
