# Mushroom Vale Stage 3.2b validation — natural firefly group frequency

## Root cause

All three biomes already registered fireflies in the `AMBIENT` category at
weight 2, with groups of 3–5. Bats had weight 10. The category's vanilla cap
is 15, shared by bats. The nighttime gate and biome restrictions were working.

The primary bottleneck was the natural position check. Vanilla 1.20.1 samples a
spawn Y uniformly from minimum build height through `WORLD_SURFACE + 1`, then
tries nearby positions at that same Y. Fireflies required Y to be strictly
above the first air block reported by `MOTION_BLOCKING_NO_LEAVES`. In open
meadows, only the single sample above that height could pass. A 0.15-block
downward safety pad also made the first-air position overlap the floor. This
discarded otherwise valid spawn attempts before the existing weighted biome
selection could result in a firefly group.

## Change

Natural fireflies now accept the first air block through four blocks above it.
The entity's exact collision volume must be clear at spawn; flight's padded
clearance checks remain unchanged. In open terrain this allows two vertical
sample positions instead of one, while forest canopy terrain can use the same
bounded near-surface band. Biome spawn weights, group sizes, ambient category,
the shared cap, palettes, daytime refusal, group coordination and flight remain
unchanged. No population cap or forced chunk behavior was added.

## Validation

The focused GameTest runs 50,000 natural-spawner vertical samples for each of
12 deterministic streams in a loaded Vale biome at night. It passes candidate
positions through the real `ValeFirefly.canSpawn` check and rolls the biome's
actual weighted ambient spawn list for accepted positions. It compares the
current first-air rule to the previous strict-above rule with the same sampled
positions and the same selection rolls. This is a controlled estimate of
weighted pack-selection opportunities under NaturalSpawner's vertical sampler;
it isolates this bottleneck and does not model the world-wide ambient cap,
player travel or BMC4's other mods.

The focused run reported 2,540 old-rule versus 5,092 new-rule eligible
positions. With identical rolls against the actual biome-weighted list, it
selected 408 old-rule versus 838 new-rule firefly packs, a **2.054×** rate
increase. The 12 deterministic streams each covered 50,000 sampled heights.

| Check | Result |
| --- | --- |
| `python3 tools/run_vale_firefly_checks.py focused` | PASS, 5/5; 12×50,000 sampled positions; 408→838 weighted firefly packs (2.054×) |
| `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build verifyValeFirefly --offline -Dnet.minecraftforge.gradle.check.certs=false` | PASS, build/reobfuscation; firefly model, six atlases, emissive masks and pulse check passed |
| `python3 tools/run_vale_firefly_checks.py full` | 172/174 required tests passed; two unrelated companion-travel tests failed (details below) |
| `git diff --check` | PASS |

Focused log: `build/reports/mushroom-vale-stage32/focused-20261011-021115/gametest.log`.
Full-suite log: `build/reports/mushroom-vale-stage32/full-20261011-021233/gametest.log`.
Build output: successful; existing 24 deprecation warnings remain.

The full suite failures were `catchuppreservesidentityandavoidsoccupiedplaces`
(`Fixture must provide a safe loaded landing`) and
`followgoalreallycatchesupaftersixstalledseconds` (`Actual goal ticks must catch
up over the unreachable gap`). Both concern existing companion travel code in
`FamilyTests`; neither touches firefly spawning. The focused firefly checks,
including the natural spawn-rate test, passed in the same build. The full suite
was run once, as requested.

Follow-up investigation: [companion catch-up test isolation](VALIDATION-FAMILY-TRAVEL-STAGE-3.2B.md)
identifies the shared file-backed config toggle as the cause and records its
test-only fix and subsequent complete-suite results. The original run above
is retained as historical evidence.

## Manual acceptance still required

Compare groups while exploring equal distances and time spans in fresh regions
of Glowing Mushroom Forest, Pink Meadows and Memory Forest, at night, across
multiple world seeds. Confirm groups remain 3–5 and mixed-color, palettes stay
biome-appropriate, and density does not accumulate. Confirm no daytime or
non-Vale natural spawns. BMC4 v55.5 spawn balance, multiplayer, and visual
acceptance remain manual checks; no client gameplay session was run here.
