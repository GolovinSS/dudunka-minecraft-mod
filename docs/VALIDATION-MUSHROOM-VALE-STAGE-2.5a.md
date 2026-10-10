# Mushroom Vale Stage 2.5a validation — 2026-10-10

Minecraft **1.20.1**, Forge **47.4.13**, Java **17.0.20.1**. Target: Better MC
**BMC4 v55.5**. Baseline included all uncommitted Stage 2.2–2.5 work. No reset,
stash, commit, push or PR. Local source and cached mapped Minecraft/Forge source
JARs supplied the implementation evidence; no web research was used.

## Homecoming root cause and correction

There were a real movement defect and a nondeterministic test precondition.

1. The unchanged original test failed in isolation before the fix. In
   `diagnostic1.log` Dudunka remained at `(3.9471,242.5625,1.4683)` toward
   `(3.5,242,1.5)`: feet were supported by a neighboring bed edge. Navigation
   had completed **2/2** nodes, the exact destination was clear, and the only
   running movement goal was priority **5 HomeAtmosphereGoal**. The last
   diagonal step down intersected the supporting bed and `safeRest` rejected
   it forever. Earlier Stage 2.5 evidence had Marusya elevated by the same
   **9/16** bed height; the affected character depends on its approach route.
   Those who reached the floor displayed WAVE/PURR/PEEK while others remained
   CURIOUS. This was an arrival defect, rather than lost HOME mode or dialogue
   stealing the movement goal.
2. `HomeAtmosphereGoal` now attempts a safe horizontal step at the current
   height when that downward diagonal is obstructed. Vanilla gravity supplies
   the drop; vertical velocity is preserved. Each step still passes the same
   collision/fluid/border safety check and uses `move`, not teleportation.
   Speeds **.04/.02**, arrival squared radius **.16**, safe-path checks,
   scene duration, trust reactions, HOME mode, home bindings and friendship
   arrival accounting are retained.
3. Six initial isolated repetitions passed after the movement correction,
   but broader focused checks exposed the fixture's random idle walk during
   its **250-tick** excursion. A cat could be mid-jump at scene selection and
   temporarily have no acceptable path, so it was omitted from that live
   group's reserved spots. A slow snail could acquire a longer route around
   the bed and still be approaching at the checkpoint, only **170 ticks**
   after the owner's return. Trace evidence includes Syusya at
   `(43.0529,242,0.9956)` toward `(42.5,242,1.5)` at tick 460, with a finished
   **4/4** path, and Marusya still eligible but unassigned after a jump.
4. The exact live greeting fixture now removes only its unrelated
   `WaterAvoidingRandomStrollGoal` before enabling AI. Homecoming sampling,
   actual owner departure/return, normal navigation, GoalSelector, all
   home/family/dialogue goals and their priorities remain active. **All
   assertions, the original 540-tick timeout, 420/430-tick checkpoints and
   HOME checks are unchanged.** This controls the starting route rather than
   giving a random excursion route an arbitrary extra deadline. Production
   roaming and scene selection are untouched.
5. Added `welcomeWalksOffLowFurnitureBeforeFinalArrival`: a real bed edge
   supports feet 9/16 above a clear destination, and the test first requires
   the original diagonal to be obstructed. It then requires physical arrival,
   WAVE, HOME and the live welcome flag within 40 ticks. Six live three-member
   repetitions plus rain/sampling regressions pass in the final focused run.

`Homecoming.java`, `HomeAtmosphereScenes.java`, `FamilyBehaviorGoal.java`,
`DudunkaDialogueGoal.java`, companion goal priorities, UUID/NBT formats and the
friendship ledger are byte-identical to the starting tree. Test diagnostics can
be enabled with `focused --trace`; they report actual positions, paths, safety
and running goals. Real homes with complex furniture and unconstrained roaming
still need the manual acceptance below.

## Environment changes

- Ordinary huge red/brown attempts **1/6, 1/8** in all biomes. Forest small
  patches **32** tries, meadow patches **24**, with ordinary red/brown weights
  **4/4**. Vanilla support/survival rules remain intact.
- Medium detailed turquoise/violet/golden mushrooms reuse the existing voxel
  parts and stable `giant_*` feature IDs, with seven-wide layered caps and
  hanging bulbs. Attempts **1/4 turquoise**, **1/4 violet**, **1/6 golden** in
  their eligible biomes. Large nine-wide landmarks retain **1/48, 1/56, 1/96**.
  The former all-air box down to the root rejected ordinary hills beneath the
  broad canopy. Preflight now accepts low dirt/moss slopes in unused cells;
  actual pieces, canopy/headroom, trunks/buildings, fluids, borders and chunk
  checks still reject unsafe sites before any placement. No clearing, filling,
  leveling, terrain density change or old-chunk rewriting.
- Lanterns emit **15** with their approved model unchanged. All three visible
  hanging bulb profiles emit **15**; other cap profiles retain **10/8/9**.
  Landmark parts explicitly obstruct zero light. Actual threaded vanilla
  engine tests in a sky-light-zero enclosure check **14/13** beside the source,
  **10** one block above the walking surface three blocks from a log/fence post,
  and **0** after removal, for lantern/turquoise/violet/golden sources. No
  invisible light blocks, auxiliary light entities or orphan cleanup system.
- Standalone benches now attempt **1/8**, previously **1/40**. Their three real
  seat blocks, ground/clearance preflight, approved appearance and interaction
  are retained. Flower beds, cottage blueprints, small-decoration content and
  their existing probabilities are unchanged. Natural bench regression now
  right-clicks a generated seat, mounts, dismounts and verifies seat cleanup.
  Its first functional extension waited on a non-ticking chunk because the
  test player is not in PlayerList. The fixture now loads the neighborhood,
  temporarily forces only that chunk, waits for actual entity ticking and
  removes its ticket; production worldgen/travel adds no chunk tickets.
  The existing JVM-opt-in, permission-2 player/Vale bench preview is retained.

## Automatic music and /playsound

Cached 1.20.1 `Minecraft.getSituationalMusic()` selects Creative before biome
music. `Musics.CREATIVE` disables replacement and uses **12000–24000** ticks,
so biome JSON alone could never supply reliable automatic Creative playback.
Forge 47.4.13 provides no situational-music selection event.

`client/MushroomValeMusic` uses the **existing native MusicManager slot**:
replace the previous managed track and start the existing Vale SoundEvent once
on entry; during subsequent quiet gaps supply **1200–3600** ticks before
starting if native scheduling has no Vale track. Creative's longer gap allows
this to precede its next vanilla track. Native Survival may start the same
Vale event first; detecting the current slot prevents another start. Running
tracks survive biome and Creative/Survival changes. On exit only a managed
Vale track is stopped, and normal Minecraft scheduling resumes. Pausing and
screens with their own background music suspend starts/countdowns. Vanilla
still owns the streaming sound, Music slider, sound lifecycle and single track
slot. No packets, dependencies, mixins, access transformers or new sounds.

`/playsound ... music` creates an independent command sound. It proves registry
lookup, decoding and category volume routing; it bypasses biome/Creative
selection, managed-track ownership, delays and entry/exit behavior. It can
overlap the automatic slot, so stop command audio before automatic testing.
A fresh entry is the clean playback check. The deterministic policy regression
covers entry/exit, 1200/3600-tick gaps, pause/resume, an uninterrupted 10000-tick
track and native co-scheduling. Audible client/BMC4 validation remains pending.

## Actual generation census

Three clean worlds, seeds **0, 42, -71021**, **768 disjoint FULL chunks per
seed**, **2304** total. The tool chooses remote dry centers in each biome and
scans three 16×16-chunk patches, including their mixed-biome/wet columns. It
uses the real seed, surface, biome, rarity and placement pipeline, without
preview features, injected placements or altered probabilities. Each object
is assigned to its actual biome. Denominators are sampled biome columns / 256,
not an assumption that all chunks in a patch belong to its center biome.
Counts include surface Y=60–115; roots count complete huge/detailed/large
mushrooms, rather than counting their many cap blocks. Small mushrooms are
individual blocks. Sampling is regional, not a guarantee for every world.

The initial Stage 2.5 census, before environment edits, found just one detailed
large violet mushroom and no turquoise/golden large mushrooms in **512**
finished chunks. Ordinary huge red/brown totaled **30**, ordinary small red/
brown **169**. That initial patch selection overlapped; final patches are
separate, so it is not a matched-area before/after experiment.

**Final totals across all three seeds.** T/V/G = turquoise/violet/golden.

| Biome | Chunk equivalents | Small red | Small brown | Huge red | Huge brown | Small T | Small V | Small G |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Glowing | 1503.46 | 1176 | 1175 | 186 | 125 | 2187 | 342 | 338 |
| Memory | 348.40 | 469 | 494 | 51 | 28 | 138 | 930 | 119 |
| Pink | 452.14 | 467 | 435 | 67 | 57 | 115 | 111 | 895 |

| Biome | Medium T | Medium V | Medium G | Large T | Large V | Large G | Bench groups / seat blocks |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Glowing | 157 | 0 | 106 | 15 | 0 | 0 | 52 / 155 |
| Memory | 0 | 59 | 0 | 0 | 3 | 0 | 21 / 64 |
| Pink | 0 | 0 | 46 | 0 | 0 | 3 | 18 / 55 |


Medium mushrooms in their principal biomes total **157 turquoise, 59 violet,
46 golden**; Glowing additionally contains **106 medium golden**. There are
only **21 exceptionally large landmarks** across all 2304 sampled chunks.
Ordinary mushrooms total **4216 small red/brown** and **514 huge red/brown**.
The **91 standalone three-seat bench groups** average one per **25.3 chunks**;
there are **274 individual bench blocks**. Group and block counts are independent
at patch/biome boundaries and include any cottage seating. Seats
straddling a biome boundary are counted individually in their own biome, while
a standalone group is assigned using its center. Preserved planters remain
present (full counts in CSV), rather than substituting for seats.

**Per seed/biome medium, large and bench results, including zeros:**

| Seed | Biome | Chunk equivalents | Medium T/V/G | Large T/V/G | Bench groups / seat blocks |
| ---: | --- | ---: | ---: | ---: | ---: |
| 0 | Glowing | 409.99 | 48/0/33 | 6/0/0 | 18 / 54 |
| 0 | Memory | 190.39 | 0/34/0 | 0/1/0 | 8 / 24 |
| 0 | Pink | 167.61 | 0/0/18 | 0/0/1 | 6 / 18 |
| 42 | Glowing | 505.62 | 59/0/41 | 6/0/0 | 10 / 31 |
| 42 | Memory | 106.55 | 0/16/0 | 0/2/0 | 9 / 27 |
| 42 | Pink | 155.83 | 0/0/16 | 0/0/0 | 3 / 9 |
| -71021 | Glowing | 587.85 | 50/0/32 | 3/0/0 | 24 / 70 |
| -71021 | Memory | 51.45 | 0/9/0 | 0/0/0 | 4 / 13 |
| -71021 | Pink | 128.70 | 0/0/12 | 0/0/2 | 9 / 28 |


[Exact counts for every variant, biome and seed](validation-data/mushroom-vale-stage25a-distribution.csv),
including all small colors, red/brown sizes, denominators and planters.
All intended medium variants and natural standalone benches were found in
all three biome samples on every seed. Zero large violet/golden in some
samples is consistent with their intentionally uncommon size class.

## Automated results and evidence

Evidence directory: `build/reports/mushroom-vale-stage25a/`.

- Resource validation **PASS**: **304 JSON**, **3 biomes**, **115 referenced
  worldgen resources**, **8 bounded decor blueprints**, **28 block/item/loot/
  localization sets**, **81 model references**, acyclic feature ordering.
- Initial original isolated homecoming: **0/1**, confirmed movement failure.
  Initial movement-fix repetitions **6/6**. Broader intermediate focused
  runs **9/16**, then **14/16**, exposing random fixture routes and the new
  natural-seat fixture's chunk readiness. These failures remain in their logs.
- Final focused `focused-stable3.log`: **16/16 PASS**, **1m 4s**, including six
  live homecoming repetitions, deterministic bed-edge arrival, rain/sampling,
  four real light sources/removal, generated bench functionality and the
  existing decoration/preview/seating lifecycle regressions.
- Preliminary survey failed its over-strict requirement that every corner of
  a 256-block biome patch match the center. The tool now accepts mixed patches,
  keeps them disjoint and reports actual per-biome columns. Final surveys
  `survey-measured-{0,42,-71021}.log`: **3/3 PASS**, 2304 sampled FULL chunks.
- Normal `build verifyModels`: **PASS**, exit **0**, **9s**. All **9** character
  model trees bake. `check` includes **verifyValeMusic**, also **PASS**.
- One final full GameTest suite `full-final.log`: **166/166 required tests
  PASS**, including all **17/17 Vale** tests, exit **0**, **1m 35s**. No repeat
  full suite was needed. No final automated failures remain.
- JAR integrity/resource comparison **PASS**: **363/363 JSON/PNG/OGG** match
  source byte-for-byte. Survey/focused Java and template assets are absent
  from the production JAR. Generator rerun is byte-identical. Python syntax
  and `git diff --check` pass.

The starting source/docs/tools checksum inventory contains **538 files**;
none was deleted. All **59 source PNG/OGG** and all **73 PNG/OGG across the
inventory** are byte-identical. Both cottage configured features, approved
lantern/bench models and textures, flower-bed blueprint, dimension/type/noise
settings, companion state/network/registry source, all IDs and album protocol
**9** are retained. Existing local changes from earlier stages remain in the
working tree and ordinary Git diff; that diff is not a Stage 2.5a-only patch.

Artifact: `build/libs/dudunka-0.26.0-alpha.jar`.
SHA-256: `d553b8eae45c0606a19b974a46b1839d73bad58761533c6b03fcd265330345e0`.

## Reproduction and manual acceptance

```bash
python3 tools/validate_vale_resources.py
python3 tools/run_vale_stage25a_checks.py focused
python3 tools/run_vale_stage25a_checks.py survey --seeds 0 42 -71021
python3 tools/run_vale_stage25a_checks.py build
python3 tools/run_vale_stage25a_checks.py full
python3 tools/validate_vale_resources.py --mod-jar build/libs/dudunka-0.26.0-alpha.jar
./gradlew runClient -PmushroomValeDev=true
```

The script selects Java 17, uses cached/offline Gradle, creates unique working
directories and refuses reused worlds. Source/templates for diagnostics are
opt-in and never added to normal source sets. [Developer commands, darkness,
automatic-music and homecoming procedures](TESTING.md#stage-25a-final-environment-fixes-and-homecoming-regression).

Manual **Minecraft and full BMC4 v55.5 remain pending**: actual visual mushroom/
lantern illumination in a dark enclosure and at an Overworld night (Vale fixes
noon), model appearance and shaders/resource packs, natural routes with adult
Syusya, natural bench poses and two-player occupancy, complex home furniture
and unconstrained roaming, audible Creative/Survival music/volume/borders/exit,
old-save loading and save/reload behavior. Automated light and policy tests
establish their numerical/scheduling contracts, not graphical or audible acceptance.
