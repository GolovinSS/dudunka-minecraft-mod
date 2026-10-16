# Stage 4.3a.5a — block-built Dance of the Fluffy Jumpers preview

Stage 4.3a.5b update: the user reports this prototype's build and focused tests
passed and has visually approved the clearing. Use the new
[automatic headless NBT export](AUTOMATIC-DANCE-STRUCTURE-EXPORT.md) workflow
instead of operating a Structure Block. The original stage handoff below is
retained as historical context; production worldgen integration remains deferred.

Status: **implemented experimental preview**. Gradle build, all GameTests,
Minecraft visual review and Better MC BMC4 v55.5 acceptance: **NOT RUN**.
Production integration and publication: **not performed**.

Read [development workflow](DEVELOPMENT-WORKFLOW.md) before validation.
Target: Minecraft 1.20.1, Forge 47.4.13, Java 17.

## What is built

An irregular, ground-level enchanted woodland clearing with a partially overgrown
stone dance ring, four low garden alcoves and a small carved-stone center.
The skirt feathers into existing soil; nothing floats and no terrain platform is
generated. Most details sit below eye height: a broken mossy wall, two unequal
block-built mushrooms, a fallen spruce trunk, persistent leaf tufts and two warm
lanterns. The four existing magical blooms provide the strongest directional cues.

The maximum horizontal footprint is **25 × 25** (roughly 21–25 blocks across the
irregular moss edge). The ring lies about 7–8 blocks from the center. The export
box is **25 × 6 × 25**, including one ground layer and reserved headroom; the
tallest placed detail reaches Y + 3. The safety envelope is **27 × 9 × 27**,
including three soil layers and an untouched one-block rim. The origin is the
carved activation block at standing/feet Y, with soil at Y − 1.

| Use | Existing blocks |
| --- | --- |
| Ring, spokes, low pedestal ruins | Mossy cobblestone, mossy stone bricks, stone bricks, stone brick stairs/slabs, mossy cobblestone/stone brick walls |
| Center and inward station markers | Chiseled stone bricks |
| Soil and overgrowth | Moss blocks/carpet, rooted dirt, coarse dirt, grass blocks, podzol |
| Woodland silhouettes | Spruce logs/fence, persistent azalea leaves, mushroom stems, red/brown mushroom blocks |
| Low plants | Ferns, red/brown mushrooms, oxeye daisies |
| Light | Two vanilla lanterns; four existing `dudunka:dance_flower` blooms; existing small golden/violet mushrooms and glowing moss |

No new blocks, models, textures, block entities, dependencies or registry IDs.
The clearing uses actual placed blocks, with explicit stair/log/wall/leaf states.

| Station | Offset from origin | Existing bloom |
| --- | --- | --- |
| NORTH | `(0, 0, -5)` | 0 |
| EAST | `(5, 0, 0)` | 1 |
| SOUTH | `(0, 0, 5)` | 2 |
| WEST | `(-5, 0, 0)` | 3 |

Radial entrances, the inner ring, every side of the center and both radial
approaches to each flower remain walkable. Raised slab fragments decorate the
outer lip; the main routes remain level. The preview deliberately uses a smaller
number of prominent blooms than the production ring for visual evaluation.

## Place in a disposable Creative world

Use **a new disposable test world only**, cheats ON. The final `disposable`
literal is an explicit acknowledgement for each command. It is not an automatic
world detector: the code cannot distinguish player-placed dirt from natural dirt.
There is no force/overwrite option for builds, trees or protected decorations.

Launch the client externally (not performed by Codex):

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew runClient -PmushroomValeDev=true
```

Alternatively add `-Ddudunka.devMushroomVale=true` to the Minecraft JVM, including
the server JVM for multiplayer. The route is absent without this opt-in and
requires a living Creative player with operator permission level 2. Console and
command blocks cannot place it. The preview works in the current dimension;
entering Mushroom Vale is optional.

For the easiest first review, create a default **Superflat Overworld with structure
generation OFF**. Its standard surface has feet Y = −60. Travel to the site and
wait for surrounding chunks to load normally. A reproducible example is:

```mcfunction
/tp @s 145 -60 128
/dudunka_dev mushroom_vale dance_preview place 128 -60 128 disposable
```

General syntax:

```mcfunction
/dudunka_dev mushroom_vale dance_preview place X Y Z disposable
/dudunka_dev mushroom_vale dance_preview restore X Y Z disposable
```

Stand **outside** X/Z ±13 of the chosen origin during either command, and move
other players, mobs and dropped items away. Inspect afterwards at normal player
height, from all four entrances, at midday and evening. In a disposable Vale
world, choose an already loaded, empty, flat area with three natural soil layers;
the prototype refuses slopes, gaps, water, trees, existing structure starts and
chunks referencing neighboring starts. This is intentionally conservative.

Placement never loads/generates chunks, creates native structure starts, locates
landmarks, spawns entities, changes biome eligibility or touches quest data.
Existing generated `dudunka:jumper_dance` sites are never replaced. The actual
production puzzle remains **NORTH → NORTH → EAST → SOUTH → SOUTH → WEST** with
the existing saved progress, rewards and native binding rules. Preview blooms
and stone are visual props: clicking them does not run that puzzle.

## Restore before editing, or preserve the edited world

The command keeps a maximum of eight undo snapshots per dimension **in memory
for this server session only**. An untouched preview can be restored to its
original ground, plants and air with `restore` at the same origin. All chunks
must still be loaded, the safety envelope unoccupied and every cell in the
25 × 6 × 25 edit box unchanged. A failed write attempts rollback of touched cells
and reports if rollback also fails.

Restore refuses any manual edit or natural block change, even in the originally
empty gaps. It has no force mode. Restarting the server/world discards undo;
the actual blocks persist normally. Export edited work, back up the test save,
then retain or discard that disposable world. Do not use destructive `/fill`
cleanup on a valuable save. This limitation is why use is restricted to disposable
worlds even though session restoration is provided.

## Edit and save with a vanilla Structure Block

1. After placement, switch to normal Creative building. Break/replace/add actual
   blocks and refine silhouettes at player height. Use middle-click to copy the
   exact bloom variants and other placed states; existing plants also have their
   usual inventory items. Keep the center and ±5 cardinal stations marked if you
   want to retain the intended composition. Back up the world before extensive
   work. **Java source cannot recover your manual changes.**
2. Obtain a Structure Block with `/give @s minecraft:structure_block`. Place it
   at `(X − 13, Y − 1, Z − 13)`, outside the capture box. For the example above,
   this is `(115, -61, 115)`. Select **SAVE** mode.
3. Set the structure name to `dudunka:previews/jumper_dance_43a5a`, relative
   position **`1 0 1`**, and size **`25 6 25`**. Enable the bounding-box display
   and check all edits lie inside it. Keep entity inclusion **OFF**. The carved
   stone is at template-local `(12, 1, 12)`, and the capture starts at world
   `(X − 12, Y − 1, Z − 12)`. Expand/reposition the box if you build beyond these
   limits; vanilla 1.20.1 allows at most 48 blocks per axis.
4. Press **SAVE**, confirm the success message, then Save and Quit. Copy the
   actual compressed NBT from the world save:
   `generated/dudunka/structures/previews/jumper_dance_43a5a.nbt`.
   With the development client, saves are normally under `run/saves/<world>/`;
   dedicated servers store this under their actual world directory. Saving only
   the Java file or taking screenshots does not preserve your edited build.
5. Verify the copied NBT by loading it with a Structure Block in a second
   disposable world, with the same mod installed. The template contains ordinary
   block states, including existing Dudunka flowers, and air inside the capture
   box. Loading it can replace blocks with air, so use a clear test area. It
   requires this mod for the non-vanilla plants. It does not contain a native
   structure start or saved quest progress.
6. For later review, archive the NBT outside runtime resources, for example
   `docs/structure-prototypes/jumper_dance/jumper_dance_43a5a.nbt`, alongside
   screenshots and a note recording author, Minecraft/Forge/mod versions,
   dimension/seed, capture origin/size, center local coordinate, flower local
   coordinates and intended walkable routes. If unmodified, the local flower
   coordinates are N `(12,1,7)`, E `(17,1,12)`, S `(12,1,17)`, W `(7,1,12)`.
   Preserve the original export as well as edited versions.

**Future approval stage:** inspect palette/properties, block entities/entities,
bounds, terrain adaptation, flower binding, native structure serialization and
save compatibility before considering production use. Do not copy the export
into `src/main/resources/data/dudunka/structures/`, change worldgen or retrofit
saved landmarks in this stage. Structure Blocks save block geometry, not the
mod's `ValeAdventurePiece` or its puzzle semantics.

## Files and external verification

Stage-specific changes (all earlier uncommitted work is preserved):

- `src/main/java/io/github/golovinss/dudunka/ValeDanceClearingPreview.java` — new layout, strict preflight, dev command and session restore.
- `src/main/java/io/github/golovinss/dudunka/MushroomVale.java` — one added child route inside the existing developer gate.
- `src/main/java/io/github/golovinss/dudunka/test/ValeDanceClearingPreviewTests.java` — five focused GameTests.
- `docs/VANILLA-BLOCK-DANCE-CLEARING-PROTOTYPE.md` — this workflow and handoff.

Run externally with JDK 17:

```bash
./tools/test.sh build
DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest
```

The music namespace already discovers the five new tests. They cover bounds,
deterministic translation, palette/stations, player-sized connected access,
plant/light support, terrain/fluid/build/occupant/unloaded-site refusal, missing
JVM opt-in, Creative/operator/player/disposable gates, guarded undo, quest and
progress isolation, and protection of native starts/references. Existing
`ValeDanceTests` cover the unchanged six-tap sequence and production interaction.
Only test fixtures generate chunks and install synthetic native metadata; preview
code does neither. `./tools/test.sh all` is available for subsequent full regression.

Cheap static check: `git diff --check` **PASS**. Build/GameTests: **NOT RUN**.
Manual vanilla/BMC4 visual and saved-NBT acceptance: **NOT RUN**. No commit, push,
publication, version bump or production integration has been performed.
