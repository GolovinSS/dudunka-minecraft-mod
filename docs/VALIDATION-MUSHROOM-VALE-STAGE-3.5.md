# Mushroom Vale Stage 3.4b finish + Stage 3.5 validation — 2026-10-11

Minecraft 1.20.1 / Forge 47.4.13 / Java 17; target Better MC BMC4 v55.5.
Work uses the existing dirty local repository at HEAD `4c3cfeb`. Previous Stage
3.4/3.4a/3.4b work is preserved. The user explicitly authorized local build,
focused/full GameTests and diagnosis for this task. No commits, pushes, resets,
stashes, reversions, PRs or publication were performed.

## Status

- Implementation: complete.
- Automated verification: **PASS** — build/verifiers, focused regressions and final full suite **217/217**, **0 FAIL**.
- Manual Minecraft/BMC4 and two real clients: **NOT RUN**.
- Publication: **NOT PERFORMED**.

## Pufik failure evidence and correction

Reproduced the actual existing movement assertion in a fresh isolated world,
using the registered Pufik cottage blueprint and an unchanged 1700-tick movement
observation. Initial focused report:
`build/reports/mushroom-vale-stage35/20261011-165140-959106/gametest.log`.
Result: **0/1 PASS, 1 FAIL**. The preceding runner setup attempt did not execute
any tests: its generated cleanup hooks collided with original batch names;
unique wrapper batch names corrected that startup issue.

Trace showed path node 0 correctly advanced to node 1; navigation supplied
`(16264.5,161,16006.5)` and movement speed 0.1152. Pufik stopped at approximately
`(16264.5201,161,16007.3400)`, grounded, with `horizontalCollision=true` and zero
horizontal velocity. The real cottage has its north-facing, left-hinged spruce
door at local `(0,1,-2)` and bottom-slab porch at local z=-3/-4. Opening the door
worked, and the goal remained active. Its open panel extends 0.1875 blocks across
the doorway; the .68-wide body at x=.5201 has its left face at .1801, overlapping
the panel by .0074 blocks (a perfectly centered body would overlap by .0275).
The approximately 51 goal ticks were about 102 server ticks, because the goal
uses the default reduced tick frequency; the stuck detector ended navigation.
This was a real collision, not a null path, frozen move controller, bad fixture,
incorrect door coordinate, competing goal or a persistence failure.

Resident movement control and path advancement now use the same free-aperture
center (0.59375 for this hinge), with ordered nodes and a symmetric .2 arrival
tolerance. Collision checks include nearby shapes and actual slab floor height.
Path creation is guarded against unloaded regions. The Pufik dimensions, art,
wild navigation branch, door/blueprint, UUID ledger, persistence NBT and Wise
implementation remain intact. All temporary `PUFIK_*` print statements were
removed. Existing five Pufik tests passed **5/5** after the correction in
`build/reports/mushroom-vale-stage35/20261011-165328-617199/gametest.log`.
Three extra rotated doorway tests now retain the same distance/step/safety and
outside/return requirements.

## Travel determinism and fixture ownership

The safe entry command first looks near `(8,64,8)` and then at heightmap surfaces
within the entry chunk. Its safety checks correctly refuse water, hazards,
obstruction, leaves and colliding bodies. Previous command tests assumed random
terrain would always provide an acceptable destination. Fixed seed alone only
reproduces that assumption; it does not establish a suitable fixture.

Source inspection confirmed Forge's launch uses `Main`'s loaded `worldstem` and
`server.properties` seed when constructing `GameTestServer`. It does not call
the vanilla static `GameTestServer.create` method with its constant seed. Both
runners now explicitly write the same default seed, with deliberate overrides
for focused regression evidence. Command tests prepare a small dry supported
arrival fixture and restore every edited block in `finally`, after restoring
scenario-specific obstructions. The veto/blocked-arrival test obstructs both
near-anchor floors and every generated heightmap candidate. Production travel
code and its read-only landing checks were not changed for testing.
Pufik fixtures restore their blocks, original biome quart data and prior forced
chunk flags through batch cleanup. Failed tests retain diagnostics but still
release players/entities/tickets. The full wrapper also rejects a zero-exit
startup failure without a completed passing suite summary.

## Fluffy Jumper implementation and coverage

Original half-block voxel creature with layered round wool body, all-round
cuboid tufts, expressive dark eyes/glints, pink cheeks, smile and four tiny paws.
The actual reference image was inspected before modeling. Three original pixel
atlases cover pink, lavender and blue. The original baked-model preview is
[FLUFFY-JUMPER-PREVIEW.png](FLUFFY-JUMPER-PREVIEW.png), generated with the existing
software rasterizer approach; it is not a Minecraft screenshot.

Server AI applies bounded impulses, validates the swept body and supported
corridor, rests between movements, performs occasional higher jumps, looks at
players and encourages nearby groups. Movement is vanilla physics. Synchronized
vanilla entity data supports interpolated stretch, landing squash, idle and
cheerful interaction animation. No attacks, taming, breeding, inventory, trading,
family progression, dependencies, mixins or global spawner. Pink Meadows adds
weight 8 / groups 2–4 under the normal creature cap; all earlier mod creature
spawn entries remain unchanged. Vanilla identity/health/name/location and
natural despawning remain in use, with additive bounded `FluffyColor` data.

Seven GameTests cover actual registration/resources, biome predicates and unsafe
surfaces, localized greeting/cooldown and save/reload, repeated hops/landings/rests,
per-tick no-teleport and collision/damage checks, swept-corridor refusal, peaceful
multiplayer/group behavior and actual far-distance despawning/disabled AI.
`verifyFluffyJumper` checks baked anatomy, three opaque atlases, UVs, locales,
client wiring, 300 finite bounded poses and nine exported poses. Existing eight
Wise residency and seven Vale travel/permission/bookmark tests are included in
the focused set, alongside eight Pufik residency tests.

The first combined focused run was **29/30 PASS, 1 FAIL**:
`build/reports/mushroom-vale-stage35/20261011-170121-453357/gametest.log`.
A one-test diagnostic reproduced the new physics test failure in
`build/reports/mushroom-vale-stage35/20261011-170342-578751/gametest.log`:
full health, no fire/water, but `alive=false` after the test itself discarded the
entity at completion while its per-tick assertion was still active. All final
hop/landing/rest/distance assertions had passed before that teardown. Cleanup
moved to `AfterBatch`; no movement or safety assertions/timeouts were relaxed.
The multiplayer test additionally uses vulnerable Survival players and a real
player hit, with a harmless bystander to check non-retaliation.

## Final automated results

Focused regressions: **30/30 PASS, 0 FAIL** in
`build/reports/mushroom-vale-stage35/20261011-170645-910973/gametest.log`:
8 Pufik residency, 8 Wise residency, 7 Jumper and 7 travel/bookmark/permission tests.
The strengthened real-hit/Survival/bystander multiplayer test additionally passed
**1/1, 0 FAIL** in
`build/reports/mushroom-vale-stage35/20261011-170948-857212/gametest.log`.
Alternate-seed travel: **7/7 PASS, 0 FAIL**, seed **42**, in
`build/reports/mushroom-vale-stage35/20261011-171059-597669/gametest.log`.
The same seven scenarios also passed under the default fixed seed in the
combined focused set. The first full wrapper run completed **214/216 PASS, 2 FAIL**:
`build/reports/dudunka-tests/20261011T141212Z-3034760/gametest.log`.
Failures were existing `friendsReallyRestBesideMarusya` (end-of-cycle reservation)
and **Wise** `peacefulEvenWhenSurprised` (block snapshot), not the Spider test
with the same unprefixed name. All Pufik, Jumper and travel scenarios passed.
Focused diagnostic runs of the three live shared-furniture and peaceful social
scenarios passed **4/4** and **5/5**; the intermittent failures did not reproduce
there. Reservation telemetry showed the ordinary cat fixture releasing guest
at tick 383 and host at 503, within the unchanged 560-tick assertion.

Source and saved-world inspection confirmed Forge bypasses vanilla's static
GameTest constructor path: the saved world had `doMobSpawning=true` and
`doWeatherCycle=true`. A GameTestServer-only startup hook now applies vanilla's
intended false/false test rules; population tests retain explicit enable/restore.
A required regression checks these rules. Shared-furniture fixtures seed their
entity random sources and suppress only unrelated idle roaming, matching the
existing welcome fixture pattern; original live movement/friendship/mode and
reservation assertions remain unchanged. Batch hooks clean failed fixtures too.
The Wise social test now owns/restores a supported roofed stone/air room while
retaining the entire block snapshot assertion. No production companion, Wise or
Spider behavior was changed. The corrected focused regression set passed
**5/5, 0 FAIL** in
`build/reports/mushroom-vale-stage35/20261011-172629-916350/gametest.log`.
The final corrected full run, `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh all`,
completed **217/217 PASS, 0 FAIL** in
`build/reports/dudunka-tests/20261011T142737Z-3042197/gametest.log`.
The count increased from 216 to 217 with the test-environment rule regression.
The wrapper exited successfully and recorded both phases as PASS; this rerun
was justified by the observed full-suite failures and focused fixture fixes.
Final build, all existing verifiers and `verifyFluffyJumper`: **PASS** in
`build/reports/dudunka-tests/20261011T142737Z-3042197/build.log`.
The Jumper verifier baked **53 original parts**, three opaque 128×64 atlases,
300 bounded poses and nine preview poses; rest height **0.509375 blocks**.
Packaged JAR inspection also passed: six relevant resources exactly match source,
entity/model/renderer classes exist and no focused wrapper namespace/classes
remain. Artifact: `build/libs/dudunka-0.27.0-alpha.jar`, SHA-256
`7096a31b7839e4333e950e970f11649d7627bb6c11d2185ec89949718c05368e`.
This is a local validation artifact, not a publication.
Final `git diff --check`: **PASS**. Static search found no temporary Pufik
navigation or shared-furniture diagnostic prints.

Initial build and all existing/new verifiers also passed:
`build/reports/dudunka-tests/20261011T140013Z-3028216/build.log`.

## Files changed for this task

This list distinguishes this task's edits from pre-existing uncommitted work.

- `build.gradle`, `tools/test.sh`.
- `src/main/java/io/github/golovinss/dudunka/DudunkaMod.java`, `Pufik.java`, new `FluffyJumper.java`.
- `src/main/java/io/github/golovinss/dudunka/client/ClientEvents.java`, new `FluffyJumperModel.java`, `FluffyJumperRenderer.java`.
- `src/main/java/io/github/golovinss/dudunka/test/MushroomValeTests.java`, `PufikCottageTests.java`, new `FluffyJumperTests.java`, `FamilyTests.java`, `WiseFlyAgaricTests.java` and new `DeterministicTestEnvironment.java`.
- `src/main/resources/assets/dudunka/lang/en_us.json`, `ru_ru.json`.
- `src/main/resources/assets/dudunka/textures/entity/fluffy_jumper_0.png`, `fluffy_jumper_1.png`, `fluffy_jumper_2.png`.
- `src/main/resources/data/dudunka/worldgen/biome/pink_meadows.json`.
- New `src/test/java/CheckFluffyJumper.java`.
- New `tools/run_vale_stage35_checks.py`, `generate_fluffy_jumper.py`, `preview_fluffy_jumper.py`.
- `docs/MUSHROOM-VALE.md`, `docs/TESTING.md`, `docs/VALIDATION.md`, this report and new `docs/FLUFFY-JUMPER-PREVIEW.png`.

## Manual acceptance and limitations

Exact commands and nine acceptance steps are in
[TESTING.md](TESTING.md#stage-35-fluffy-jumpers-and-residencytravel-regressions).
Real Minecraft rendering/audio, BMC4 compatibility, existing-world graphical
acceptance, natural encounter density over exploration and two real clients
remain **NOT RUN**. Automated server tests do not replace these checks.
Jumpers conservatively avoid steps/ledges and may rest in cramped or heavily
uneven terrain. Natural rolls are probabilistic and share the vanilla creature
cap; no population survey or broad density tuning was introduced. External
pushes and mid-flight terrain edits are handled by ordinary Minecraft physics.
Existing empty-after-removal residency reservations remain intentional; cottage
fingerprint/discovery boundaries and save-interruption limitations from Stage
3.4a/3.4b remain unchanged. No later development stage was started.
