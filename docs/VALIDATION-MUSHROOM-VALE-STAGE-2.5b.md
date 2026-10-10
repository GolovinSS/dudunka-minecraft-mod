# Mushroom Vale Stage 2.5b validation — 2026-10-10

This distribution-only pass raises the small-mushroom patch attempts from 32 to
40 in both forests and from 24 to 30 in Pink Meadows. The red and brown weights
remain 4 each. Magical mushroom weights are unchanged, so their absolute
opportunities also rise with the larger patch attempt counts. Lantern posts
change from 1/32 to 1/28 eligible chunks. Their blueprints, block light, models
and textures are unchanged. Existing chunks keep their generated distribution.

## Measured natural distribution

The three-seed survey used seeds **0, 42, -71021**, 768 disjoint FULL chunks per
seed (2304 total), with three remote dry-centered patches and mixed-biome
columns assigned to the biome actually sampled. It scans surface Y=60–115 and
counts individual ordinary/magical small mushrooms and visible `vale_lantern`
blocks. Lantern totals include those in cottages and cozy clusters, as well as
lantern posts. Sampling is regional and does not guarantee the same rate in
every world.

| Biome | Chunk equivalents | Small red/brown | Small magical | Lantern blocks |
| --- | ---: | ---: | ---: | ---: |
| Glowing Mushroom Forest | 1503.46 | 2878 | 3491 | 10 |
| Memory Forest | 348.40 | 1175 | 1405 | 4 |
| Pink Meadows | 452.14 | 1087 | 1385 | 5 |
| **Total** | **2304.00** | **5140** | **6281** | **19** |

The 5140 ordinary small mushrooms are 21.9% above the Stage 2.5a census total
of 4216 in a separate sample. This is directionally consistent with the 25%
increase in configured patch tries; the old/new regional census is not a
matched-area experiment. Magical small mushrooms remain plentiful in the same
survey (6281 total), including 3491 turquoise/violet/golden blocks in the
Glowing Forest. Nineteen lantern blocks appeared across the sample, or one per
about 121 biome-column chunk equivalents; this is a measured outcome, not the
1/28 attempt rate, since blueprint site checks reject many candidate sites.

Per-seed, per-biome counts, denominators and all surveyed feature categories
are in [the distribution CSV](validation-data/mushroom-vale-stage25b-distribution.csv).

## Validation

- Resource validation: **PASS**, 304 JSON resources, 3 biomes, 115 referenced
  worldgen resources, 8 bounded decor blueprints, and acyclic feature ordering.
- Three-seed natural distribution survey: **3/3 PASS**, 2304 FULL chunks total.
- `build verifyModels`: **PASS**, exit 0.
- The first full GameTest run exposed an outdated assertion requiring permanent
  daylight. The contract test now explicitly requires the approved day/night
  cycle while retaining its height, scale, skylight and bed checks.
- Follow-up full GameTest suite: **166/166 required tests passed**, exit 0
  (`full-daynight-contract-20261010.log`). Resource validation also passed
  after the test and documentation update.
- Manual Minecraft and BMC4 v55.5 visual checks remain required: walk fresh
  routes with adult Syusya through all three biomes, confirm navigable lanes and
  visible magical mushrooms, and inspect the occasional lantern model, warm
  appearance and lighting. Existing chunks do not receive new placements.

Survey and full-suite logs are under `build/reports/mushroom-vale-stage25a/`
with the `stage25b-20261010` prefix. Reproduce with:

```bash
python3 tools/validate_vale_resources.py
python3 tools/run_vale_stage25a_checks.py survey --seeds 0 42 -71021 --label stage25b-20261010
python3 tools/run_vale_stage25a_checks.py build
python3 tools/run_vale_stage25a_checks.py full --label stage25b-20261010
```
