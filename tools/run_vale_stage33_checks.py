"""Isolated focused tests, actual natural spawning surveys, full suite and final build.

Fresh worlds only. Opt-in survey sources/templates are removed from build outputs.
No production source overlays, competitor removal, custom spawning or cap overrides.
"""
from pathlib import Path
import argparse, datetime, fcntl, hashlib, json, os, shutil, subprocess, sys

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('mode', choices=['focused', 'survey', 'full', 'build'])
p.add_argument('--seed', type=int, default=0)
p.add_argument('--seeds', type=int, nargs='+')
a = p.parse_args()
if a.mode in ('full', 'build') and (a.seed != 0 or a.seeds): p.error('Seed overrides apply to focused/survey only')
if a.seeds:
    for seed in a.seeds: subprocess.check_call([sys.executable,__file__,a.mode,'--seed',str(seed)])
    raise SystemExit(0)
base = ROOT/'build/reports/mushroom-vale-stage33';base.mkdir(parents=True,exist_ok=True)
lock = (base/'.runner.lock').open('w');fcntl.flock(lock,fcntl.LOCK_EX)
name = f'{a.mode}-{a.seed if a.mode in ("focused","survey") else "default"}-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
report=base/name;report.mkdir();world=ROOT/'run'/('vale-stage33-'+name);world.mkdir()
(report/'inputs.json').write_text(json.dumps({
    'mode':a.mode,'seed':a.seed if a.mode in ('focused','survey') else None,
    'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT).decode().strip(),
    'production_sha256':{str(path.relative_to(ROOT)):hashlib.sha256(path.read_bytes()).hexdigest()
        for path in (ROOT/'src/main').rglob('*') if path.is_file()},
    'survey_sha256':hashlib.sha256((ROOT/'tools/validation33/SpiderSurvey.java').read_bytes()).hexdigest(),
},indent=2)+'\n')
extra='';namespace='dudunka_33'
if a.mode in ('focused','survey'):
    src=report/'java';pkg=src/'io/github/golovinss/dudunka/test';pkg.mkdir(parents=True)
    (pkg/'SpiderSeed.java').write_text(f'''package io.github.golovinss.dudunka.test;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="dudunka")
public class SpiderSeed {{
 @net.minecraftforge.eventbus.api.SubscribeEvent public static void seed(net.minecraftforge.event.server.ServerAboutToStartEvent e) throws Exception {{
  var f=net.minecraft.world.level.storage.PrimaryLevelData.class.getDeclaredField("worldOptions");f.setAccessible(true);
  f.set(e.getServer().getWorldData(),new net.minecraft.world.level.levelgen.WorldOptions({a.seed}L,false,false));
 }}
}}
''')
    entry='''package io.github.golovinss.dudunka.test;
import net.minecraft.gametest.framework.*;import net.minecraftforge.gametest.*;
@GameTestHolder("dudunka_33") @PrefixGameTestTemplate(false) public class SpiderEntry {
'''
    if a.mode=='survey':
        shutil.copyfile(ROOT/'tools/validation33/SpiderSurvey.java',pkg/'SpiderSurvey.java')
        entry+='''@GameTest(template="empty",timeoutTicks=10000,batch="survey") public static void survey(GameTestHelper h){SpiderSurvey.start(h);}
@AfterBatch(batch="survey") public static void cleanup(net.minecraft.server.level.ServerLevel level){SpiderSurvey.cleanup();}
'''
    else:
        for i,method in enumerate(['registrationAndAssetReferences','habitatAndSafeSpawnRestrictions','greetingAndVanillaSaveReload','peacefulEvenWhenSurprised','sharedCreatureCapAndOrdinaryDespawn']):
            entry+=f'@GameTest(template="empty",timeoutTicks=200,batch="isolated_{i}") public static void check{i}(GameTestHelper h) throws Exception {{ValeSpiderTests.{method}(h);}}\n'
        entry+='@AfterBatch(batch="isolated_3") public static void cleanup(net.minecraft.server.level.ServerLevel level){ValeSpiderTests.cleanupSocial(level);}\n'
    (pkg/'SpiderEntry.java').write_text(entry+'}\n')
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
tasks=['build','verifyModels','verifyPufik','verifyValeSpider'] if a.mode=='build' else ['runGameTestServer']
log=report/'gametest.log';print(log,flush=True)
try:
    with log.open('w') as out:
        result=subprocess.run(['./gradlew','--offline','-Dnet.minecraftforge.gradle.check.certs=false','-I',str(init),*tasks,'-x','downloadAssets'],cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT)
finally:
    if a.mode in ('focused','survey'):
        shutil.rmtree(ROOT/f'build/resources/main/data/{namespace}',ignore_errors=True)
        for f in (ROOT/'build/classes/java/main/io/github/golovinss/dudunka/test').glob('Spider*.class'): f.unlink()
print('\n'.join(log.read_text().splitlines()[-25:]),flush=True)
raise SystemExit(result.returncode)
