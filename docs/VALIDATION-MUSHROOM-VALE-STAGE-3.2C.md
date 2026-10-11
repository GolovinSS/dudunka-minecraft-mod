# Mushroom Vale Stage 3.2c — sustained flight and natural visibility

Target: Minecraft 1.20.1, Forge 47.4.13, Java 17, BMC4 v55.5. No commit or push.

## Confirmed causes

1. **Takeoff was blocked by its own safety margin.** Stage 3.2b correctly allowed
   the first air block above a floor, but `travel` inflated the entire swept body
   downward by 0.12. That volume intersected the floor, even during a tiny upward
   takeoff step. Movement was set to zero and retried indefinitely. Gravity was
   already disabled; adding `FlyingMoveControl` would not fix this, because the
   entity's custom `travel` uses direct velocity and ignores move-control inputs.
2. **Slow steering could never accumulate.** Minecraft `LivingEntity.aiStep`
   clears velocity components below 0.003 before AI. Old daytime acceleration
   was at most `0.018 * 0.15 = 0.0027` per component on the first tick. It could
   therefore be cleared every tick, leaving a stationary entity despite targets.
   Ground-centered homes also selected many low or floor-intersecting targets.
3. **Completed populations and visible groups were different quantities.** With
   flight fixed but density unchanged, correctly simulated seed-0 Memory Forest
   had up to 17 nearby fireflies but only 0.76 visible on average along the route.
   Most of its capped population remained behind the observer/outside 64 blocks.
   Highest-surface-only eligibility also excluded safe ground beneath giant
   mushroom caps. Increasing weight cannot create room in an already full cap.
4. **Vanilla attempts fragmented packs.** NaturalSpawner resets `SpawnGroupData`
   between its three nearby loops and does not replace failed members at the
   same Y. Position failures left many real singleton/pair packs: one intermediate
   Pink Meadows run completed 17 observed packs, 10 of them singletons. This was
   visible-group scarcity even when the total ambient population was full.

The shared ambient cap and bats were inspected. Bats still have weight 10 and
fireflies weight 2; the ambient cap is still 15. Actual generated Vale probes
recorded **zero bats** near the observer, so bat competition was not established
as the cause in these worlds. A focused check deliberately fills the cap with
bats and verifies fireflies do not bypass it. BMC4's other ambient mobs still
require manual acceptance.

## Changes

- Keep the direct, goal-free server flight architecture. Retain steering momentum
  separately from vanilla velocity, disable gravity, and use exact swept-body
  collision checks during movement while retaining clearance at destinations.
- Ground-level/legacy saved homes establish an elevated flight area without
  moving the entity. Loaded-column probes choose normal targets about 1–3 blocks
  above dry safe terrain; arrival refreshes targets. Occasional small circles
  change height as they turn. Daytime movement remains slower and no longer freezes.
- Check fluids, fire, collisions, world border and loaded chunks. Blocked flights
  retry locally, with safe upward motion where available. No teleport, path search,
  chunk request, terrain/light update or new packet is used.
- Keep `AMBIENT`, cap 15, all three biome weights **2**, requested sizes **3–5**,
  daytime refusal, palettes and variant NBT. Natural spawning uses vanilla's
  24-block minimum and a firefly-specific **48-block** maximum. Ordinary unnamed
  residents beyond 48 blocks are released, retaining names, explicit persistence,
  normal idle despawning and Forge despawn vetoes. Global mob caps are unchanged.
- Accept safe understory within 12 blocks below the canopy heightmap, while
  requiring a dry safe floor within four blocks. Deep caves/high air remain excluded.
- Same-biome newborns from nearby vanilla loops in the same server tick can share
  group data up to five members. Every member must independently pass vanilla
  spawning; no extra entities fill failed slots. Flight preferentially gathers
  with its pack. Pack identity is transient; existing `ValeVariant`, UUID, name,
  health and `FlightHomeX/Y/Z` saving stay compatible.

Approved models, six textures, wings, pulse/glow and rendering were not modified.
Pufik, companion identities, family data, worldgen IDs, music and album protocol
**9** are preserved.

## Completed natural-spawn experiment

The new population fixtures run the actual server chunk spawning pipeline on
unmodified generated Vale terrain, not a sampled-position estimate. Each uses
a real non-spectator server player, 8-chunk view/simulation distances, nighttime
18,000 held constant, normal natural spawning and 1,200 server ticks. The observer
moves 16 blocks every 200 ticks, for an 80-block route. Each route starts in the
named biome; the loaded window and route may include biome boundaries, just as
normal exploration does. There are no summoned fireflies or manufactured floors.

Join events count completed natural births and their actual group identity.
Fifty snapshots, ticks 200–1180, measure population within 128 blocks and a
**visibility proxy**: within 48 blocks, within eight vertical blocks, and with
player line of sight. A visible group has at least two visible members sharing
group data. Mixed-group peaks additionally require two visible colors. Airborne
observations have a collision-free body half a block below the firefly. These
are server measurements; they do not measure client pixels/shaders or BMC4 FPS.

GameTestServer's default simulation/view distances are zero. Early diagnostic
probes with age-zero unticked entities were discarded as density evidence. The
runner's generated startup hook changes the otherwise hardcoded GameTest seed
before level creation; merely writing `server.properties` would still use seed 0.
Logs verify actual seeds **0, 42, 2026**. All observer/rule/clock/distance/listener
changes are test-only and restored, including batch cleanup after failure.

### Flight-only baseline, original 128-block density rules

These baseline runs already include the takeoff/slow-steering fix, isolating the
remaining density issue. They use the same terrain seeds, route starts, night and
8-chunk simulation/view conditions. Natural spawning itself remains stochastic;
this is not a guaranteed rate multiplier.

| Seed | Starting biome | Mean visible fireflies | Peak nearby population |
| --- | --- | ---: | ---: |
| 0 | Glowing Forest | 7.80 | 18 |
| 0 | Pink Meadows | 7.88 | 16 |
| 0 | Memory Forest | 0.76 | 17 |
| 42 | Glowing Forest | 2.12 | 18 |
| 42 | Pink Meadows | 5.22 | 21 |
| 42 | Memory Forest | 5.40 | 19 |

Baseline logs: `population-20261011-031541/gametest.log` and
`population-20261011-031701/gametest.log` under
`build/reports/mushroom-vale-stage32/`.

### Final Stage 3.2c population

| Seed | Starting biome | Natural births | Mean visible flies | Mean visible groups | Peak mixed groups | Peak nearby population | Airborne samples |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | Glowing Forest | 44 | 12.38 | 3.06 | 4 | 18 | 617/619 |
| 0 | Pink Meadows | 30 | 7.58 | 1.72 | 5 | 17 | 376/379 |
| 0 | Memory Forest | 39 | 5.48 | 1.54 | 5 | 18 | 258/274 |
| 42 | Glowing Forest | 35 | 11.28 | 2.70 | 4 | 17 | 559/564 |
| 42 | Pink Meadows | 44 | 7.44 | 2.68 | 4 | 17 | 366/372 |
| 42 | Memory Forest | 48 | 10.82 | 3.20 | 5 | 18 | 533/541 |
| 2026 | Glowing Forest | 45 | 13.98 | 4.54 | 6 | 18 | 685/699 |
| 2026 | Pink Meadows | 45 | 9.76 | 3.32 | 5 | 18 | 484/488 |
| 2026 | Memory Forest | 44 | 8.08 | 2.24 | 4 | 19 | 401/404 |

All **9/9** route checks passed. Nearby peaks stayed **17–19**, with ordinary
vanilla pack overshoot around the shared cap of 15. The 30–48 births per route
are turnover over a minute, not simultaneous populations. Every completed group
had at most five members and every group with two or more births contained two
colors. Vanilla safety failures still leave some singleton/pair groups; those
were recorded rather than spawning replacements. Logs include the full completed
pack-size histograms. Visible airborne observations ranged **94.2–99.7%**;
newborn takeoff and terrain changes account for observations near the floor.

Final logs under `build/reports/mushroom-vale-stage32/`:

- Seed 0: `population-20261011-033302/gametest.log`.
- Seed 42: `population-20261011-033411/gametest.log`.
- Seed 2026: `population-20261011-033524/gametest.log`.

Across the six comparable seed-0/42 biome-start cases, mean visible fireflies
increased from **4.86 to 9.16** (about **1.88×**), while peak nearby populations
stayed in the same small range. Across all nine final routes, mean visible packs
was **2.78** and mixed-group peaks were **4–6**. This is measured visibility and
turnover under the stated conditions, not a guaranteed multiplier or an increase
to spawn weight/global caps.

## Final validation

| Check | Result |
| --- | --- |
| `python3 tools/run_vale_firefly_checks.py focused` | PASS **7/7**; final log `focused-20261011-033648/gametest.log` |
| `python3 tools/run_vale_firefly_checks.py population --seed 0` | PASS **3/3** |
| `python3 tools/run_vale_firefly_checks.py population --seed 42` | PASS **3/3**, actual seed verified in telemetry |
| `python3 tools/run_vale_firefly_checks.py population --seed 2026` | PASS **3/3**, actual seed verified in telemetry |
| `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build verifyModels verifyValeFirefly --offline -Dnet.minecraftforge.gradle.check.certs=false` | PASS; build/reobfuscation, `test`, `verifyModels`, `verifyValeFirefly`, `verifyValeMusic` |
| Packaged resources | PASS; all six firefly atlases exactly match source, no generated seed/probe helpers in JAR |
| `git diff --check` | PASS |
| One final `python3 tools/run_vale_firefly_checks.py full` | PASS **179/179 required tests**, exit 0; log `full-20261011-033933/gametest.log` |

The full suite independently repeated the seed-0 population routes: mean visible
fireflies were **11.10 / 3.98 / 7.12** in Glowing / Pink / Memory, nearby peaks
**17 / 17 / 18**, and peak mixed groups **4 / 4 / 4**. This variation reinforces
that the nine-run table is a measured sample rather than a deterministic count.

Focused coverage includes explicit command `ValeVariant` NBT and save/reload,
registration/non-hostility/compatibility, weighted palettes across 256 seeds,
dimension/night/dry-floor restrictions, canopy understory, local spawn range,
400-tick bounded wall/water flight and unloaded-chunk refusal, 600-tick ground
takeoff/vertical movement/daytime hover with a stationary NoAI control, and
named/persistent/Forge-veto despawning plus deliberate bat-cap competition.
The movement tests run on `ServerLevel`; client motion remains ordinary entity
tracking/interpolation. Actual multiplayer client synchronization is manual.

During fixture development, an uncontained water strip flooded the takeoff
fixture at tick 20, and its tick-600 cleanup initially ran before the final
per-tick observer. These were test-fixture failures: water is now contained and
observations stop when cleanup begins. Flight-only fixtures use explicit
persistence to isolate movement from unrelated players/despawning. No earlier
family assertions or deadlines were changed. Existing 24 deprecation warnings
remain in the build.

## Unrelated companion intermittency

The prior `togetherfriendshiprequiresactualsimultaneoussitting` investigation is
kept separately in [its investigation report](VALIDATION-HOME-TOGETHER-INTERMITTENT.md).
No family/scene/friendship implementation was changed for Stage 3.2c. The earlier
catch-up config-race fix is preserved. The final complete run passed both catch-up
tests and `togetherfriendshiprequiresactualsimultaneoussitting`; no unrelated
intermittent failure occurred in this run. One passing run does not establish
that the historical sitting intermittency is resolved.

## Files changed for Stage 3.2c

- `src/main/java/io/github/golovinss/dudunka/ValeFirefly.java` — takeoff, steering,
  hover/circle/pack behavior, safe understory and local natural population turnover.
- `src/main/java/io/github/golovinss/dudunka/test/ValeFireflyTests.java` — focused
  regression coverage and contained/isolated flight fixtures.
- `src/main/java/io/github/golovinss/dudunka/test/ValeFireflyPopulationTests.java`
  — new completed-spawn and moving-observer measurements.
- `tools/run_vale_firefly_checks.py` — focused additions and seeded population mode,
  with generated helpers cleaned outside ordinary builds.
- `docs/MUSHROOM-VALE.md`, `docs/TESTING.md`, `docs/VALIDATION.md` and this report —
  current mechanics, exact manual/automated commands and measured results.

All other existing uncommitted Stage 3.2–3.2b work is retained.

## Manual acceptance

[Exact runClient and natural-observation commands](TESTING.md#stage-32c-natural-flight-and-density-acceptance)
cover all three biomes/seeds, nearby variant counts, a tagged natural firefly's
position/velocity/gravity, save/reload, daytime hover and two-player/BMC4 checks.
No graphical client or BMC4 session was run in this environment. Dense foliage,
ambient-cap competition from other mods and client rendering remain manual
acceptance; no fixed count is promised at every point on an exploration route.
