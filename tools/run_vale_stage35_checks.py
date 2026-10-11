"""Focused Stage 3.5 GameTests in a fresh world, using the real test methods and cleanup hooks."""
from pathlib import Path
import argparse, datetime, fcntl, json, os, re, shutil, subprocess
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('group', choices=['pufik','travel','jumper','wise','regressions','all'])
p.add_argument('--method')
p.add_argument('--seed',type=int,default=-255494441983840131)
a=p.parse_args()
lock_dir=ROOT/'build/reports/dudunka-tests';lock_dir.mkdir(parents=True,exist_ok=True)
lock=(lock_dir/'.runner.lock').open('w')
try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
except BlockingIOError:raise SystemExit('Another tools/test.sh or Stage 3.5 run is active')
classes={'pufik':'PufikCottageTests','travel':'MushroomValeTests','jumper':'FluffyJumperTests','wise':'WiseCottageTests','family':'FamilyTests','spider':'ValeSpiderTests','elder':'WiseFlyAgaricTests'}
report=ROOT/'build/reports/mushroom-vale-stage35'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f');report.mkdir(parents=True)
world=ROOT/'run'/('vale-stage35-'+report.name);world.mkdir();(world/'server.properties').write_text(f'level-seed={a.seed}\n')
namespace='dudunka_35'
src=report/'java';pkg=src/'io/github/golovinss/dudunka/test';pkg.mkdir(parents=True)
entry=['package io.github.golovinss.dudunka.test;','import net.minecraft.gametest.framework.*; import net.minecraftforge.gametest.*;',f'@GameTestHolder("{namespace}") @PrefixGameTestTemplate(false) public class Stage35Entry {{']
count=0
for group,cls in classes.items():
 if a.group=='regressions':
  if group not in ('family','spider','elder'):continue
 elif group in ('family','spider','elder') or a.group not in ('all',group):continue
 text=(ROOT/f'src/main/java/io/github/golovinss/dudunka/test/{cls}.java').read_text()
 for annotation,method in re.findall(r'(@GameTest\([^)]*\))\s*public static void (\w+)\(GameTestHelper h\)',text):
  if group=='family' and method not in ('friendsReallyWatchDudunkasDrawing','friendsReallyRestBesideMarusya','friendsReallyWatchSyusyaPeek'):continue
  if group in ('spider','elder') and method!='peacefulEvenWhenSurprised':continue
  if a.method and method!=a.method:continue
  if group=='travel' and not any(x in method for x in ('RoundTrip','Return','Travel','UnsafeDeparture','CommandsRequire')):continue
  annotation=re.sub(r'(batch\s*=\s*")([^"]+)',r'\1stage35_\2',annotation)
  entry.append(f'{annotation} public static void check{count}(GameTestHelper h) throws Exception {{ {cls}.{method}(h); }}');count+=1
 for batch,method in re.findall(r'@AfterBatch\(batch\s*=\s*"([^"]+)"\)\s*public static void (\w+)\(',text):
  entry.append(f'@AfterBatch(batch="stage35_{batch}") public static void clean{group}{method}(net.minecraft.server.level.ServerLevel level) {{ {cls}.{method}(level); }}')
entry.append('}')
if count==0:raise SystemExit('No matching tests')
(report/'inputs.json').write_text(json.dumps(dict(group=a.group,method=a.method,seed=a.seed,expected_tests=count),indent=2)+'\n')
(pkg/'Stage35Entry.java').write_text('\n'.join(entry)+'\n')
res=report/'resources';target=res/f'data/{namespace}/structures/empty.nbt';target.parent.mkdir(parents=True);shutil.copyfile(ROOT/'src/main/resources/data/dudunka/structures/empty.nbt',target)
init=report/'focused.gradle';init.write_text(f'''allprojects {{ afterEvaluate {{
 sourceSets.main.java.srcDir('{src}')
 sourceSets.main.resources.srcDir('{res}')
 tasks.named('processResources') {{ outputs.upToDateWhen {{ false }} }}
 minecraft.runs.gameTestServer.property 'forge.enabledGameTestNamespaces','{namespace}'
}} }}
gradle.taskGraph.whenReady {{ graph ->
 def task=graph.allTasks.find {{ it.name=='runGameTestServer' }}
 if(task!=null) task.doFirst {{
  def dir=task.project.file('{world}')
  assert !new File(dir,'world/level.dat').exists()
  task.runConfig.get().workingDirectory(dir)
 }}
}}
''')
env=os.environ.copy();env.setdefault('JAVA_HOME','/usr/lib/jvm/java-17-openjdk-amd64');env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
log=report/'gametest.log';print(f'{count} focused tests; log: {log}',flush=True)
try:
 with log.open('w') as out:result=subprocess.run(['./gradlew','--offline','-Dnet.minecraftforge.gradle.check.certs=false','-I',str(init),'runGameTestServer','-x','downloadAssets'],cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT)
finally:
 shutil.rmtree(ROOT/f'build/resources/main/data/{namespace}',ignore_errors=True)
 for f in (ROOT/'build/classes/java/main/io/github/golovinss/dudunka/test').glob('Stage35Entry*.class'):f.unlink()
data=log.read_text();print('\n'.join(data.splitlines()[-30:]),flush=True)
passed=re.search(r'All (\d+) required tests passed',data)
raise SystemExit(result.returncode or (0 if passed and int(passed[1])==count else 1))
