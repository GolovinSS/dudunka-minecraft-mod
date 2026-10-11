# Mushroom Vale Stage 3.2d — populate the Vale

Target: Minecraft 1.20.1, Forge 47.4.13, Java 17, Better MC BMC4 v55.5.
Started from clean `main` at `65ac290a4754121b07c8dd1c6c3fa6aa7589c23f`;
`git ls-remote origin refs/heads/main` confirmed the same pushed revision.
No commit, push, PR or existing-world chunk regeneration was performed.

Across the three matched seeds, natural cottages increased **0 → 10** (five of
each type), standalone benches **186 → 376**, and lantern posts **21 → 56**.
The census recorded **75 → 130 completed Pufik chunk-generation births**.
Across the three forest routes, mean visible Pufiks increased **0.347 → 0.687**;
all nine route windows recorded **3 → 15 completed runtime NATURAL births**.
Final resource/model/build checks PASS, focused checks **10/10**, and one complete
GameTest suite **182/182**. Graphical Minecraft/BMC4 acceptance remains manual.

## Findings and changes

**Cottages:** the published 1/48 and 1/64 filters were only opportunities.
The old preflight demanded a perfectly flat 11×11 or 15×15 soil square, including
unused corners and the walking halo. Foundation and clearance rejections, rather
than missing registrations or missing blueprints, prevented natural landmarks.
Pufik cottages remain Pink Meadows features; Wise cottages remain Memory Forest
features. Neither cottage was previously configured in Glowing Mushroom Forest.

The actual foundation stays flat beneath every ground-level blueprint block.
Unused exterior soil may rise/drop one block, with dry sturdy support and clear
space above. A level three-wide approach to the half-step porch prevents an
otherwise possible 1.5-block entrance step. No ground is leveled, excavated or
filled. Trees/leaves, structures, fluid, unloaded/unwritable positions, world
borders and height bounds still reject before any write. Only BushBlock plants
at explicitly authored blueprint positions can be replaced. Original blueprints,
rotation, functional doors, furnished interiors and registry IDs are unchanged.

A selected cottage chunk checks its original site and at most 15 additional
positions in the same chunk and biome, with the full preflight for each. The
filters become 1/24 and 1/32. This combines better site acceptance with a bounded
local search, instead of simply flooding the old rejecting validator with attempts.
Developer previews retain their exact requested position and orientation.

**Pufik:** registration, `ON_GROUND` placement, dimension/biome restrictions and
safety checks are working. The earlier Stage 3.1 checks established valid spawn
rules, not completed natural encounters. Local Minecraft sources show runtime
creatures are attempted only every 400 ticks, with Y sampled uniformly from
minimum build height to `WORLD_SURFACE + 1`. The registered heightmap selects
chunk-generation positions; it does not make runtime sampling surface-only.
Safe-floor/cliff checks reject genuinely unsafe sites and remain unchanged.

Vanilla's creature cap is 10 for 289 spawnable chunks. Mooshrooms share it and
have weight 8 with packs 4–8. Generated animals can already exceed the runtime
cap; chunk-generation spawning does not use that cap. Those mooshrooms do not
have Pufik's ordinary distance turnover. Therefore increasing attempts alone
cannot replenish an already full creature population. The matched routes measure
both that competition and real successful births rather than assuming a weight
implies encounters.

Only Pufik's Glowing Forest creature weight changes, **5 → 24**; groups remain
**1–2**, mooshrooms remain weight 8 / groups 4–8, and the creature cap remains 10.
`Pufik.java`, registration, Y sampling, safe ground, AI, visuals, animation,
interaction and vanilla 24/128-block player-distance behavior are unchanged.
Unnamed Pufiks keep ordinary distance/idle despawning; names, explicit persistence
and Forge vetoes remain protected. No custom spawner or permanent NPC population
was introduced. A loaded area whose existing animals fill the cap can still
have no runtime births; new terrain gives the revised creature mix opportunities.

**Props:** the full 5×5 flat halo also rejected most lantern posts. Benches already
required only three supported seats, but rises in the unused halo still rejected
them. Both now tolerate dry exterior steps without changing their actual support
or overwriting that terrain. Bench attempts stay 1/8. Lantern attempts become
1/64 from 1/28 because improved acceptance otherwise raised successful posts too
far in the first probe. Direction markers, flower beds, picnic/cozy clusters and
small mushrooms retain their own rules/configuration. Geometry, textures, lantern
block light and bench riding implementation are unchanged.

## Survey method and interpretation

`tools/run_vale_stage32d_checks.py` uses cached local Forge/Minecraft sources,
Java 17 and fresh isolated GameTest worlds. Test-only source overlays count real
feature calls/returns and Pufik spawn-predicate calls; Forge events count completed
joins and final position checks. Baseline overlays are pinned to published
`65ac290`, including its four placed-feature resources and forest creature list.
Production source files are never temporarily rewritten. A file lock serializes
runs, and helpers/templates/instrumented classes are removed afterward. An
ordinary final build and JAR audit verify that no probe references are packaged.

For each seed, three disjoint 24×24-chunk patches are selected from dry biome
centers before generation. The census finishes **1,728 FULL chunks / 442,368
columns per seed**. Biome boundaries cross patches: area is attributed using
actual biome queries at Y=80, and feature attempts/successes use the origin biome.
Equivalent chunks are columns / 256, not a claim that boundary chunks belong to
only one biome. Each phase covers **5,184 FULL chunks / 1,327,104 columns** across
seeds **0, 42, 2026**, with exactly matched centers and areas.

An **attempt** is a configured feature invocation after its placement filters.
A **success** is a completed all-or-nothing placement. Additional cottage site
checks are counted separately in rejection histograms; they are not additional
landmarks. Baseline `check25` denotes soil-foundation rejection and `check27`
clearance rejection. Final `check39` denotes the dry lower-step support check,
`check40` level support/soil, and `check43` upper clearance. The generated source
overlays preserve the exact check-to-line mapping in each run directory.

Each world then observes three separate dry, 160-block routes, starting in the
named biomes, at least 4,096 blocks apart. A real non-spectator Creative server
player drives normal spawning with view/simulation distance 8, daytime 6,000
held constant, and 2,400 ticks per route. The observer advances 32 blocks every
400 ticks and stands on real soil rather than mushroom caps. No mobs are summoned,
no spawn floors are manufactured, no competitors are removed and no cap is bypassed.
Rules, observer, clock, distances and event listeners are restored after each run.

Each route samples ticks 400–2380 every 20 ticks: **100 snapshots**. Nearby Pufik
population uses a 128-block bounding volume. The visibility proxy requires
**distance <64 blocks, vertical difference ≤8 and player line of sight**. It is a
server proxy, not a rendered-pixel or BMC4/shader measurement. Route labels denote
starting biomes; loaded windows can contain other biomes. Every accepted Pufik
spawn still passes the exact Glowing Forest habitat predicate. A coarse quart
biome label in the join telemetry is not an independent restriction test; census
Pufik births are reported as the habitat-validated total.

Runtime **NATURAL** rule calls are observed after vanilla's ON_GROUND preflight;
they do not count failed earlier Y/floor selections or category attempts blocked
by the cap. **Position checks** occur later, and **births** count newly joined
entities, excluding disk reloads. **CHUNK_GENERATION** births include newly loaded
route terrain and the initial observer column, separately from runtime births.
These counts must not be read as a weight/probability estimate. Runtime spawning
and AI vary between runs even with matched terrain, clocks and routes.

The saved-region inspector reads existing Anvil files with Python's standard
library, verifies FULL census chunks, compares every cottage block/property to
its approved rotated blueprint, and counts saved lantern/bench blocks. It uses
the local 1.20.1 BiomeManager fuzzy zoom when attributing blocks at biome boundaries.
It never launches Minecraft, writes region files or generates chunks.

## Matched generated-world results

Attempts and successes below count configured feature invocations and finished placements.

| Seed | Biome | Columns (chunk equivalents) | Cottage attempts baseline → final | Cottage successes baseline → final | Bench successes baseline → final | Lantern-post successes baseline → final |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 0 | glowing_mushroom_forest | 277,030 (1082.15) | — | — | 51 → 90 | 6 → 8 |
| 0 | pink_meadows | 56,150 (219.34) | 2 → 6 | 0 → 0 | 12 → 26 | 1 → 2 |
| 0 | memory_forest | 109,188 (426.52) | 8 → 14 | 0 → 2 | 20 → 37 | 1 → 4 |
| 42 | glowing_mushroom_forest | 279,711 (1092.62) | — | — | 32 → 71 | 5 → 14 |
| 42 | pink_meadows | 67,653 (264.27) | 2 → 11 | 0 → 5 | 10 → 23 | 2 → 4 |
| 42 | memory_forest | 95,004 (371.11) | 2 → 7 | 0 → 0 | 9 → 18 | 2 → 7 |
| 2026 | glowing_mushroom_forest | 297,639 (1162.65) | — | — | 34 → 73 | 3 → 12 |
| 2026 | pink_meadows | 27,270 (106.52) | 2 → 2 | 0 → 0 | 1 → 5 | 0 → 1 |
| 2026 | memory_forest | 117,459 (458.82) | 8 → 16 | 0 → 3 | 17 → 33 | 1 → 4 |

## Completed Pufik encounters

Each row uses a 2,400-tick / 160-block route and 100 visibility snapshots. `CG` means completed chunk-generation births, including the initial observer column. `N` means completed runtime natural births. Rule attempts are the observed runtime Pufik predicate calls, after vanilla ground preflight.

| Seed | Starting biome | Runtime rule attempts baseline → final | Runtime births baseline → final | CG births baseline → final | Mean visible baseline → final | Peak nearby baseline → final | Peak mooshrooms baseline → final | Cap-full samples baseline → final |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | glowing_mushroom_forest | 3 → 10 | 3 → 10 | 7 → 7 | 0.32 → 0.49 | 6 → 13 | 11 → 8 | 61 → 62 |
| 0 | pink_meadows | 0 → 0 | 0 → 0 | 2 → 5 | 0.2 → 0.33 | 2 → 5 | 15 → 6 | 24 → 47 |
| 0 | memory_forest | 1 → 0 | 0 → 0 | 0 → 2 | 0.0 → 0.0 | 0 → 2 | 2 → 5 | 0 → 0 |
| 42 | glowing_mushroom_forest | 0 → 2 | 0 → 2 | 15 → 28 | 0.16 → 0.82 | 6 → 14 | 23 → 5 | 96 → 80 |
| 42 | pink_meadows | 0 → 0 | 0 → 0 | 2 → 3 | 0.0 → 0.0 | 2 → 3 | 7 → 0 | 77 → 74 |
| 42 | memory_forest | 0 → 0 | 0 → 0 | 1 → 7 | 0.13 → 0.08 | 1 → 4 | 10 → 7 | 58 → 56 |
| 2026 | glowing_mushroom_forest | 0 → 0 | 0 → 0 | 8 → 7 | 0.56 → 0.75 | 6 → 4 | 33 → 18 | 100 → 100 |
| 2026 | pink_meadows | 0 → 0 | 0 → 0 | 1 → 3 | 0.0 → 0.0 | 1 → 2 | 3 → 0 | 94 → 87 |
| 2026 | memory_forest | 0 → 4 | 0 → 3 | 1 → 4 | 0.2 → 0.2 | 1 → 3 | 8 → 8 | 9 → 41 |

## Aggregate acceptance, saved blocks and limits

| Feature | Baseline invocations / successes | Final invocations / successes | Successful invocation rate |
| --- | ---: | ---: | ---: |
| Pufik cottage | 6 / 0 | 19 / 5 | 0% → 26.3% |
| Wise cottage | 18 / 0 | 37 / 5 | 0% → 13.5% |
| Bench | 586 / 186 | 587 / 376 | 31.7% → 64.1% |
| Lantern post | 168 / 21 | 77 / 56 | 12.5% → 72.7% |

The baseline's 24 cottage checks failed **11 foundations / 13 clearance** checks.
Final cottages used **772 local site checks: 762 rejected, 10 successful**.
Most raw positions still cannot safely hold a cottage, and nearby samples are
correlated. The real improvement is **10 finished, intact landmarks**, with
smaller flat support requirements, preserved exterior steps and 17.9% successful
selected chunks overall. The regression fixture directly demonstrates safe
sloped exteriors that the old full-square flat rule rejected. Neither clearing
vegetation wholesale nor flattening hills was used to raise that success rate.

Pufik census predicate calls were **204 → 403**: completed accepted births
**75 → 130**, ground rejections **113 → 233**, and habitat rejections **16 → 40**.
Safety was preserved rather than accepting those rejected positions. Recorded
births equal accepted census predicate calls in each of the six worlds.
Across all nine route windows, runtime predicate calls were **4 → 16**,
position checks **3 → 15**, and completed NATURAL births **3 → 15**; one ground
rejection occurred in each phase. For the three forest-start routes alone,
completed CG births were **30 → 42**, runtime births **3 → 12**, and mean visible
population **0.347 → 0.687** (about **1.98×**). These small samples do not establish
fixed multipliers. Nearby forest Pufik peaks were **6/6/6 → 13/14/4** across
0/42/2026; mooshroom peaks were **11/23/33 → 8/5/18**.

Seed 2026's forest route still had a full creature cap in **100/100 snapshots**
and no runtime births, in either phase. Its naturally generated Pufiks remained
visible, and a separate Memory-start window containing forest habitat completed
three runtime births in the final run. This is evidence of ordinary cap-limited
spawning, not a hidden replacement population. Seed 42's Memory-start route had
slightly lower mean visibility despite more births; zero-cottage seed/biome cases
are retained in the table. New-world generation and exploration do not guarantee
a Pufik or cottage at every position, and an already populated old area may have
no spare creature capacity.

The saved-file audit read **10,368 FULL chunks** across both phases. All **10/10**
final cottages retained every approved block and relevant rotated property:
Pufik cottages **150 blocks each**, Wise cottages **264 each**, including door
halves, furnishings and lanterns. There were no baseline cottages in these areas.
Actual saved bench blocks increased **562 → 1,146** and lantern blocks **31 → 91**.
These include cottage furniture, markers/cozy clusters and boundary-crossing
pieces; they are distinct from the standalone feature-instance counts above.
Their exact per-biome distributions and cottage coordinates/rotations are in
[the saved-world audit](validation-data/mushroom-vale-stage32d-saved-worlds.json).
The final standalone densities remain sparse: about **one bench per 13.8 chunks**
and **one lantern post per 92.6 chunks** over the whole census, plus cottage
furniture. Global navigation connectivity still needs client acceptance.

## Final validation

| Check | Exact result |
| --- | --- |
| `python3 tools/validate_vale_resources.py --mod-jar build/libs/dudunka-0.26.0-alpha.jar` | PASS: 304 JSON resources, 3 biomes, 115 worldgen references, 8 blueprints, 28 block/item/loot/localization sets, 81 model references; 370 JSON/PNG/OGG files byte-match JAR |
| `python3 tools/validate_vale_stage32d.py --jar build/libs/dudunka-0.26.0-alpha.jar` | PASS: 373 stable resource paths; approved assets/blueprints and dimension/terrain data unchanged; original entity registration/Pufik/firefly sources and codecs preserved; JAR payloads match source, with normal mods.toml template expansion; no helper classes, templates or probe references |
| `python3 tools/run_vale_stage32d_checks.py focused` | PASS **10/10**, final porch-access regression included; `focused-final-0-20261011-051325/gametest.log` |
| Baseline surveys, actual seeds 0 / 42 / 2026 | PASS **3/3 worlds**, nine route windows, 5,184 FULL census chunks |
| Final surveys, actual seeds 0 / 42 / 2026 | PASS **3/3 worlds**, nine route windows, 5,184 FULL census chunks |
| Saved-region inspector, six completed worlds | PASS **10,368 FULL chunks**, all 10 final cottages retain complete rotated blueprints |
| `python3 tools/run_vale_stage32d_checks.py build` | PASS: `build`, JAR/reobfuscation, `test`, `verifyModels` (all 9 family layers), `verifyPufik` (45 parts / 960 vertices / approved atlas and posed camera clearance), `verifyValeFirefly`, `verifyValeMusic`; `build-final-default-20261011-052601/gametest.log` |
| One `python3 tools/run_vale_stage32d_checks.py full` | PASS **182/182 required tests**, exit 0; `full-final-0-20261011-052044/gametest.log`; actual automatic GameTest world seed **2678865581740338438** |
| Python validation-tool syntax and `git diff --check` | PASS |

All log paths above are under `build/reports/mushroom-vale-stage32d/`.
The full suite uses its automatic GameTest seed independently of the controlled
survey seeds. The final runner labels unseeded full/build runs `default`.
No unrelated companion test failed in this complete suite. Existing Java and
Gradle deprecation notices remain; no new dependency was added.

Focused coverage includes four rotations and unchanged blueprints; safe exterior
steps and level porch approaches; missing/wet foundations, trees/leaves, excessive
drops and duplicate-offset rejection without partial writes; original preview
permissions and exact-position behavior; functional doors/interiors, lantern
light propagation/removal, seating/dismount lifecycle; entity/category/identity,
dimension/biome restrictions, body/head clearance, hazardous ground, fluids and
cliffs; the shared ten-creature cap; ordinary/named/persistent/Forge-veto despawning;
and saved UUID/name/health with no family progression tags. Resource comparison
adds stable worldgen/entity IDs, unchanged blueprint codecs, dimension/terrain
settings, fireflies, music and approved assets. No new save format or packet is
introduced; album protocol **9** and companion systems remain unchanged.

Early harness compiles exposed a ChunkAccess/LevelChunk type mismatch and missing
wrapper exception declaration; both were fixed before final runs. Exploratory
runs with overlapping/non-dry routes, the initially excessive lantern acceptance,
and the earlier missing porch-approach guard are excluded from the matched tables.
The six definitive production overlays were normalized back to source and checked
against pinned baseline or final source; their hashes are stored in the metrics.
The first JAR comparison also needed to account for Gradle's expected mods.toml
template expansion; the final audit validates the expanded content explicitly.

## Reproducible evidence and manual acceptance

- [Generation CSV](validation-data/mushroom-vale-stage32d-generation.csv): all 18 phase/seed/biome rows, exact columns, attempts and successes.
- [Structured metrics](validation-data/mushroom-vale-stage32d-metrics.json): all six worlds, predicate/placement rejection counters and all 18 route rows.
- [Raw telemetry](validation-data/mushroom-vale-stage32d-telemetry.txt): exact selected-run records and their log paths.
- [Saved-world audit](validation-data/mushroom-vale-stage32d-saved-worlds.json): final block counts and complete natural cottage blueprints.
- [Exact runClient, natural exploration and offline commands](TESTING.md#stage-32d-natural-landmarks-and-pufik-acceptance).

No graphical runClient or BMC4 session was performed. Remaining checks are natural
encounters under ordinary client/render/simulation settings, BMC4 creature-cap
competition, approved visual quality with normal shaders, entry/exit through
naturally generated cottage doors, seating/dismount, Syusya navigation around
natural landmarks/props, two-player behavior and old-save upgrade acceptance.
Existing chunks must remain intact; no region deletion or regeneration is needed.
Firefly implementation and its accepted visual design are preserved.

## Changed files

- `src/main/java/io/github/golovinss/dudunka/MushroomValeDecorFeature.java`
- `src/main/resources/data/dudunka/worldgen/biome/glowing_mushroom_forest.json`
- `src/main/resources/data/dudunka/worldgen/placed_feature/mushroom_vale/pufik_cottage.json`
- `src/main/resources/data/dudunka/worldgen/placed_feature/mushroom_vale/wise_fly_agaric_cottage.json`
- `src/main/resources/data/dudunka/worldgen/placed_feature/mushroom_vale/lantern_post.json`
- `src/main/java/io/github/golovinss/dudunka/test/PufikTests.java`
- `src/main/java/io/github/golovinss/dudunka/test/ValePopulationSafetyTests.java`
- `tools/generate_vale_decor.py`
- `tools/validate_vale_resources.py`
- `tools/validate_vale_stage32d.py`
- `tools/run_vale_stage32d_checks.py`
- `tools/inspect_vale_stage32d_world.py`
- `tools/validation32d/PopulateValeSurvey.java`
- `docs/MUSHROOM-VALE.md`
- `docs/TESTING.md`
- `docs/VALIDATION.md`
- `docs/VALIDATION-MUSHROOM-VALE-STAGE-3.2D.md`
- `docs/validation-data/mushroom-vale-stage32d-generation.csv`
- `docs/validation-data/mushroom-vale-stage32d-metrics.json`
- `docs/validation-data/mushroom-vale-stage32d-saved-worlds.json`
- `docs/validation-data/mushroom-vale-stage32d-telemetry.txt`
