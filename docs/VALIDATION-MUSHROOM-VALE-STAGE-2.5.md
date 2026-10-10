# Mushroom Vale Stage 2.5 validation

Environment: Minecraft **1.20.1**, Forge **47.4.13**, Java **17.0.20.1**.
Target modpack: Better MC **BMC4 v55.5**. Starting point included all uncommitted
local Stage 2.2–2.4b changes. No reset, stash, commit, push or PR was performed.
No internet research was used; native scheduling/lighting behavior was checked
against locally cached mapped Minecraft/Forge sources.

## Changes and boundaries

- Reduced all eight giant/landmark placed-feature frequencies and all three
  small-mushroom patch tries; retained vanilla red/brown accents. Stable feature
  IDs remain intact. Three landmark configured features now decode the additive
  bounded `mushroom_vale_landmark` feature, with original voxel cap/stem parts,
  gills, raised spots, hanging glowing spores and original pixel palettes.
- Lanterns were already real `vale_lantern` blocks with emission 14. Explicit
  zero light obstruction preserves their model/texture while proving light
  propagation/removal with the actual threaded vanilla engine. No invisible
  blocks, orphan cleanup system or unrelated lighting changes.
- Verified benches and flower beds are separately wired and have distinct block
  content. Standalone bench support now checks beneath the seats without a flat
  outer floor halo; normal 1/40 rarity remains unchanged. Shared opt-in preview
  supports `decor bench|lantern_post`, with existing permission/player/dimension,
  occupant and blueprint checks. Seat entity/interaction code is untouched.
- Registered `dudunka:music.mushroom_vale_theme`, streamed the existing Vorbis
  OGG and configured all three biome music definitions with delays 1200–3600
  and replacement enabled. Vanilla owns Music volume, scheduling and one
  managed background track. No client music ticks, packets or dependencies.

Both cottage blueprints and the soundtrack were SHA-256 checked against the
starting tree and are byte-identical. Soundtrack SHA-256:
`de50359a8680227764f5fdaa54b9de5d804818a2006e9f96314c83e948ffe2a6`.
Audio is existing stereo Vorbis, 44100 Hz. Cottage architecture, placement,
models and textures were not edited. Terrain/dimension/type/biome IDs, density,
travel/return, companions/dialogue, saved data/UUIDs and album protocol **9**
remain unchanged. Existing local work is retained; the ordinary Git diff also
contains those earlier stages and must not be interpreted as Stage 2.5 alone.

## Changed files for this stage

Java modified:
`DudunkaMod.java`, `MushroomVale.java`, `MushroomValeCottagePreview.java`,
`MushroomValeDecor.java`, `MushroomValeDecorFeature.java`,
`test/MushroomValeTests.java`.
Java added: `MushroomValeLandmarks.java`, `MushroomValeLandmarkFeature.java`,
`MushroomValeLandmarkShapes.java` under `src/main/java/io/github/golovinss/dudunka/`.

Resources modified under `src/main/resources/`:

- All three `data/dudunka/worldgen/biome/*.json`: music effects only.
- `data/dudunka/worldgen/placed_feature/mushroom_vale/`: `giant_red`,
  `giant_brown`, `giant_turquoise`, `giant_violet`, `giant_golden`,
  `turquoise_landmark`, `violet_landmark`, `golden_landmark` JSON: rarity only.
- `data/dudunka/worldgen/configured_feature/mushroom_vale/`: three
  `*_landmark.json` blueprints, plus `glowing_small_mushrooms.json`,
  `memory_small_mushrooms.json`, `pink_small_mushrooms.json`.
- `assets/dudunka/lang/en_us.json` and `ru_ru.json`: four additive block names.

Resources added: `assets/dudunka/sounds.json`; four blockstates, four item
models, four block loot tables and twenty block models for
`turquoise_landmark_cap`, `violet_landmark_cap`, `golden_landmark_cap`,
`vale_landmark_stem`; six 16×16 textures (`{turquoise,violet,golden}_landmark_{cap,glow}.png`).
No existing PNG or OGG was altered.

Tools added: `tools/generate_vale_atmosphere.py`,
`tools/preview_vale_landmarks.py`. Updated: `tools/validate_vale_resources.py`.
Docs updated: `docs/MUSHROOM-VALE.md`, `docs/TESTING.md`, `docs/VALIDATION.md`;
added this report. Evidence/init scripts/offline images are ignored outputs
under `build/reports/mushroom-vale-stage25/`.

## Automated validation

- Resource validation PASS: **304 JSON**, **3 biomes**, **115 worldgen
  references**, **8 decor blueprints**, **28 block/item/loot/localization sets**,
  **81 model references**, consistent feature ordering, bounded unique landmark
  offsets, sparse attempt contracts, distinct bench/flower-bed content, sound
  registration/resource/stream flags and all biome scheduling definitions.
- Normal `build` and `verifyModels`: PASS; all nine character model trees bake.
  `verifyModels` checks character trees, not graphical acceptance of new block
  assets. New cuboid bounds/UVs/texture references are checked by the resource
  validator; Forge startup supplies authoritative feature/biome codec loading.
- Packaging PASS: JAR ZIP integrity and all **363/363 JSON/PNG/OGG** source
  resources byte-match. Focused harness/template are absent from production JAR.
- Focused final run: **7/7 PASS**, **1m 4s**. Covers mushrooms, real lantern
  propagation/removal, native music, naturally generated benches, decor rotation
  and safety, guarded previews, and the existing full seating lifecycle.
  Natural bench found at **(1038,73,1028)** in the first examined finished chunk
  in that fresh random-seed world. Other fresh focused worlds found benches
  after 100, 66 and 11 chunks; no frequency was increased for these tests.
- Single final full-suite run: **164/165 PASS**, all **17/17 Vale tests PASS**,
  exit **1**, **2m 4s** overall. The existing unchanged
  `realExcursionGetsAllThreeWalkingGreetingsWithoutChangingModes` failed; this
  full regression result is **not green**. No second full-suite run was made.
  The added root/canopy/fluid snapshot assertions passed in this final run.
  A natural bench was found at **(1272,78,1276)** after 496 finished chunks in
  this fresh seed; no preview or configured-feature placement supplied it.
- Generator rerun: byte-identical across **449 source/resource files**.
  Both cottage blueprints and OGG preservation hashes PASS. Python syntax and
  `git diff --check` PASS.

Focused failures were investigated before the full run. The old mushroom test
recognized only `*_mushroom_cap`, so assertions now also recognize new landmark
parts and still require complete caps, slender stems, light and clearance.
Root/canopy/fluid rejection additionally checks a complete unchanged snapshot.
The first lantern fixture sampled surrounding underground deepslate: the source
emitted 14 but neighboring solid blocks received zero. The corrected fixture
clears and restores a local air volume, waits for a light-worker POST_UPDATE
barrier (GameTestServer advances ticks without sleeping), and still requires
exact neighboring levels **13/12**, then **0/0** after removal. An intermediate
sequence helper produced a null future after a preceding assertion failed;
explicit staged callbacks now report the actual assertion without masking it.
No production bug was bypassed and no expected light level was weakened.

## Existing full-suite greeting failure

At the unchanged greeting test's checkpoint only Dudunka had greeted. Marusya
was at `(23.941,242.5625,133.834)` toward `(23,242,133)` and Syusya at
`(22.940,242,133.099)` toward `(22,242,133)`; both still ran their welcome scene
in CURIOUS activity and retained HOME mode. `HomeAtmosphereGoal` requires
squared distance <=0.16 to the bottom-center target before selecting PURR/PEEK;
these recorded positions were outside that arrival threshold. The test gives
170 ticks between returning and its greeting checkpoint. This identifies the
failed arrival condition, without asserting a new cause or changing behavior.

The same failure predates this task: Stage 1, 2.4 and 2.4a reports record missed
greetings, including the same Dudunka-only outcome. `FamilyTests.java`,
`HomeAtmosphereGoal.java` and `HomeAtmosphereScenes.java` have no source diff.
An isolated replay invokes the original method with its original 540-tick
budget and assertions: **0/1 PASS**, same Dudunka-only outcome, exit **1**,
**1m 9s**. Marusya again remained elevated at Y=242.5625 and Syusya outside
the arrival threshold. The isolated replay confirms this is not dependent on
concurrent Vale tests, but does not establish the underlying navigation cause.
Evidence: `build/reports/mushroom-vale-stage25/greeting-replay.log`.
No deadline, arrival threshold or assertion was relaxed. Companion behavior is
explicitly outside Stage 2.5 scope, so this existing reliability issue remains
visible for a separate authorized fix.

## Reproduction and remaining acceptance

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
python3 tools/validate_vale_resources.py
./gradlew --offline -I build/reports/mushroom-vale-stage25/full.gradle \
  build verifyModels runGameTestServer -x downloadAssets
python3 tools/validate_vale_resources.py --mod-jar build/libs/dudunka-0.26.0-alpha.jar
python3 tools/preview_vale_landmarks.py
./gradlew runClient -PmushroomValeDev=true
```

The full init script refuses a reused world; change its working directory to an
unused one before reproduction. Normal client use needs no init script. Optional
`python3 tools/generate_vale_atmosphere.py` regenerates only Stage 2.5 overrides;
run after older Vale generators if intentionally regenerating earlier stages.

[Exact new-world mushroom, lantern, bench and soundtrack commands](TESTING.md#stage-25-magical-atmosphere-music-and-environmental-polish).
Manual Minecraft and full BMC4 v55.5 acceptance are **pending**: landmark
silhouettes/pixel textures and rare natural distribution over multiple seeds,
walking routes/companion clearance, roofed real-client lantern lighting and
removal, natural bench versus planter discoverability, actual seating poses and
two-player occupancy/lifecycle, old-save loading, shaders/resource packs and
real soundtrack playback/volume/quiet gaps/border/exit transitions.

Native limitations are documented in MUSHROOM-VALE.md and TESTING.md: Survival
is needed for biome scheduling because Creative music takes priority; entry
replacement can delay up to 30 seconds; initial delay can be shorter than one
minute; screens/underwater/End priorities apply; the Vale track can finish on
exit when the destination's native music does not request replacement. No
claim is made that automated configuration checks establish audible acceptance.
Offline isometric previews use actual new model cuboids/textures but approximate
rendering/lighting; they are not Minecraft screenshots or approval of visuals.

## Final artifact

After the isolated legacy-test investigation, a normal build without either
focused harness passed: `build verifyModels -x downloadAssets`, exit **0**,
**11s**. Final packaging/resource validation again passed **363/363**, and
both temporary harnesses/templates are absent. This separate successful build
does not change the full-suite **164/165** failure result.

Artifact: `build/libs/dudunka-0.26.0-alpha.jar`.
SHA-256: `e26c80bbb3e9bb3c246d04d724eeaf21887f9ca36301e59cdf3673079b9db2ff`.
`git diff --check`: PASS. No commit or push. Manual Minecraft/BMC4 acceptance
and the existing greeting/navigation reliability issue remain open.
