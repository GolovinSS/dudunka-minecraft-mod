# Companion catch-up GameTest investigation after Stage 3.2b

## Diagnosis

The two failures in the original Stage 3.2b full run were:

- `catchuppreservesidentityandavoidsoccupiedplaces`: `Fixture must provide a safe loaded landing`.
- `followgoalreallycatchesupaftersixstalledseconds`: `Actual goal ticks must catch up over the unreachable gap`.

Original log: `build/reports/mushroom-vale-stage32/full-20261011-021233/gametest.log`.
Its saved `run/vale-stage32-full-20261011-021233/world/serverconfig/dudunka-server.toml`
contains **`familyCatchUp = false`**, despite a new world's default being true.
The test `catchUpRejectsCommandsVehiclesForeignOwnersAndUnsafeGround` toggled
the live setting off and back on before the two failing tests ran. That test
and its live `.set(false)`/restore already existed in baseline `a0f67bc`.

Cached Forge 47.4.13 sources show that `ConfigFileTypeHandler` builds an
autosaving file config and installs a watcher that reloads the same config on
another thread, then invalidates `ForgeConfigSpec` value caches. Two live
config mutations therefore allow a save/reload race across restoration. The
test can leave the shared file and subsequent catch-up checks disabled.

The failures are test isolation across shared configuration, not a change to
companion movement or firefly spawning. An unmodified-logic diagnostic full
run passed 174/174 and a focused parallel run passed 15/15, confirming that the
original failure is intermittent. Their saved configs retained `true`.

A deterministic focused reproduction supplied the failed world's disabled
setting to a fresh world and ran the two tests in separate batches. Both
failed at the original assertions. Diagnostics showed `ownerLoaded=true`,
`mobLoaded=true`, `ownerGround=true`, `enabled=false`, `eligible=false`, and
`destination=null`. The navigation goal was still ticked, but its counter
cannot credit an eligible stall or teleport while configuration forbids it.
This excludes unloaded chunks and parallel fixture interference as necessary
causes; neither assertion failed because a timeout expired.

## Fix

Only the hazard/config GameTest now attaches an in-memory copy of the loaded
config while exercising the disabled setting. A `finally` restores the original
file-backed config. The test explicitly invalidates Forge's value caches,
checks disabled behavior, checks that the original setting never changed,
and checks restoration. The original identity, occupied-landing and timing
assertions and all deadlines remain unchanged. Failure messages now include
positions, loaded chunks, setting, eligibility, landing and navigation state.

`FamilyTravel`, `FamilyFollowGoal`, production config handling, firefly code,
spawn weights, category, caps, palettes and visual assets are unchanged.

The focused runner generates test wrappers outside normal source/resource
paths, removes compiled wrappers/templates afterward, and always creates a
fresh world. Its disabled-setting mode is an intentional failing diagnostic.

## Results

| Check | Result |
| --- | --- |
| Diagnostic full run before fix | PASS, 174/174 (`full-20261011-022031`) |
| Focused parallel before fix | PASS, 15/15 (`parallel-20261011-022217`) |
| Focused disabled-setting reproduction, isolated | EXPECTED FAIL, 0/2; both original assertions (`isolated-20261011-022509`) |
| Focused isolated after fix | PASS, 15/15; saved setting true (`isolated-20261011-022613`) |
| Focused parallel after fix | PASS, 15/15; saved setting true (`parallel-20261011-022715`) |
| Build, `verifyModels`, `verifyValeFirefly` | PASS; Java 17 / Forge 47.4.13, `build/reports/family-travel/build.log` |
| Complete GameTest suite after fix | 173/174; both original catch-up tests and all firefly tests passed; saved setting true (`full-20261011-022836`) |
| `git diff --check` | PASS |

Full logs are under `build/reports/mushroom-vale-stage32/`; focused logs are
under `build/reports/family-travel/`, in the named run directories.
The final full run used `python3 tools/run_vale_firefly_checks.py full` and
exited 1 because `togetherfriendshiprequiresactualsimultaneoussitting` failed:
`Real goal ticks must finish seeded minute once for simultaneous sitting`.
That distinct home-gathering test was not modified, and its root cause remains
unresolved. The complete suite is therefore **not green**. No assertion,
timeout or gameplay change was made to suppress that failure.

Build command: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build
verifyModels verifyValeFirefly --offline -Dnet.minecraftforge.gradle.check.certs=false`.
The built JARs exclude generated focused wrappers. Firefly gameplay,
renderer/model and all three biome JSON hashes match the investigation-start
versions. Stage 3.2/3.2a/3.2b work remains uncommitted; no commit or push was made.
Manual Minecraft/BMC4 acceptance is separate; this change only fixes tests.
