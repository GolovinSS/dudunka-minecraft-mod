# Stage 4.3a — approved Blockbench mesh integration prototype

Stage 4.3a.3 adds a separate [Minecraft-fantasy styled terrain preview](MUSHROOM-BELLS-ART-STYLE.md).
The original source, assets and `place`/`clear` routes below remain available unchanged.

Implementation: an isolated, static in-world visual prototype. Production generation,
saved structure revisions, quest progression, audio, travel and firefly assistance are
unchanged. This stage stops before native landmark replacement.

## Source and local implementation inspected

`docs/blockbench/mushroom_bells_clearing.bbmodel` is the read-only source of truth.
Its SHA256 is `6bb7545660bdbb9090624adedc4ea9997ee8e649331fb6405300657875e50551`.
The actual saved file contains **164 meshes, 23 groups and 18 embedded textures**
(the additional bell-gold texture explains the difference from the estimated 17).
All meshes/groups currently have zero transforms. The texture images are opaque,
solid-color RGBA PNGs; only `concept_magic` requests emissive rendering. Colors,
mesh names and source UUIDs are recorded in the generated manifest.

Inspected the local, dirty working tree, including `AGENTS.md`, the development
workflow, Mushroom Vale/visual-stage documentation, `ValeAdventurePiece`, native
structure/placement commands, landmark registration/shapes, music quest/progress,
clues, hints, ducking and firefly assistance, and existing landmark/music tests.
The recent teleport work was preserved; there is no family `/tp` change here.

## Rendering architecture and tradeoffs

Use **static Forge OBJ block models in a multi-block assembly**. Forge 47.4.13
already includes this loader; no custom renderer, client registration hook, mixin,
GeckoLib, entity, block entity, ticking animation or new dependency is necessary.
The implementation was checked against the locally cached 47.4.13 `ObjLoader`,
`ObjModel` and material-library source and the
[Forge 1.20.1 model-loader documentation](https://docs.minecraftforge.net/en/1.20.1/rendering/modelloaders/).
Ordinary vanilla block-model JSON cannot represent these arbitrary mesh faces.

`tools/generate_mushroom_bells_preview.py` reads the approved source without changing
it. It triangulates the source quads (including concave faces around the carved
diamond opening), clips triangles to world-block cells, emits
local coordinates within `[0,1]`, preserves flat normals and double-sided materials,
and extracts the embedded PNGs byte for byte. Clipping conserves total surface area;
it does not voxelize or reshape the visual model. Seams have automatic face culling
disabled so adjacent partial blocks cannot hide the ornaments. Material `Ka` supplies
Forge's emissive-ambient light only for the original emissive material; ordinary
source colors retain scene lighting. Small real block-light level 7 accents are
attached to existing cells, not separate light blocks. There are no new particles.

One additive registry ID, `dudunka:mushroom_bells_preview_part`, has a `part` property
selecting a generated cell. It has no item, recipe or loot. The common-side layout
is packaged JSON, loaded once; Blockbench is not needed by the running mod. Existing
IDs/models are untouched. Geometry is baked and drawn through normal chunk buffers,
including at neighboring and negative chunk coordinates. The cells contain **347
parts and 30,394 baked triangle faces**, including reversed faces for the source's
double-sided materials. Maximum per cell: **704 faces**. Generated resources total
about **4.8 MB** before jar compression. This is a bounded prototype budget, not a
production performance acceptance result: profile reload/baking, chunk rebuilds and
many simultaneous landmarks before rollout.

Alternatives considered:

| Approach | Tradeoff |
|---|---|
| Vanilla Java block-model cuboids | Familiar and cheap, but would require hand-rebuilding curves, caps, hanging gills and recessed carving; substantially lower fidelity. Keep for simpler future props if appropriate. |
| One oversized OBJ on an anchor block | Few states, but geometry outside its cell gets the wrong chunk bounds, lighting, culling and selection. Avoid for a walkable landmark. |
| Custom `IGeometryLoader` / `BakedModel` | Could pack reusable component data more efficiently, but adds client code, reload/model-data contracts and maintenance. Not justified for this first slice. |
| Entity or block-entity renderer | Allows animation, but adds ticking/render lifecycle and network/save costs that static scenery does not need. |
| Chosen clipped OBJ assembly | Closest direct source fidelity with built-in Forge support and local chunk bounds; more generated files and baked faces. Generated assets, not hand-maintained per-cell models. |

The converter deliberately rejects nonzero group/mesh transforms, hidden elements,
unresolved materials, non-solid/transparent images and unsupported render modes.
Those edits need explicit converter support before regeneration, rather than
silently becoming incorrect assets. It does not alter the approved project.

## Source group mapping

Every group is converted now for visual comparison. The manifest's per-mesh source
UUIDs/group paths and per-cell group lists preserve the reusable object layout.

| Blockbench group | Minecraft asset/layout role |
|---|---|
| `01_Ground_and_Path` | Thin faceted earth/moss base, integrated stepping stones and pebbles; local static support shapes. Production will need terrain integration instead of a floating diorama base. |
| `02_Rose_Bell_Broad_Scalloped` → `Rose_Cap`, `Rose_Stem`, `Rose_Hanging_Gills` | Full rose bell mesh, including curved stem, scalloped lining, sweeping gills, suspension and gold clapper. Future logical identity **violet / note 1**; its approved rose color must be explicitly reconciled with existing violet clue wording before rollout. |
| `03_Teal_Bell_Pointy_Umbrella` → `Teal_Cap`, `Teal_Stem`, `Teal_Hanging_Gills` | Full pointed teal bell. Future logical identity **turquoise / note 0**, regardless of its new visual position. |
| `04_Amber_Bell_Round_Lobed` → `Amber_Cap`, `Amber_Stem`, `Amber_Hanging_Gills` | Full squat amber bell. Future logical identity **golden / note 2**. |
| `05_Central_Carved_Stone` → `Stone_Body`, `Carved_Glyphs`, `Crown_Crystal` | **One** wide carved stone, real recessed diamond/bevel geometry, luminous inset and taller crystal crown. |
| `Ritual_Musical_Ring` under the central stone | Broken ornamental stone/gold inlay and three tone markers. Ground-level decoration, not a second clue pillar. |
| `06_Tiny_Mushrooms` | Five original small mushroom arrangements. |
| `07_Crystal_Clusters` | Three original faceted crystal clusters. |
| `08_Plants_and_Moss` | Original plants, ferns, moss cushions and path-edge moss. |
| `09_Magic_Accents` | Static source motes; no particle emitters. |

## World scale, placement and collision

The source measures **31.37 × 25.12 × 12.4 source units** (width × depth × height).
It is a Generic Model composition, not a layout of 31 world blocks. Using the
ordinary 16-model-units-per-block convention without an explicit scale decision
would instead make a roughly two-block tabletop ornament.

The prototype uses **0.375 world blocks per source unit**, with X/Z offset +0.5
relative to the requested integer origin. This is equivalent to six Minecraft
model units per source unit, but the generated OBJ coordinates are already in
world-block units. There is no further implicit 1/16 conversion.

- Actual mesh bounds relative to origin: X **−5.383…6.380**, Y **−0.338…4.313**,
  Z **−4.105…5.313**.
- Visible size: **11.76 × 9.42 × 4.65 world blocks**.
- Occupied cell envelope: X **−6…6**, Y **−1…4**, Z **−5…5**: **13 × 11 × 6 cells**.
- The current native landmark bounds are X/Z ±6 and, for revision-2 bells,
  Y −3…+5. The new mesh fits those limits on a level site without enlarged roofs
  or spilling outside an owning chunk when its origin is at chunk-center +8.
- Approximate cap diameters are 3–4 blocks. The teal peak reaches 4.31 blocks above
  origin; the central crystal reaches 3.92. The ground surface is around Y +0.26.
- The forward approach to the stone has two blocks of headroom. Low amber gills
  and clappers deliberately read near player eye/hand height; circulation is around
  the mushrooms, not through their stems or central stone.

Ground collision is a 4×4 support-column approximation in each cell, sampled from
the actual ground meshes. Stem and stone-body collision uses conservative clipped
component bounds. Caps, crystal points, gills, ornaments, plants and motes are
currently visual/passable; outlines use cell-local visual bounds. These collision
approximations keep the prototype walkable but are not final production hitboxes.
The ground/base is placed in clear air for controlled comparison. It does not grade,
clear, flatten, repair or automatically fit existing terrain. Terrain-following
placement must be designed and accepted separately before worldgen adoption.

## Gameplay compatibility and the two historical clues

The current `ValeAdventurePiece` creates **two** chiseled-stone targets: the root
`(0,0,0)` and the listening column `(0,0,-3)` with amethyst at `(0,1,-3)`.
`ValeMusicQuest.resolve` maps the root and matching planned cells in that second
column to the **same listening action**, `bell == -1`. They are redundant access
points, not two independent puzzle steps. The demonstrated melody and completion
remain turquoise → violet → golden, indices 0→1→2, pitches .75→1→1.25.

No quest code, interaction map, revision default, structure ID, saved progress,
network/audio format, music ducking, discovery/verification or firefly code was
modified here. Preview parts never create a native `StructureStart` and cannot
complete any chapter or receive native landmark firefly assistance. That isolation
is intentional; this is a playable-world **visual** inspection, not a second quest.

After visual acceptance, a safe production mapping should:

1. Introduce an explicit new native **visual revision**, preserving absent/0, 1 and
   2 exactly. Do not change existing files/models those revisions reference or
   retrofit generated sites. Keep the structure ID, saved `Anchor`, owner chapter
   IDs and sequence stable.
2. Bind both the one stone's carved face/body and crystal to the existing listening
   action (`bell == -1`). Both replay/reset semantics survive without a second
   physical pillar. Do not remove the two old coordinates from legacy plans.
3. Add an explicit new-revision object/cell-to-bell map from source groups: teal=0,
   rose=1, amber=2. Do not infer identity from its new X/Z location or color ordering
   in the outliner. Reconcile rose/violet accessibility text explicitly.
4. Update native root validation (currently requires chiseled stone), exact planned
   clicked-state checks, required interaction subsets, note-effect positions,
   physical discovery/verification and firefly integrity consistently for that
   revision. A missing ornament must not disable the puzzle; a missing required
   interaction anchor must still fail closed. Preserve private audio and ducking.
5. Add saved-revision and both-clues-to-one-action regression coverage alongside
   existing `ValeAdventureTests`, `ValeFantasyVisualTests`, `ValeMusicTests`, audio,
   hint, completion and firefly tests. Existing tests remain intact in this stage.

## Development-world inspection

Use JDK 17. Run validation externally, not alongside runClient:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh build
DUDUNKA_TEST_MUSIC=1 JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
DUDUNKA_TEST_LANDMARKS=1 JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --offline -PmushroomValeDev=true runClient
```

`build/check` includes the new `verifyMushroomBellsPreview` headless resource task.
The new GameTests are in `dudunka_music`. The existing wrapper supplies a fresh
isolated GameTest world. Existing quest/old-layout tests must still pass.

Create a disposable Creative superflat world with cheats. Fly near X=64, Z=77,
around Y=92, outside the preview envelope. All four neighboring chunks must already
be loaded. In that world's chat:

```mcfunction
/dudunka_dev mushroom_vale bells_preview place 64 90 64
/time set day
/time set night
```

The `mushroom_vale` developer command branch is shared with existing previews, but
this isolated visual fixture deliberately works in **any development dimension**.
No travel or family teleport is necessary. Inspect from the positive-Z side, then
orbit/walk the clearing and check all three silhouettes, recessed stone detail,
clappers, color fidelity, daylight/night lighting and paths. Inspect across chunk
boundaries and after a resource reload (F3+T) and save/reload. Check the log for
missing model/material/texture errors. Repeat in BMC4 with shaders off before
evaluating shader-specific differences.

To remove the exact unchanged preview, step/fly outside it and use:

```mcfunction
/dudunka_dev mushroom_vale bells_preview clear 64 90 64
```

Both commands require the existing JVM opt-in, permission 2 and a living,
non-spectator player. They never generate/load chunks to make a site fit. Placement
requires the **entire** 13×11×6 envelope to be dry air without block entities or
living occupants. Clearance inside the gaps is checked as well. Removal requires
every expected part/state still to match and all gaps to remain air; changed player
blocks are preserved by refusing the operation. Failed block writes restore the
cells touched by that attempt. There is no automatic retrofit or cleanup task.

## Expected differences and remaining work

- Visual geometry/colors come from all approved meshes. Quads become flat triangles,
  possibly revealing a diagonal on nonplanar source quads. Cell clipping adds edges
  without changing the surface; Minecraft's lighting can reveal cell seams.
- Source double-sided surfaces remain double-sided, including the closed caps.
  Remove redundant interior/back faces only in a reviewed optimization stage.
- Lighting/shadows use Minecraft chunk lighting, not Blockbench's studio light.
  There is no transparency, refraction, dynamic crystal light or animation.
- Terrain and collision are the explicit prototype approximations described above.
  Tiny clappers, carving bevels and magic accents need ground-level review at this
  scale. Flat source palettes remain flat palettes; no invented production textures.
- Optimize the generated face/state/file budget after profiling. Decide reusable
  mushroom/stone component boundaries, production collision and terrain policies;
  keep the converter/source relationship reproducible.
- Only after visual acceptance should a separate stage implement the native revision,
  single-center gameplay binding, terrain/chunk preflight and firefly/discovery tests.

## Exact implementation files and validation status

Handwritten changes for this stage:

- Modified `build.gradle`: add one resource-verification task/check dependency.
- Modified `src/main/java/io/github/golovinss/dudunka/DudunkaMod.java`: one additive registration call.
- Modified `src/main/java/io/github/golovinss/dudunka/MushroomVale.java`: one additive developer command subtree.
- Added `src/main/java/io/github/golovinss/dudunka/MushroomBellsPreview.java`.
- Added `src/main/java/io/github/golovinss/dudunka/test/MushroomBellsPreviewTests.java`.
- Added `src/test/java/CheckMushroomBellsPreview.java`.
- Added `tools/generate_mushroom_bells_preview.py`.
- Added `tools/test_mushroom_bells_converter.py`.
- Added this document.

The **exact 715 generated resource paths** are listed in the `files` array of
`src/main/resources/data/dudunka/mushroom_bells_preview/layout.json`: 347 model JSONs,
347 OBJ cells, one shared MTL, 18 PNGs, one blockstate JSON and the layout manifest
itself. No existing asset was replaced. The approved `.bbmodel` is unchanged.

Reproduce/check without Gradle:

```bash
python3 tools/generate_mushroom_bells_preview.py        # regenerate owned derivative files only
python3 tools/generate_mushroom_bells_preview.py --check # read-only exact comparison
python3 tools/test_mushroom_bells_converter.py
git diff --check
```

Implementation: **implemented**. Static resource comparison and converter geometry
tests: **PASS** (exact comparison of 715 generated resources and five focused pure
Python tests, including concave carving preservation). `git diff --check`: **PASS**.
Gradle build, Java resource verifier and
GameTests: **NOT RUN**. Minecraft/BMC4 visual, gameplay and performance acceptance:
**NOT RUN**. Production worldgen replacement: **not implemented**. Publication,
version bump, commit and push: **not performed**.
