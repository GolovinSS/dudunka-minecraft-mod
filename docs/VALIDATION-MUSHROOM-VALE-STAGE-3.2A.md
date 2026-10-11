# Mushroom Vale Stage 3.2a — naturally spawning mixed-color fireflies

Date: 2026-10-11. Baseline: clean `main` at `a0f67bc`.
Minecraft 1.20.1, Forge 47.4.13, Java 17; target Better MC BMC4 v55.5.
Changes remain uncommitted.

## Root cause

Stage 3.2 selected each natural variant independently in `finalizeSpawn`, then
returned `super.finalizeSpawn(...)`'s group data. That gave `NaturalSpawner` no
firefly-specific state to pass to later members. Its palette made golden colors dominant in Pink Meadows at 90%. Glowing Forest
split golden and turquoise but omitted violet; Memory Forest favored violet at
90%. No biome palette guaranteed a visible color difference within a pack.
Manual `/summon` NBT worked because entity NBT loads after command finalization
and persists `ValeVariant`.

## Changes

- Natural groups now request 3–5 at biome weight 2 (from 2–4 at weight 3).
  Weight 2 × mean pack size 4 stays close to the former weight 3 × mean size 3,
  limiting total population growth; vanilla ambient caps still apply.
- `FireflySpawnGroup implements SpawnGroupData` captures the pack palette and
  first color. Natural finalization returns that data to `NaturalSpawner`; the
  second successfully finalized member must choose a different color. Later
  members use the original biome's weighted palette.
- Palettes: Glowing Forest golden/turquoise/violet **55/30/15**; Pink Meadows
  **70/20/10**; Memory Forest **10/20/70**. At least two successful members
  therefore make a mixed group. Existing local flight grouping and safety checks
  are unchanged.
- Only `MobSpawnType.NATURAL` receives coordinated selection. COMMAND/SUMMON
  keeps vanilla command finalization; explicit `ValeVariant` EntityTag values,
  NBT keys, entity/biome IDs, assets, model, animation and emissive layer remain
  unchanged. No family, protocol 9, dependency, worldgen or lighting changes.

## Automated validation

The focused Stage 3.2a run passed **4/4** required GameTests in a fresh seed-0
world. Its multi-seed test exercises 256 group seeds in each biome, alternating
3-, 4- and 5-member groups, asserts two or more colors in every group, and checks
all three weighted palettes. Existing tests cover biome entries and dimension,
time, safety, explicit variant NBT/save-reload, non-hostility and bounded flight.

| Check | Result |
| --- | --- |
| Focused `python3 tools/run_vale_firefly_checks.py focused` | PASS, 4/4; 256 seeds per biome |
| `./gradlew build verifyValeFirefly --offline -Dnet.minecraftforge.gradle.check.certs=false` | PASS, build/reobfuscation and emissive model/resource check |
| Full `python3 tools/run_vale_firefly_checks.py full` | PASS, **173/173** required GameTests; one final fresh seed-0 run |
| `git diff --check` | PASS |

Focused log: `build/reports/mushroom-vale-stage32a/focused.log`.
Full-suite log: `build/reports/mushroom-vale-stage32a/full.log`.
Final build log: `build/reports/mushroom-vale-stage32a/build.log`. The earlier
Stage 3.2 suite predates this change and is not counted here.

## Manual acceptance pending

No graphical client, BMC4, multiplayer or actual old-world upgrade acceptance was
performed. Follow [Stage 3.2a and runClient commands](TESTING.md#stage-32a-mixed-color-natural-groups).
Confirm tiny friendly silhouette/face, all three colors, translucent animated
wings and independent soft pulses with shaders off, then target resource packs;
no terrain illumination; gentle night flight/day slowdown, occasional loose
grouping, water/lava/wall and unloaded-edge behavior; natural biome palettes and
conservative density; save/reload and two-client movement. Check the existing
Pufik/family, album, music, Vale terrain and developer return in a pre-stage save.
Automated success does not establish visual quality or full modpack compatibility.

## Changed files

- Entity/registration: `ValeFirefly.java`, `DudunkaMod.java`.
- Client: `ValeFireflyModel.java`, `ValeFireflyRenderer.java`, `ClientEvents.java`.
- Resources: six `textures/entity/vale_firefly_*.png` files, EN/RU entity names,
  additive ambient entries in the three Vale biome JSONs.
- Validation/tooling: `ValeFireflyTests.java`, `CheckValeFirefly.java`, `build.gradle`,
  `tools/generate_vale_firefly.py`, `tools/run_vale_firefly_checks.py`.
- Documentation: `MUSHROOM-VALE.md`, `TESTING.md`, `VALIDATION.md`, this report and
  `reference/mushroom-vale/README.md`.
