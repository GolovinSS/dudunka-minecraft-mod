# Mushroom Vale Stage 3.6 — Living Mushroom Vale

Implementation handoff for Minecraft 1.20.1, Forge 47.4.13, Java 17, and BMC4 v55.5. This note records code and test changes only. Automated verification and Survival/BMC4 acceptance are **NOT RUN**.

## Findings and implementation

Earlier evidence separated the encounter bottlenecks. Stage 3.3 recorded 101 chunk-generation births and 7 runtime natural births across six routes; the shared `CREATURE` cap was full in 999/1,320 samples (75.7%). Runtime natural attempts use the 400-tick cycle and broad vertical sampling; placement predicates reject unsafe ground and sampled locations away from the surface. Small silhouettes, rolling ground, foliage, and line of sight further reduce sightings. Chunk-generation births can exist even while later runtime spawning is capped. Stage 3.5 identified pigs, rabbits, and sheep as Pink Meadows competitors; Stage 3.2d and 3.3 also measured mooshrooms and other nearby animals.

All `minecraft:*` entries were removed from the `CREATURE` spawn lists for Glowing Mushroom Forest, Memory Forest, and Pink Meadows. The new lists keep Pufik in Glowing Mushroom Forest; Vale Spider in both forests; Fluffy Jumper in Pink Meadows; and the existing Wise Fly Agaric Memory Forest entry. Magical fireflies remain `AMBIENT`; cottages and all worldgen features are unchanged. Weights/groups are Pufik 40 / 1–2, Vale Spider 44 / 1–2 in Memory Forest and 20 / 1–2 in Glowing Mushroom Forest, and Fluffy Jumper 24 / 2–3 in Pink Meadows.

An independent population assist is necessary for old saves: vanilla animals already present are intentionally preserved, so exclusive biome lists cannot free their occupied shared creature cap. The server-side assist only runs in Mushroom Vale when `doMobSpawning` is true and a non-spectator player has moved at least eight blocks since the prior 400-tick sample. It chooses one explorer and attempts one pack per Vale level per 400 ticks, with at most 12 candidate checks and at most three members in a pack. Candidate chunks must already be loaded. Existing placement predicates, Forge placement/position/finalize events, entity collision, player clearance, and dimension/biome checks apply. A local 48-block budget caps the three species at 4 Pufiks, 4 spiders, 6 jumpers, and 12 total. Existing named and persistent entities are never modified and count toward the local limits. The vanilla cap and categories remain unchanged.

## Coverage added or revised

`ValeWildlifePopulationTests` checks exclusive spawn lists, intended habitats, exact weights/groups, independent population limits, group clamping, a shared cooldown for overlapping players, the Vale-only gate, and `doMobSpawning=false`. Existing entity spawn safety, shared-cap, residency, Wise, and firefly tests remain. Spawn-list expectations in `PufikTests`, `ValeSpiderTests`, and `FluffyJumperTests` now assert the exclusive magical population.

Manual acceptance is documented in [TESTING.md](TESTING.md#stage-36-living-mushroom-vale): Survival exploration at ordinary view/simulation distance, no `/summon`, fresh and existing saves, two overlapping players, disabled mob spawning, and wildlife/residency regressions.

## Status

- Implementation: complete; awaiting external validation.
- Automated verification (`./tools/test.sh all`): **NOT RUN** by Codex, per workflow.
- Manual Minecraft/BMC4 Survival acceptance: **NOT RUN**.
- Publication: not performed.
