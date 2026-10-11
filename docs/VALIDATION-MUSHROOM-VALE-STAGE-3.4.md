# Stage 3.4 — Wise Fly Agaric validation

Baseline: clean `main` at `ae81ad4`, release `0.27.0-alpha`. Target: Minecraft
1.20.1, Forge 47.4.13, Java 17, Better MC BMC4 v55.5. No commit or push.
The installed Java used for checks is OpenJDK 17.0.20.1. No internet search,
new dependency, mixin, custom packet or protocol change was used.

## Implementation decisions

The actual approved `wise_fly_agaric.png` and `cottages_and_decor.png` were
inspected before implementation, alongside Pufik, ValeSpider, their models,
registration, spawning/tests, the cottage feature and Wise cottage JSON, and
cached mapped Forge/Minecraft sources. The original model expresses the elder
through a red spotted dome, beard/moustache/brows, leafy cloak, belt and pouch,
staff with lantern and a reading book. The prop lantern does not emit world
light. [Actual baked geometry preview](WISE-FLY-AGARIC-PREVIEW.png) includes front,
side, back, three-quarter, idle, walk, look-up, greet, think and read views.
It is an offline texture-sampled rendering, not a Minecraft screenshot.

`WiseFlyAgaric` independently extends `PathfinderMob`, registered through the
existing DeferredRegister as `dudunka:wise_fly_agaric`. Attributes: health 12,
speed 0.12, follow range 8; collision size 0.72×1.15. No `Kind`/`Companion`
entry, ownership, growth, carrying, friendship, inventory, quest, trade or reward.
Target setting and melee damage are disabled, and farmland cannot be trampled.
AI and interactions are server-authoritative. Ordinary entity metadata carries
emote type and start time, without custom networking. Vanilla NBT retains
UUID/name/health; animations and four-second interaction cooldown are transient.

Strolling chooses at most eight local columns and three near-foot heights,
checks reachable paths and every path node with `Pufik.safeGround`, follows the
checked path and rechecks its next node. The bounded 80-tick paths avoid water,
cliffs, lava and damaging ground without terrain edits or custom chunk tickets.
FloatGoal is recovery from external immersion. Thinking/reading pauses stop
walking. Client idle/walk/look and sine emote envelopes use rigid transforms;
per-entity weak-map smoothing blends look and interrupted emotes independently.
Face, beard and cap share one head pivot. Close look lifts the head and opens
the brim sightline. Each render resets geometry and book visibility.

Six friendly dialogue lines are provided in both Russian and English, selected
by ordinary main-hand right-click, with an 80-tick server cooldown. No social
state is saved into family data.

Cottage association is explicitly deferred. The approved cottage is a bounded
block blueprint placed by `MushroomValeDecorFeature`, without structure starts,
resident identity markers or a saved residency lifecycle. Adding a reliable
single resident across chunk unload/reload, despawn and old chunks needs its own
contract. No cottage scan, forced loading, worldgen entity creation or persistent
duplicate population is added. Cottage blueprints/placement/interiors are unchanged.

## Natural spawning and encounters

Memory Forest receives one additive `CREATURE` entry: weight **10**, group **1–1**.
Both `dudunka:mushroom_vale` and `dudunka:memory_forest` are required. Dry sturdy
soil/moss, body/head clearance, safe neighboring floors, world border and surface
height bounds exclude fluids, damaging floors, roofs/caps and deep caves.
Existing spawn entries and their numeric weights are unchanged. The new entry
shares weighted selection and the cap with existing creatures; this necessarily
adds competition. No existing creature is removed or given a different cap/rate.

Cached `NaturalSpawner` confirms runtime spawn Y is randomly sampled between
minimum build height and column surface, whereas chunk-generation spawning
tries top non-colliding positions. Both production spawning paths remain vanilla;
runtime creatures use the existing 400-tick cadence and cap of ten. Names,
explicit persistence and Forge despawn vetoes protect otherwise ordinary despawn.

Surveys use a real non-spectator server observer, view/simulation distance eight,
fresh seeded worlds, a 352-block dry Memory-start route over 4,800 ticks, normal
population/caps, no clearing competitors and no synthetic mob creation. Each
birth is a successful entity join, classified by actual spawn reason. Sightings
are standing-observer line-of-sight samples within 64 blocks and ±8 vertical
blocks, sampled every 20 ticks after 400 ticks of warmup. This is a server
encounter proxy; it does not prove client silhouette visibility or BMC4 behavior.
Zero-encounter routes are retained.

Measured routes (all samples are server proxies, not client acceptance):

| Seed / route start X,Z | Completed chunk-generation births | Runtime births | Visible samples / 220 | Peak visible / resident within 128 | Cap-full samples |
| --- | ---: | ---: | ---: | --- | ---: |
| 0 / 8512,7840 | 0 | 0 | 0 | 0 / 0 | 207 |
| 42 / 7648,8832 | 5 | 0 | 80 | 1 / 3 | 220 |
| 2026 / 7424,8160 | 4 | 0 | 0 | 0 / 3 | 164 |

All nine completed births were in Memory Forest. Seed 42 also recorded 60
samples with an elder within 32 blocks. Seed 2026 recorded 20 samples within
64 blocks but no unobstructed sightline. Seed 0 produced no elder. Runtime
position checks were zero on all three routes; existing creature-cap competition
prevented evidence of runtime replenishment. No rate/cap was increased to hide
this result. Discovery is uncommon and varies with seed, vegetation and existing
population; it is not guaranteed over a short route. For manual reproduction,
seed 42 starts near **7648,8832** and moves 352 blocks east over about four
simulated minutes.

The seed-0 survey **failed** its explicit completed-birth assertion (exit 1).
This negative result is retained, rather than treating eligible spawn positions
as successful encounters. Seeds 42 and 2026 passed the completed-birth assertion;
only seed 42 provided actual sightline encounters. The survey runner continues
all requested seeds and returns failure if any birth-required route fails.

Evidence: [population CSV](validation-data/mushroom-vale-stage34-population.csv),
[raw telemetry](validation-data/mushroom-vale-stage34-telemetry.txt),
[structured metrics/log hashes](validation-data/mushroom-vale-stage34-metrics.json).

## Automated results

Evidence directories below are under `build/reports/mushroom-vale-stage34/`
unless stated otherwise. Every reported server pass includes the actual
GameTest completion summary, not just Gradle's exit code.

| Check | Actual result / evidence |
| --- | --- |
| Focused entity GameTests | **PASS 6/6**, exit 0; `focused-0-20261011-131114/gametest.log` |
| Seed 0 natural survey | **FAIL completed-birth assertion**, zero births; `survey-0-20261011-131245/gametest.log` |
| Seed 42 natural survey | **PASS 1/1**, 5 completed births; `survey-42-20261011-131411/gametest.log` |
| Seed 2026 natural survey | **PASS 1/1**, 4 completed births; `survey-2026-20261011-131524/gametest.log` |
| One full suite | **192/193 passed, 1 required test FAILED**, exit 1; `full-default-20261011-131641/gametest.log`, seed **-4853257702107486027** |
| Final build/check/reobfuscation | **PASS**, exit 0; `build-default-20261011-132035/gametest.log` |
| `verifyModels` | **PASS**, all nine family layers |
| `verifyPufik`, `verifyValeSpider`, `verifyValeFirefly`, `verifyValeMusic` | **PASS** |
| `verifyWiseFlyAgaric` | **PASS**, 120 parts, height **1.1575 blocks**, original 128×64 atlas, UVs, locales, renderer references, 960 rigid poses, idle/walk/think/greet/read/look checks and six standing-camera sightlines at 1–3 blocks |
| Resource/JAR checks | **PASS**, 304 JSON resources, 115 worldgen references, 8 blueprints, 81 model references; **372 JSON/PNG/OGG resources** byte-match JAR |
| Additive compatibility/JAR audit | **PASS**, all **473** baseline production paths checked; only authorized registration/locales/Memory spawn additions; all packaged JSON/PNG/OGG/NBT match, no survey helpers |
| Python syntax / `git diff --check` | **PASS** |

The full suite's single failure was the unchanged
`ValeFireflyTests.groundTakeoffAndSustainedHover` collision assertion at tick 20,
position `(602.9926,-41.4008,3.6549)`. All six new elder tests passed within that
same full run. No old assertion or firefly implementation was changed. A focused
replay of the firefly test used the failed run's world seed; its result is recorded
below. The full-suite failure remains part of acceptance and was not erased by
repeated full runs. Production source/resource hashes captured for the full suite
and final build are identical and match the final files.

Focused elder tests cover independent registry/category/size/attributes, texture
resource references, biome entries and actual registered spawn predicates,
Overworld/unrelated-biome rejection, fluid/hazard/leaf floors, head/body clearance,
cliffs, actual strolling on a dry island beside water/drop edges, server dialogue
cooldown, localized message keys, greeting synchronization, vanilla UUID/name/
health NBT round-trip, no family tags, no attack target/damage/trampling/changed
blocks, unchanged shared creature cap and ordinary/name/persistence/Forge-veto
despawning. The first five-test focused pass preceded adding the live movement
fixture; the subsequent six-test focused run and full suite exercised it.

The isolated firefly diagnostic **passed 1/1**, including the 600-tick takeoff/
hover checks, at the same seed. Evidence:
`build/reports/mushroom-vale-stage34-firefly-diagnostic/focused--4853257702107486027-20261011-132159/gametest.log`.
The cause of its full-suite failure remains unresolved; isolated success does
not establish full regression acceptance. The first diagnostic wrapper had a
duplicate AfterBatch name and never started the tests, even though Gradle exited
0. That setup error was corrected with a unique batch. The Stage 3.4 runner now
requires the actual passing GameTest summary to reject this false-success case.
No production code changed during diagnosis, and the full suite was run once.

Built artifact: `build/libs/dudunka-0.27.0-alpha.jar`. Version is unchanged from
the requested baseline. Existing Forge/Java deprecation notices remain.

## Remaining manual acceptance

No graphical runClient or BMC4 session was performed. Manual acceptance remains
for likeness and texture seams, foot contact, animation blending/prop appearance,
normal standing-player face visibility in actual lighting/shaders, natural
encounters with full modpack competitors, bilingual messages, multiplayer pose
synchronization, real save/restart and existing-save upgrade. Automated NBT
round-trips and triangle sightlines do not replace those checks. Exact IntelliJ,
`runClient`, `/summon` and natural inspection steps are in
[TESTING.md](TESTING.md#stage-34-wise-fly-agaric).

## Changed files

- Entity: `src/main/java/io/github/golovinss/dudunka/WiseFlyAgaric.java`.
- Registration: `DudunkaMod.java` and `client/ClientEvents.java` in that package.
- Dedicated client model/renderer: `client/WiseFlyAgaricModel.java`, `client/WiseFlyAgaricRenderer.java`.
- Tests: `test/WiseFlyAgaricTests.java`, `src/test/java/CheckWiseFlyAgaric.java`, `build.gradle`.
- Assets: `assets/dudunka/textures/entity/wise_fly_agaric.png`, additive English/Russian locale keys, one `data/dudunka/worldgen/biome/memory_forest.json` spawn entry.
- Tools: `generate_wise_fly_agaric.py`, `preview_wise_fly_agaric.py`, `run_vale_stage34_checks.py`, `validate_vale_stage34.py`, `validation34/WiseSurvey.java` under `tools/`.
- Documentation: `MUSHROOM-VALE.md`, `TESTING.md`, `VALIDATION.md`, this report, `WISE-FLY-AGARIC-PREVIEW.png` and Stage 3.4 evidence under `docs/validation-data/`.
