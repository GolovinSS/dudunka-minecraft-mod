# Stage 4.3a — first two music landmarks: visual polish

Target: Minecraft 1.20.1 / Forge 47.4.13 / Java 17 / Better MC BMC4 v55.5.
Version and album protocol are unchanged. Existing uncommitted work is retained.

| Status | Result |
| --- | --- |
| Implementation | Implemented, first two landmarks only |
| Cheap static checks | JSON parsing and `git diff --check` passed |
| Build / model verification / GameTests | **NOT RUN** |
| Manual runClient / BMC4 / multiplayer / restart | **NOT RUN** |
| Commit / push / PR / publication | **NOT DONE** |

## Visual changes

- Mushroom Bells: broad turquoise parasol, pointed violet diamond and low oval
  golden bell. Existing voxel cap profiles supply exposed gills, domed crowns
  and one hanging glowing clapper per bell. Low clickable mushroom stems and
  the original listening crystal remain in place. Moss, three tiny crystals,
  four static spore bushes and three small colorful mushrooms frame the clearing.
- Dance: the original full ring now uses blue star, pink bud, golden cup and
  violet wing blooms, with visible glowing centers and light level 5. The four
  cardinal clue flowers have distinct silhouettes/colors. Moss and a small
  quartz inlay frame the original center stone; small glowing mushrooms replace
  the four large cube lamps. Original ring positions, iteration order, note
  pitches, cardinal identities and all quest rules stay unchanged.
- No new particles, ticking blocks, block entities, renderers or dependencies.

## Files changed for this stage

- `src/main/java/io/github/golovinss/dudunka/ValeAdventurePiece.java`: new-only
  visual blueprints and saved revision, terrain-following clearing details.
- `MushroomValeLandmarks.java` in the same directory: one additive block registration.
- `ValeDanceFlower.java` in the same directory: one non-colliding plant block,
  `dudunka:dance_flower`, four `bloom` states and selectable outlines.
- `ValeDanceQuest.java` in the same directory: recognize the new flower alongside
  legacy flowers; retain exact-state/native-start checks and voice ordering.
- `src/main/java/io/github/golovinss/dudunka/test/ValeAdventureTests.java`,
  `ValeMusicTests.java`, `ValeDanceTests.java`: revision persistence, legacy/new
  native reload fixtures, sloped bounds, unchanged ring positions/voices,
  clear target approaches, stem/crown/clapper resolution and wrong-bloom rejection.
- `src/main/resources/assets/dudunka/blockstates/dance_flower.json` and
  `models/block/dance_flower_0.json` through `dance_flower_3.json`: four opaque
  cuboid models, using existing Minecraft/mod textures. **No new textures**.
- `src/main/resources/data/dudunka/loot_tables/blocks/dance_flower.json`: no drops,
  preserving the absence of flower quest rewards. No block item is added.
- `src/main/resources/assets/dudunka/lang/en_us.json`, `ru_ru.json`: block name only.
- `docs/MUSHROOM-VALE.md` and this report.

## Compatibility

Native structure IDs, placement, anchor coordinates, saved music progress,
network/album protocol, audio/ducking and the other three landmarks are unchanged.
New bell/dance pieces persist `VisualRevision: 1`. A piece without that tag keeps
its original blueprint, including vanilla flowers, across saves/reloads; resolving
an interaction never upgrades it. Existing FULL/upgrading chunk protections remain.
New first-two pieces extend their lower bounds by one block to contain moss support
on the existing maximum two-block terrain slope; loaded pieces retain saved bounds.

Only newly generated pieces receive the revised visuals. A candidate start saved
before this update also retains the old blueprint if generated later. There is no
retrofit, migration or repair pass. Do not downgrade a world containing the new
flower block to a mod build that lacks that block. Resource packs can change the
reused texture palettes; manual presentation and BMC4 acceptance remain pending.

## External automated validation — NOT RUN

Run with JDK 17, without simultaneous runClient/other Gradle processes:

```bash
./tools/test.sh build
DUDUNKA_TEST_LANDMARKS=1 ./tools/test.sh gametest
DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest
```

The existing native-generation tests cover real chunk block/start serialization
and finished-chunk protection; the new focused tests cover both visual revisions.
Review reports before treating implementation as automatically validated.

## Manual runClient acceptance — NOT RUN

1. Run `./gradlew --offline runClient` with JDK 17. Use a fresh test world, or
   explore genuinely new chunks in a backed-up test save. Enter Mushroom Vale.
   Locate both sites:
   `/execute in dudunka:mushroom_vale run locate structure dudunka:music_bells`
   and `/execute in dudunka:mushroom_vale run locate structure dudunka:jumper_dance`.
2. Inspect day/night and on uneven terrain: three distinct bell crowns, attached
   clappers, low readable stems, moss/crystals/spores; four distinct cardinal
   flower types and a coherent ring with clear walking space. Check the soft
   flower centers without shaders, and with particles reduced. Check for missing
   model/texture warnings, floating details or buried/obstructed interaction points.
3. In Survival, reach/click each bell stem and the listening crystal. Complete
   turquoise/violet/golden, then listen at the dance stone and tap north/north/
   east/south/south/west. Try other ring flowers and wrong inputs, then replay;
   prerequisites, retries, note voices, rewards and private feedback must match
   Stage 4.2. Test existing old sites too: their geometry must stay unchanged and
   their original tulips/alliums must still work.
4. Save/quit/restart: new and old structures remain unchanged; completed chapters
   stay complete and partial attempts reset as before. Repeat with a second player
   and in BMC4 v55.5, checking muted audio/reduced particles and comfortable lighting.

Stage 4.3b is not started.
