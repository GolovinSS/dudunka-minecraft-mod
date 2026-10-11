# Mushroom Vale Stage 3.3 — friendly magical spider

Date: 2026-10-11. Minecraft **1.20.1**, Forge **47.4.13**, Java **17.0.20.1**.
Target manual modpack: Better MC **BMC4 v55.5**.
Preparation confirmed branch `main`, HEAD
`cc3f3b649810771aecf1e1eab1b5a23b68911bf8` (expected `cc3f3b6`) and a clean
working tree. AGENTS.md, Vale/reference documentation, the actual approved
`friendly_spider.png`, Pufik/firefly source, client registration/model patterns,
GameTests and local cached Forge/Minecraft spawning sources were inspected.
No internet research, dependencies, commits, pushes or PRs were used.

## Delivered behavior and visuals

`dudunka:vale_spider` is an independent `PathfinderMob`/`CREATURE`, 0.8×0.5 blocks,
6 health, 0.14 movement speed, and a two-member maximum spawn cluster. It has no
hostile-spider inheritance, climbing, targeting, melee damage, webs or block
modification. Float support, hazard path penalties and a one-block path fall
limit protect ordinary movement. A bounded local stroll goal checks loaded
columns and safe reachable ground; occasional choices approach nearby players.
Close sprinting can trigger a short normal-speed retreat and shy body dip.
Server social cooldowns avoid constant reactions. Right-click sends a short
localized English/Russian greeting and a synchronized front-leg wave.

The inspected concept is reflected in **55 baked model parts**: a rounded stepped
ivory silhouette, eight short articulated paws, large eyes with white highlights,
rosy cheeks, a smile and a layered tilted terracotta cap. The original **128×64**
pixel atlas is generated deterministically by `tools/generate_vale_spider.py`.
Idle breathing/leg motion, coordinated eight-leg walking, a curious upward look,
a greeting and a shy dip use rigid procedural transforms. Per-entity client
smoothing tracks interpolated nearby player eyes; emote type/start time use
ordinary server entity synchronization and a 32-tick envelope. No geometry scaling,
external animation library or custom packet is involved.

[The offline preview](VALE-SPIDER-PREVIEW.png) samples the real baked geometry/UVs
in nine viewpoints/poses. Its first inspection exposed an overlapping cap edge;
the final geometry separates that surface and rounds the body/brim further.
This preview is an asset review, not a graphical Minecraft screenshot or visual
acceptance in BMC4. Vanilla NBT preserves UUID, name and health; social/emote
state is transient. No ownership, taming, trading, inventory, quest or progression
state was added.

## Spawning contract and measurement method

Only additive creature entries were introduced: **Memory Forest weight 32**, and
**Glowing Mushroom Forest weight 6**, each requesting **1–2**. Existing pigs,
rabbits, sheep, mooshrooms and Pufiks retain their entries exactly; Pink Meadows
receives no spider entry. Both the dimension and biome must match. The registered
`ON_GROUND` predicate checks dry sturdy soil, clear body/head space, safe adjacent
floors without two-block drops, loaded chunks, the border and a surface-height
window. Dirt/mycelium and moss are eligible; roofs, mushroom-cap floors and deep
caves are excluded. Existing `Pufik.safeGround` is reused without changing it.

Local `NaturalSpawner`/`Mob` sources establish why weights/predicates alone are
insufficient: runtime creatures use the normal **400-tick cadence** and vertical
sampling, share a **cap of 10**, and compete with generated animals. Chunk-generation
creature births can exceed that runtime cap. Ordinary spiders can despawn at
vanilla distance/idle thresholds; names, explicit persistence and Forge vetoes
remain protected. No custom population, competitor removal, cap adjustment or
worldgen rewrite was introduced.

`tools/run_vale_stage33_checks.py survey --seeds 0 42 2026` launches isolated fresh
worlds with actual saved seeds **0 / 42 / 2026**, independently verified by reading
`level.dat`. Opt-in seed/survey helpers live outside production source and are
removed from build outputs. Each world observes two disjoint dry routes starting
in the named forest, at least 4,096 blocks apart. Routes are selected from biome/
terrain data before mobs are observed. A real non-spectator server player uses
view/simulation distance **8**, daytime **6000**, and normal engine spawning.
There are no manufactured spawn floors, summoned mobs or removed competitors.

Each route lasts **4,800 ticks**, advances **32 blocks every 400 ticks** for
**352 blocks**, and samples ticks 400–4780 every 20 ticks (**220 snapshots**).
Across six routes this is **28,800 simulation ticks / 1,320 snapshots** (24 minutes
at normal 20 TPS; GameTestServer runs faster in wall time). Join-event UUIDs count
completed births, excluding disk reloads. `CHUNK_GENERATION` and runtime `NATURAL`
births are separate. Later runtime position checks are recorded independently.

The visibility proxy requires **distance <64 blocks**, **vertical difference ≤8**
and **player line of sight**. Nearby population/competitor peaks use a 128-block
bounding volume. State counts/cap occupancy come from vanilla's actual spawning
state, covering its loaded window rather than just the nearby query. The extra
proximity counters show when nearby spiders are hidden; current non-soil feet
samples can include walking/jumping and are not spawn-rule violation counts.
Rules, clocks, distances, listeners and observer are restored after each route,
including batch cleanup on failure. Production AI/world data are never overlaid.

## Completed natural encounters

These are the final runs after entity code was frozen and competitor/proximity
telemetry was added. Starting-biome labels describe routes; loaded windows cross
biome boundaries. Birth attribution by actual biome is preserved in the metrics.

| Seed | Starting biome | CG births | Runtime births | Mean visible | Snapshots with sighting | Peak visible | Peak nearby | Cap-full snapshots |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | Memory Forest | 21 | 0 | 0.150 | 33/220 | 1 | 6 | 210/220 |
| 0 | Glowing Mushroom Forest | 16 | 1 | 0.564 | 96/220 | 2 | 16 | 173/220 |
| 42 | Memory Forest | 31 | 0 | 0.818 | 80/220 | 4 | 10 | 204/220 |
| 42 | Glowing Mushroom Forest | 1 | 0 | 0.000 | 0/220 | 0 | 1 | 140/220 |
| 2026 | Memory Forest | 23 | 5 | 0.136 | 30/220 | 1 | 13 | 120/220 |
| 2026 | Glowing Mushroom Forest | 9 | 1 | 0.000 | 0/220 | 0 | 6 | 152/220 |

Total: **108 completed births = 101 CG + 7 runtime NATURAL**. All seven recorded
runtime position checks completed births. Actual birth-biome attribution is
**73 Memory Forest / 35 Glowing Mushroom Forest**; loaded areas differ, so these
counts do not estimate a biome-normalized density or a fixed weight ratio.

All three primary-habitat routes recorded visible encounters. Their mean visible
population was **0.368**, with at least one sighting in **143/660 snapshots (21.7%)**.
The secondary routes recorded sightings in **96/660 snapshots (14.5%)**, all in
seed 0. Seed 42 and 2026 secondary routes retained completed births and nearby
residents but no clear-sightline samples. They are explicit encounter limitations.
For example, seed 42 secondary had 60 qualifying proximity samples before line
of sight, while the visible count remained zero; seed 2026 had 135.

Competing nearby Pufik peaks were **5/6/2/6/3/6**, and mooshroom peaks
**12/5/11/5/4/5** in table order. Other recorded competitors include rabbits,
sheep, pigs and hive bees; the full species maps are in the metrics. The creature
cap was full in **999/1320 snapshots (75.7%)**. This explains sparse or absent
runtime births even where the registered predicate is valid. Body height,
rolling terrain and dense vegetation further limit visibility. Earlier exploratory
seed-0 Memory observation had zero sightings; its raw line is retained alongside
final telemetry. Natural outcomes and AI vary between otherwise matched runs.

This establishes actual primary-habitat encounters across multiple seeds and
lower-frequency secondary births, with the measured limits above. It does not
promise an encounter at every position or prove rendered visibility in BMC4.
Already populated old areas can have no runtime replenishment; fresh exploration
creates ordinary chunk-generation opportunities without regenerating old chunks.

## Automated validation

All paths below are under `build/reports/mushroom-vale-stage33/`.

| Check | Result / evidence |
| --- | --- |
| Final focused `python3 tools/run_vale_stage33_checks.py focused` | **PASS 5/5**, exit 0; `focused-0-20261011-121750/gametest.log` |
| Final surveys, seeds 0 / 42 / 2026 | **PASS 3/3 worlds**, six routes; `survey-0-20261011-121919`, `survey-42-20261011-122047`, `survey-2026-20261011-122217` |
| One full `python3 tools/run_vale_stage33_checks.py full` | **PASS 187/187 required tests**, exit 0; `full-default-20261011-122418/gametest.log`; saved automatic world seed **-5916914642405697938** |
| `python3 tools/run_vale_stage33_checks.py build` | **PASS**, exit 0; `build-default-20261011-122625/gametest.log`: build, JAR/reobfuscation, test, verifyModels, verifyPufik, verifyValeFirefly, verifyValeMusic, verifyValeSpider |
| `verifyModels` | **PASS**, all nine family model layers |
| `verifyPufik` | **PASS**, 45 parts / 960 vertices, original atlas/locales and posed camera clearance |
| `verifyValeSpider` | **PASS**, 55 parts, eight articulated paws, atlas/UVs/locales/client references, 960 bounded rigid animation poses, all-leg walking, breathing, curious/shy behavior |
| `python3 tools/validate_vale_resources.py --mod-jar build/libs/dudunka-0.26.0-alpha.jar` | **PASS**, 304 JSON resources, 115 worldgen references, 8 blueprints, 81 model references; **371 JSON/PNG/OGG files** byte-match packaged JAR |
| `python3 tools/validate_vale_stage33.py --jar build/libs/dudunka-0.26.0-alpha.jar` | **PASS**, **468 existing source/resource paths** audited against cc3f3b6; permitted changes normalize to baseline; all packaged resources including NBT match; no survey helpers |
| Python tool syntax / `git diff --check` | **PASS** |

Focused GameTests cover independent registry/category/attributes and resource
references; both allowed habitats and excluded Pink Meadows/Overworld; actual
registered placement predicates, hazard/fluid/leaf floors, body/head clearance
and cliffs; localized interaction and wave; UUID/name/health NBT round-trip;
active non-hostile behavior and a sprint-triggered shy reaction; unchanged blocks,
no trampling/attack target/damage attribute; shared creature cap; and ordinary,
named, persistent and Forge-veto despawning. Dedicated-server checks avoid client
class loading. Actual client model baking and renderer/layer wiring are checked
separately by `verifyValeSpider`.

The first focused run failed an incorrect test-fixture assumption that all Vale
biomes contained mooshrooms. The assertion now verifies each biome's actual
existing inhabitants (rabbits in Memory/Pink, mooshrooms in Glowing Forest), with
baseline spawn lists independently audited unchanged. Subsequent focused runs
passed. No full-suite failures occurred and no existing tests were weakened.
Existing Gradle/Java deprecation notices remain. Full-suite captured production
input hashes still match final source/resources.

Compatibility audit confirms unchanged existing entity implementations, all prior
IDs/UUID and save-data code, Companion family, Pufik/firefly behavior, terrain,
dimension/biome identities, decoration/music resources, and album protocol **9**.
Only the new registry/render wiring, locale keys and two biome entries are additive.
The built artifact is `build/libs/dudunka-0.26.0-alpha.jar`.

## Evidence and remaining manual acceptance

- [Structured metrics and log hashes](validation-data/mushroom-vale-stage33-metrics.json).
- [Population CSV](validation-data/mushroom-vale-stage33-population.csv).
- [Raw final telemetry and exploratory zero-sighting record](validation-data/mushroom-vale-stage33-telemetry.txt).
- [Actual baked geometry preview](VALE-SPIDER-PREVIEW.png).
- [Exact runClient, summon, natural exploration and acceptance instructions](TESTING.md#stage-33-friendly-magical-spider).

No graphical runClient or BMC4 session was performed. Remaining manual checks:
visual likeness/scale, texture seams, eye readability from a standing camera,
foot contact and animation smoothness; real natural encounters under normal
render/simulation/shader settings, especially sparse secondary forests; bilingual
interaction, multiplayer emote synchronization; ordinary save/restart and old-save
upgrade; regression acceptance for Pufik, fireflies, companions, album and music.
The automated NBT round-trip is distinct from a full graphical save/restart test.
No regions or old terrain should be deleted or regenerated.

## Changed files

- `src/main/java/io/github/golovinss/dudunka/ValeSpider.java`
- `src/main/java/io/github/golovinss/dudunka/DudunkaMod.java`
- `src/main/java/io/github/golovinss/dudunka/client/ValeSpiderModel.java`
- `src/main/java/io/github/golovinss/dudunka/client/ValeSpiderRenderer.java`
- `src/main/java/io/github/golovinss/dudunka/client/ClientEvents.java`
- `src/main/java/io/github/golovinss/dudunka/test/ValeSpiderTests.java`
- `src/test/java/CheckValeSpider.java`
- `src/main/resources/assets/dudunka/textures/entity/vale_spider.png`
- `src/main/resources/assets/dudunka/lang/en_us.json`
- `src/main/resources/assets/dudunka/lang/ru_ru.json`
- `src/main/resources/data/dudunka/worldgen/biome/memory_forest.json`
- `src/main/resources/data/dudunka/worldgen/biome/glowing_mushroom_forest.json`
- `build.gradle`
- `tools/generate_vale_spider.py`
- `tools/preview_vale_spider.py`
- `tools/run_vale_stage33_checks.py`
- `tools/validate_vale_stage33.py`
- `tools/validation33/SpiderSurvey.java`
- `docs/MUSHROOM-VALE.md`
- `docs/TESTING.md`
- `docs/VALIDATION.md`
- `docs/VALIDATION-MUSHROOM-VALE-STAGE-3.3.md`
- `docs/VALE-SPIDER-PREVIEW.png`
- `docs/validation-data/mushroom-vale-stage33-metrics.json`
- `docs/validation-data/mushroom-vale-stage33-population.csv`
- `docs/validation-data/mushroom-vale-stage33-telemetry.txt`
