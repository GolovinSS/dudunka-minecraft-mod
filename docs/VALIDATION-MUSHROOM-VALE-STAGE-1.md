# Mushroom Vale Stage 1 validation — 2026-10-10

Stage 1 implementation is complete. Graphical Minecraft and full BMC4 acceptance
are still pending. The implementation contract is in [MUSHROOM-VALE.md](MUSHROOM-VALE.md#stage-1-prototype-contract);
the IntelliJ runClient checklist is in [TESTING.md](TESTING.md#stage-1-mushroom-vale-in-intellij-idea).

## Automated results

Environment: Minecraft 1.20.1, Forge 47.4.13, OpenJDK 17.0.20.1, Gradle 8.8.
The shell default was Java 8, so `JAVA_HOME` and `PATH` explicitly selected JDK 17.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew build verifyModels runGameTestServer -x downloadAssets
```

Final result: **BUILD SUCCESSFUL**, exit **0**, **150/150 required GameTests**
(142 existing + 8 new), in 1m 1s. Client asset download was excluded for this
headless server/model run; graphical client startup was not performed.

- `build`: passed, including compilation, resource processing, and reobfuscated JAR.
- `verifyModels`: passed all nine age/model trees and the existing animation,
  geometry, UV, and pose checks.
- `MushroomValeTests`: **8/8 passed**. Actual Forge command registration and server
  dispatcher execution; loaded JSON dimension/type and vanilla terrain layers;
  JVM opt-in, permission level 2, and player-only access; round-trip identity,
  rotation, motion, and fall-distance handling; companion instance/UUID/full-NBT
  preservation for all three kinds; vanilla player Dimension serialization;
  separate return bookmark serialization and malformed-row handling; unsafe
  departure/passengers; missing/removed/unsafe bookmark fallback; blocked entry;
  Forge travel veto rollback; unsafe return retaining a bookmark and a successful
  retry after a safe return point becomes available.
- JAR integrity and both packaged worldgen JSON resources were checked against
  source; both travel classes are present. Existing album protocol remains **9**.
- `git diff --check`: passed. Existing production family/entity/network sources
  were not modified; no dependency or version change was made.

Local logs (ignored build artifacts):

- `build/reports/mushroom-vale/final.log`: final successful run with actual server commands.
- `build/reports/mushroom-vale/earlier-pass.log`: earlier 150/150 successful run.
- `build/reports/mushroom-vale/intermittent-failures.log`: intervening 147/150 run.

Artifact: `build/libs/dudunka-0.26.0-alpha.jar`.
SHA-256: `4f3cfb425bcc3be3f710d0db3718d04d4bdc0330b9b4b4d4acae69cdcb1c69b6`.
The version is unchanged; this is an uncommitted Stage 1 development artifact.

## Intermittent existing tests

The first run, before the eighth Vale test was added, passed 148/149 tests; the
unchanged `realExcursionGetsAllThreeWalkingGreetingsWithoutChangingModes` test
missed Syusya's greeting deadline. An intervening run with all eight Vale tests
passed 147/150: that same homecoming test and these unchanged loot tests failed:

- `registeredFriendNoteLootPreservesRandomAndResources`
- `noteLootRoutesPreserveEggRollsAndExistingItems`

All Vale tests passed in every run. The loot failures coincided with
`trailNotes=false` being left in the generated `run/world/serverconfig/dudunka-server.toml`;
the log shows Forge's config watcher reloading this file while the existing tests
temporarily toggle that setting. Restoring the test world's default
`trailNotes=true` was followed by the final 150/150 pass. The homecoming fixture
also passed on both successful full runs. These observations remain a test
reliability risk; no changes were made to unrelated gameplay or legacy test
assertions to hide them. The player's existing `run/saves/` worlds were not edited.

## Manual acceptance and compatibility limits

Not yet performed: IntelliJ graphical `runClient`, actual networked clients on
a dedicated server, save/stop/restart with a real player inside Vale, upgrade of
a pre-change world, and full Better MC BMC4 v55.5 acceptance. GameTests used real
ServerPlayer objects with a simulated network; bookmark NBT round-trips and the
vanilla Dimension field do not establish actual client reconnection or crash
recovery. Run the documented checklist before claiming those results.

Dimension/type IDs and the flat generator settings now form part of world data.
Later terrain stages must preserve existing chunks and consider generator
persistence. The developer command normally loads its destination chunk during
explicit travel and does not add persistent force-load tickets. A return refuses
when both the saved departure area and Overworld spawn chunk are unsafe; an
operator must repair a landing and retry. Modded hazards/protection hooks need
BMC4 acceptance. No final family portal, quests, custom mobs, companion transfer,
or reconstruction was implemented. Existing NBT formats, UUIDs, registry IDs,
namespace, and protocol 9 are preserved.

No commit, push, or PR was created. Work stops at Stage 1.
