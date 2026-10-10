# Mushroom Vale Stage 2.1 validation — 2026-10-10

Implementation uses Minecraft 1.20.1 / Forge 47.4.13 / OpenJDK 17.0.20.1 /
Gradle 8.8. Manual graphical and full Better MC BMC4 v55.5 acceptance remain
pending. See [terrain architecture](MUSHROOM-VALE.md#stage-21-natural-terrain-and-worldgen-architecture)
and the [runClient checklist](TESTING.md#stage-21-mushroom-vale-natural-terrain).

## Automated validation

- JSON syntax: **118/118** source JSON resources parsed successfully.
- All new vanilla noise references resolved against the cached Minecraft 1.20.1
  client JAR; implementation consulted cached Minecraft/Forge/ForgeGradle sources,
  without external research.
- `compileJava -x downloadAssets`: **BUILD SUCCESSFUL**, exit **0**, 18 seconds.
  An initial sandbox attempt could not write the existing Gradle wrapper cache;
  the approved cache-access rerun succeeded. Java reports the existing use of
  deprecated Minecraft APIs; compilation has no errors.
- Initial `build verifyModels runGameTestServer -x downloadAssets`: **157/157
  required GameTests passed**, including **9/9 Vale tests**. This includes the
  existing uncommitted dialogue tests. No unrelated tests were modified.
- The first two full runs each passed **157/157**, but exposed an isolation
  failure in the temporary Gradle init script: the first `JavaExec.workingDir`
  override and subsequent RunConfig override were skipped because ForgeGradle
  registers the run task after the hooks used. Both used `run/world`; those
  passes establish regression behavior, not fresh-chunk acceptance. A further
  configuration-only attempt failed before tasks with `runGameTestServer not
  found`. The final script uses the ready task graph, asserts the target world
  has no `level.dat`, and sets the actual RunConfig at execution time. The
  final runtime log explicitly confirms GAMEDIR is the separate, previously
  empty `run/vale-stage21-gametest/` directory. These repetitions investigated
  validation isolation; no unrelated flaky tests or implementation assertions
  were changed. No chunks were deleted and no saved dimension settings edited.
  Existing graphical worlds under `run/saves/` are untouched.

Fresh-world final result: **BUILD SUCCESSFUL**, exit **0**, **1m 32s**,
**157/157 required GameTests**, including **9/9 MushroomValeTests**.
`build` passed with resource processing and reobfuscated JAR; `verifyModels`
passed all nine age/model trees and existing geometry/UV/animation checks.
Fresh startup reported a missing `server.properties` before creating the new
server defaults; this did not prevent world loading or any test. Existing
Gradle deprecation notices remain.

Additional checks: `git diff --check` passed; JAR ZIP integrity and equality of
packaged Vale dimension/type and all worldgen JSON against source passed.
Artifact: `build/libs/dudunka-0.26.0-alpha.jar`.
SHA-256: `c2adfe34c64306e64214a4638338914cb0764dc904f707908ca0283988f4de71`.
The version remains unchanged; this is an uncommitted development artifact.

Local evidence (ignored build artifacts):

- `build/reports/mushroom-vale-stage21/confirmed-fresh.log`: final fresh-world run.
- `build/reports/mushroom-vale-stage21/initial.log`: first regression run.
- `build/reports/mushroom-vale-stage21/isolation-retry.log`: second regression run.
- `build/reports/mushroom-vale-stage21/compile.log`: initial successful compilation.
- `build/reports/mushroom-vale-stage21/fresh-world.gradle`: effective isolation script.

The corrective command is:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew -I /tmp/vale-stage21-fresh-world.gradle \
  build verifyModels runGameTestServer -x downloadAssets
```

The temporary init script uses `gradle.taskGraph.whenReady`, finds
`runGameTestServer`, and adds a `doFirst` action to call
`runConfig.get().workingDirectory(project.file('run/vale-stage21-gametest'))`.
It asserts that the directory has no saved world; a future fresh acceptance run
must select another unused directory. The script is outside the repository;
ordinary Gradle runs retain their existing config.
Client asset download is excluded only for the headless checks.

### Terrain samples

The new test uses vanilla noise-generator base columns at **1089 locations per
seed** over X/Z=-2048…2048, 128-block spacing, plus each column's east and south
neighbors. Low columns are also checked for actual water at Y=62. No distant
chunks are generated for these samples.

| Seed | Base-column height range | Maximum sampled adjacent step | Wet / dry columns |
| --- | --- | --- | --- |
| 0 | 52–100 | 1 | 67 / 1022 |
| 42 | 52–100 | 1 | 71 / 1018 |
| -71021 | 52–100 | 1 | 76 / 1013 |

These numbers describe base terrain before surface decoration and carvers;
they are not a proof of every finished route's navigability or visual quality.
The registry/travel tests generate actual Vale chunks and check the separate
unchanged type, vanilla noise generator, Vale noise settings, explicit multi-noise
source, sole temporary biome, bedrock, and absence of the three placeholder
biomes. The blocked-entry fixture now obstructs every candidate column at its
actual surface height. Existing opt-in, permissions, round-trip identity,
companion NBT, bookmark serialization, hazards, fallback, unsafe-return retry,
and Forge veto rollback checks continue to run.

## Changes and preservation

Stage 2.1 changes `dimension/mushroom_vale.json`, adds three resources under
`worldgen/noise_settings`, `worldgen/density_function/mushroom_vale`, and
`worldgen/noise/mushroom_vale`, updates `MushroomVale.java` surface landing and
`MushroomValeTests.java`, and updates `MUSHROOM-VALE.md`, `TESTING.md`,
`VALIDATION.md`, plus this report. Existing Stage 1 and dialogue work was already
modified/untracked at task start and remains in the workspace.

A before/after SHA-256 manifest of existing source files and `build.gradle`
confirms that this stage changed only the dimension JSON, travel class and Vale
tests among those existing files. In particular the dimension type, return-data
class, dialogue implementation/tests, companion/family changes, language files,
build configuration, and album protocol **9** retain their task-start contents.
No commit, push or PR was created.

## Manual acceptance and compatibility limits

Not performed: graphical `runClient`, aesthetic review, finished-terrain walking
routes across seeds, actual client reconnect/server restart, old-world upgrade,
multiplayer and full BMC4 v55.5 acceptance. Vanilla mushroom-fields carvers,
lava features, vegetation and biome-tag-based structures are inherited; no
claim is made that disabling noise caves removes these independent features.
Modpack biome changes and hazards need manual review.

Use a fresh world created with Stage 2.1 installed for terrain acceptance.
Existing worlds may retain saved flat generator settings even in new chunks;
old generated chunks are never regenerated or migrated. `/reload` is not a
migration mechanism. On a backup copy, separately verify retained builds,
player location, developer travel/bookmarks and unaffected other dimensions.
Surface entry searches only one chunk and safely refuses when that entire area
is submerged or obstructed. No arrival platform is manufactured.
