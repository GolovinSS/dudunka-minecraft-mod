# Home-together friendship intermittent GameTest investigation

Date: 2026-10-11  
Target: `togetherfriendshiprequiresactualsimultaneoussitting`

## Finding

The recorded full-suite failure is the tick-45 assertion in `FamilyTests`. Its exact log message was:

```text
togetherfriendshiprequiresactualsimultaneoussitting failed! Real goal ticks must finish seeded minute once for simultaneous sitting
```

The failure occurred after the assertion had seeded 1,180 of the 1,200 required friendship ticks. The assertion did not print entity state, so the original full-suite log does not identify which prerequisite failed. The test was repeated with failure diagnostics. One focused run reproduced the failure in attempt 10/10 and reported:

```text
worldTime=776, serverTime=776, ownerAlive=true, ownerShift=false,
sceneUntil=931, activeA=false, activeB=false,
a=.../IDLE/(91.5, -58.0, 3.5)/seat=0.0,
b=.../IDLE/(93.5, -58.0, 1.5)/seat=0.0,
score=1, progress=0
```

This confirms that both goals had stopped and the gathering was inactive by the tick-45 assertion. The scene had not expired, the owner was alive and not shifting, both companions remained at their reserved seat coordinates, and the friendship point had already been awarded. The evidence does not reveal why `HomeTogetherScenes.active` became false earlier in that run. It does not distinguish a transient participant/seat validation failure from the scene state being replaced or another fixture interaction.

No gameplay change was made because the root trigger could not be confirmed. The test now includes failure-only diagnostics for server/world time, owner state, scene activity/expiry, companion activities and seat distances, score/progress, plus the first tick on which the scene is seen inactive. Future recurrence should provide the missing transition evidence.

## Focused repetitions

`python3 tools/run_home_together_checks.py isolated --repeat 10`: 9/10 passed; attempt 10 reproduced the state above.

`python3 tools/run_home_together_checks.py isolated --repeat 10`: 10/10 passed.

`python3 tools/run_home_together_checks.py shared --repeat 10`: 10 target tests passed in one shared batch.

`python3 tools/run_home_together_checks.py shared --repeat 10`: 10 target tests plus the four neighboring home-together fixture scenarios passed in one 14-test batch.

`python3 tools/run_home_together_checks.py isolated --repeat 20`: 20/20 passed.

Across the four subsequent runs, all 50 target attempts passed, including concurrent/shared-batch coverage. The first, 10-attempt run had one failure, for 69/70 total target attempts passing. These results establish intermittency but do not establish a root cause.

## Build

`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH ./gradlew --offline -Dnet.minecraftforge.gradle.check.certs=false build` — **passed**. This includes `test`, `verifyValeFirefly`, `verifyValeMusic`, and `check`.

The same build command without the Java 17 environment first failed because the machine selected Java 8 to launch the Java 17 `verifyValeFirefly` helper; rerunning with the project Java 17 runtime passed.

The most recent full GameTest suite run remains the earlier 174-test run with this one failure. No full suite was rerun because no confirmed fix was made. No firefly, spawn-balance, scene, friendship, identifier, or compatibility behavior was changed.
