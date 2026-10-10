# Mushroom Vale (Грибная долина)

## Approved design

Mushroom Vale is a cozy, magical dimension for Dudunka Family on Minecraft 1.20.1, Forge 47.4.13, and Java 17. The target modpack is Better MC BMC4 v55.5. Its landscapes include glowing mushroom forests, pink meadows, and a mysterious memory forest. Friendly inhabitants are Pufik, a small living mushroom; the Wise Fly Agaric; magical fireflies; and fluffy jumping creatures.

The story, **Restore the Music of the Valley**, unfolds across four chapters. Access opens through cooperation between grown-up Dudunka, Marusya, and Syusya after sufficient family friendship. The experience stays gentle: combat is never mandatory, and failure or missed activities do not punish the player. An original instrumental soundtrack has been prepared separately. Approved visual concepts are artistic references only; they are not production-ready models or textures. Do not assume reference images or audio assets are present in Git.

## Compatibility principles

Git and the implemented source are authoritative for existing behavior and compatibility. Before implementation, inspect the relevant concept, validation, testing, age, entity, networking, and save-data documentation. Preserve existing entity identities and UUIDs, registry IDs, the `dudunka` namespace, save formats, and established gameplay mechanics. Any new dimension, biome, entity, item, block, structure, or sound identifiers must be additive and stable after introduction. Do not change the album protocol unless a network format change requires it and that change is explicitly authorized. Avoid new dependencies; justify any proposed dependency, especially GeckoLib or mixins, before introducing it. Follow the mod's existing Forge registration, server-authoritative behavior, persistence, and client-sync patterns.

## Implementation stages

### 1. Dimension prototype

**Scope:** Add the dimension and minimal dimension type/generator configuration using Forge and vanilla systems. Establish a safe, reachable test entry/exit path for development without final portal progression. Keep the Overworld and existing dimensions unchanged.

**Acceptance:** A dedicated server and client load the dimension without errors; a test route enters and exits safely; world save/reload preserves location and dimension data; existing dimensions and old saves continue to load. Prototype settings and identifiers are documented before they become compatibility commitments.

**Compatibility risks:** Dimension identifiers and generator settings become part of world data and must remain stable. Incorrect spawn/exit handling can strand players or companions. Avoid overwriting vanilla or modpack dimension registrations and avoid forcing chunk loads.

### 2. Environments

**Scope:** Build distinct glowing mushroom forest, pink meadow, and mysterious memory forest biomes, with their own terrain, vegetation, lighting, ambience, and exploration landmarks. Use original, repository-approved assets when available; treat concept art as visual guidance.

**Acceptance:** Each environment generates in new chunks with clear visual identity and navigable terrain; existing blocks and structures are not replaced. Generation respects dimension boundaries and works with the target Forge setup. Existing worlds remain loadable and previously generated chunks are not destructively rewritten.

**Compatibility risks:** Biome, feature, placed-feature, and structure IDs must be stable. Biome modifiers and loot/generation data can conflict with BMC4 worldgen; test with the complete modpack. Lighting and worldgen must not require chunk loading or mutate old terrain.

### 3. Inhabitants

**Scope:** Implement Pufik, the Wise Fly Agaric, magical fireflies, and fluffy jumping creatures as friendly dimension inhabitants. Define their non-hostile behavior, movement, interactions, visual/audio presentation, and any persistence needed for ordinary play.

**Acceptance:** Inhabitants spawn in intended environments, behave safely, synchronize between server and client, and persist across save/reload. Their interactions reinforce the cozy tone and do not attack, steal items, block progress, or impose penalties. Existing Dudunka, Marusya, and Syusya retain their identities, UUID behavior, models, personalities, and mechanics.

**Compatibility risks:** New entity registry IDs and data accessors must be additive and stable. Entity AI, spawn rates, and navigation can affect server performance or conflict with modpack rules. Do not repurpose existing entity types or alter existing entity NBT to implement new inhabitants.

### 4. Family portal

**Scope:** Add a discoverable portal activation that requires grown-up Dudunka, Marusya, and Syusya to cooperate and meet a defined family-friendship threshold. Reuse established friendship and companion state; provide clear, gentle feedback when requirements are unmet. Portal access must not bypass player choice or trap companions.

**Acceptance:** The portal activates only when all three eligible, owned companions and the agreed friendship condition are satisfied. It communicates missing conditions, supports safe travel and return, and remains available after save/reload. No existing companion is replaced, duplicated, forcibly moved, or assigned a new UUID.

**Compatibility risks:** Friendship data and companion ownership are established save mechanics; read them without changing their format or scores. Portal state and travel must be server-authoritative, multiplayer-safe, and dimension-safe. Avoid implicit teleportation of companions, chunk forcing, or conflicts with existing recovery and carrier systems.

### 5. Quests

**Scope:** Implement the four chapters of **Restore the Music of the Valley**. Use exploration, friendly encounters, family cooperation, and restoration of musical elements to advance the story. Integrate the prepared instrumental soundtrack only from assets explicitly included and tracked for the implementation.

**Acceptance:** All four chapters can be completed in order, explain their goals in accessible language, and preserve progress across save/reload. Players can pause or leave and return without losing progress. No chapter requires combat, time-limited attendance, consumable progression items, or punitive failure. Existing requests, keepsakes, memories, and album progress remain intact.

**Compatibility risks:** New quest progress must use additive, version-tolerant saved data and must not reinterpret existing memories or request state. Audio registration and playback must tolerate a missing optional asset during development and avoid interrupting unrelated game audio. Do not presume the separately prepared soundtrack or concept art is committed.

### 6. Final testing

**Scope:** Validate the complete dimension and progression on the required Minecraft/Forge/Java versions, first with focused automated checks and then in a graphical client and full BMC4 v55.5. Include multiplayer and old-world upgrade scenarios.

**Acceptance:** Appropriate Gradle build, model/resource checks, and relevant GameTests pass with results recorded. Manual Minecraft testing confirms all three environments, inhabitants, four chapters, portal requirements, travel and return, save/reload, multiplayer ownership, and gentle failure handling. Full BMC4 acceptance is recorded separately from automated results. Existing IDs, UUIDs, saves, protocol behavior, and established mechanics pass regression checks.

**Compatibility risks:** A successful build or GameTest run does not establish visual quality, client/server GUI behavior, or compatibility with the full modpack. Test actual BMC4 worldgen, dimensions, audio, and multiplayer interactions; document unresolved issues and migration limits before release. Never commit, push, or create a PR without explicit approval.

## Delivery rule

Treat each stage as a reviewable milestone. Do not begin a compatibility-breaking migration or introduce an external dependency without explicit authorization. Keep implementation and manual acceptance status in the project validation and testing documentation when those stages are performed.

## Stage 1 prototype contract

Historical baseline: the flat terrain below describes Stage 1. Fresh worlds now
use the [Stage 2.1 terrain contract](#stage-21-natural-terrain-and-worldgen-architecture).
The dimension type, developer opt-in, and return bookmark format remain applicable.

The prototype introduces two additive datapack registry entries: dimension
`dudunka:mushroom_vale` (`data/dudunka/dimension/mushroom_vale.json`) and dimension type
`dudunka:mushroom_vale` (`data/dudunka/dimension_type/mushroom_vale.json`). Forge loads
these JSON resources on world/server startup through the
[datapack registry system](https://docs.minecraftforge.net/en/1.20.x/datagen/server/datapackregistries/).
No vanilla dimension, world preset,
biome, biome modifier, or existing registry entry is replaced.

Terrain deliberately uses `minecraft:flat` with the existing
`minecraft:mushroom_fields` biome. From Y=-64: one bedrock layer, 124 stone layers,
two dirt layers, and one mycelium layer; the surface is Y=63 and arrival feet are
Y=64. Decoration, lakes, and all structure sets are disabled. There are no custom
biomes, mobs, portal blocks, quests, or soundtrack assets in Stage 1. Vanilla
biome spawning still applies, including mooshrooms. This technical test world
establishes dimension loading and travel before the final landscapes are built.

The separate dimension type has min Y=-64, height/logical height 384, coordinate
scale 1, Overworld sky/effects/infiniburn, a normal day/night cycle, skylight,
no ceiling, normal temperature, zero ambient light, working beds, disabled
respawn anchors, and disabled raids. The Stage 1 prototype used fixed noon; that
setting is no longer used. Monster light limits use vanilla Overworld values
(block light 0, uniform sky light threshold 0–7). The dimension and type IDs are stable
compatibility commitments. Generator settings are now recorded in world data:
later terrain work must account for existing chunks and generator persistence;
it must not silently rewrite generated terrain or assume `/reload` migrates it.

### Developer travel

Dimension registration is always available. The development route is absent
unless the **server JVM** has `-Ddudunka.devMushroomVale=true`. For ForgeGradle
client/server runs, `-PmushroomValeDev=true` supplies that JVM property. GameTest
server runs enable it automatically. The commands require permission level 2,
an executing player, and a living, awake player without a mount or passengers:

```text
/dudunka_dev mushroom_vale enter
/dudunka_dev mushroom_vale return
```

Enter from safe solid ground in any other dimension. Entry checks a small area
around (8,64,8), moves only the player, and saves the departure dimension, block
position, yaw, and pitch per player UUID. Return checks the departure area again;
if it is missing or obstructed, it checks a safe surface in the Overworld spawn
chunk. Positions are centered on blocks, so this is not an exact fractional
coordinate restoration. Unsafe entry, repeated entry, wrong-dimension return,
or a Forge travel veto reports an error. A failed return retains the bookmark.
Fall distance and motion are cleared after successful travel. No blocks are
created, destroyed, or replaced by either command.

Safety requires a sturdy floor, standing-player clearance, an unoccupied landing
box inside the world border, and no nearby fluids, fire, magma, cactus, campfires,
berry bushes, wither roses, powder snow, pointed dripstone, or vanilla portals.
Leaves are excluded as floors. Each search is bounded to one target chunk (up
to one departure chunk plus one fallback chunk for return). Explicit travel can
load/generate its destination normally; there is no background search, persistent
chunk forcing, or custom ticket. If both return areas are unsafe, an operator
must clear a safe landing in the saved departure area or Overworld spawn chunk
and retry. The command does not modify terrain to manufacture a landing.

Bookmarks use the new Overworld SavedData file
`data/dudunka_mushroom_vale_returns.dat`, independent of existing player,
companion, carrier, album, and family NBT. Normal save/reload retains the bookmark;
successful return removes it. Missing/corrupt rows are ignored and use the
Overworld fallback. Vanilla playerdata stores the player's current dimension;
Vale terrain uses `dimensions/dudunka/mushroom_vale/`. Minecraft saves separate
files, so this does not promise an atomic recovery after a crash or a partial
backup restore. Keep the dimension resources installed while a world uses them.

The route never finds, transfers, recreates, or rebinds companions. Their existing
AI, UUIDs, ownership, NBT, registry IDs, and album protocol **9** are untouched.
Existing inventory behavior, including the previously implemented carrier, is
unchanged; test this stage with an empty carrier and leave the family at home.

IntelliJ IDEA steps and manual acceptance are in [TESTING.md](TESTING.md#stage-1-mushroom-vale-in-intellij-idea).
Automated and manual results are tracked in [VALIDATION-MUSHROOM-VALE-STAGE-1.md](VALIDATION-MUSHROOM-VALE-STAGE-1.md).


## Stage 2.1 natural terrain and worldgen architecture

Stage 2.1 replaces the flat generator for **fresh worlds**. The dimension and
its separate type remain `dudunka:mushroom_vale`; the type JSON is unchanged.
Only Vale references the new settings. No vanilla resources, presets, biome
modifiers, or other dimensions are overridden. Java travel never rewrites world
settings or chunks. Dialogue, family behavior, UUIDs, existing save formats, and
album protocol **9** are preserved.

### Resource responsibilities

| Responsibility | Resource under `src/main/resources/data/dudunka/` |
| --- | --- |
| Dimension wiring and explicit biome selection | `dimension/mushroom_vale.json` |
| Sky, time, build limits, beds and travel scale | `dimension_type/mushroom_vale.json` (unchanged) |
| Vanilla noise generator, fluid level and surface materials | `worldgen/noise_settings/mushroom_vale.json` |
| Continuous terrain density | `worldgen/density_function/mushroom_vale/terrain.json` |
| Broad horizontal hill noise | `worldgen/noise/mushroom_vale/hills.json` |
| Temporary vegetation, carvers and spawns | Existing `minecraft:mushroom_fields` biome |
| Future landmarks/structures | Separate future structure/template/structure-set resources; none added here |

The generator is vanilla `minecraft:noise`. A vertical gradient centered on
Y=76 combines with horizontal, low-frequency two-octave noise, clamped to
±0.75. The base solid surface is bounded to approximately Y=52–100; the density
is monotonic vertically, so it cannot create floating terrain, overhangs, or
noise caves. Broad hills and valleys meet water at sea level 63 (water blocks
through Y=62), forming shallow basins, ponds and connected water bodies where
the seed permits. Aquifers and noise ore veins are disabled. There is no claim
that every chunk contains water. Bedrock, stone, dirt, dry mycelium and submerged
gravel are temporary vanilla materials; vanilla biome features decorate them
with small and large mushrooms and other existing vegetation.

**Inherited vanilla generation:** mushroom fields includes cave/canyon carvers,
ores, dungeons, lava lakes, springs, and mooshrooms. These are independent of
the base density; disabling aquifers does not disable biome carvers. Occasional
openings and lava can therefore occur. The broad base terrain provides routes
around local obstacles, but the sampled slope checks do not prove every
finished route is passable. Inspect finished terrain in the client, including
ravines, shorelines and mushroom clusters. Global removal of vanilla biome
features/carvers would also affect other dimensions, so no such edit is made.
Future complete custom biomes can define gentler carvers/features locally.
Vanilla structure eligibility also follows biome tags; there are no new
structure resources or global tag changes. BMC4 can modify this vanilla biome,
its tags and features, so modpack acceptance remains required.

### Adding the approved biomes later

The `minecraft:multi_noise` biome source has an explicit `biomes` list, currently
containing only `minecraft:mushroom_fields` with full climate ranges. It does
not use the Overworld preset. Temperature, humidity (`vegetation`), continents,
erosion and weirdness (`ridges`) have horizontal noise channels; depth has a
vertical gradient. They select biomes separately from the terrain density.

When each biome is complete, add its resource at
`worldgen/biome/glowing_mushroom_forest.json`, `pink_meadows.json`, or
`memory_forest.json`, producing the approved IDs `dudunka:glowing_mushroom_forest`,
`dudunka:pink_meadows`, and `dudunka:memory_forest`. Add only the configured and
placed features actually referenced by those biomes. Replace the temporary
full-range entry with explicit climate regions for the completed biomes in
`dimension/mushroom_vale.json`; avoid leaving a full-range entry that wins ties
against narrower regions. Extend the noise-settings surface rule with biome
conditions when distinct ground materials are ready. Shared rolling terrain
continues to work without Java terrain changes. Introduce structures separately
with explicit biome eligibility once their designs are approved. No final biome,
placeholder, custom feature, structure, generator class, dependency, mixin, or
TerraBlender integration is introduced in Stage 2.1.

### Surface-aware developer travel

Entry reads `MOTION_BLOCKING_NO_LEAVES` at the 14×14 interior columns of the
single chunk containing (8,8), using the existing collision, hazards, fluid,
world-border and standing-clearance checks. Entry Y is no longer fixed to 64.
The bounded search can refuse a completely submerged, obstructed, or unsafe
arrival chunk; it never constructs a platform or searches distant chunks.
Return still checks the saved departure near its recorded Y before its bounded
Overworld spawn fallback. Permission, opt-in, travel veto rollback, bookmark
persistence and companion isolation are unchanged. Safety applies to vanilla
hazards recognized by the existing checker; modded hazards need manual review.

### Existing worlds and migration limits

Back up a complete world before updating. Minecraft can persist the dimension's
generator/biome-source configuration in `level.dat`; replacing the datapack JSON
or running `/reload` does not guarantee an existing Stage 1 world switches from
flat to noise terrain. Previously generated chunks remain exactly as saved.
The implementation neither migrates saved settings nor regenerates chunks.
Even ungenerated chunks in an old world may continue using its saved flat
settings. Worlds that adopt a different generator through external editing can
have visible seams and changed feature behavior; that is outside this stage.
Do not delete Vale region files or edit `level.dat` as an acceptance shortcut.
Use a **new, separate test world created with Stage 2.1 installed** for terrain
acceptance. Test a backup copy of an old world separately for loading, retained
builds/player positions, travel and bookmarks; flat terrain there is compatible
legacy behavior. Keep the dimension/type resources installed in existing saves.

Manual steps: [TESTING.md](TESTING.md#stage-21-mushroom-vale-natural-terrain).
Results: [VALIDATION-MUSHROOM-VALE-STAGE-2.1.md](VALIDATION-MUSHROOM-VALE-STAGE-2.1.md).

## Stage 2.2: three independent biomes

Historical Stage 2.2 decoration baseline; Stage 2.3 below defines current vegetation.

Fresh worlds now select `dudunka:glowing_mushroom_forest`,
`dudunka:pink_meadows` and `dudunka:memory_forest`. Stage 2.1's temporary
`minecraft:mushroom_fields` selection and inherited features are historical.
The dimension/type IDs, density function, hill noise, sea level, aquifers and
travel code are unchanged. There is no Java generator or saved-world migration.

The explicit multi-noise list partitions humidity: -1…-0.25 selects Pink
Meadows, -0.25…0.25 selects Glowing Mushroom Forest, and 0.25…1 selects Memory
Forest. Other axes span -1…1 with offset zero; boundary ties are harmless.
The broad central region makes the mushroom forest the main environment.
Only the vegetation climate channel's horizontal scale changes from 0.25 to
1.0, allowing all environments within ordinary exploration distances without
changing terrain density. Biome selection stays independent of height.

| Biome | Dry ground | Vegetation and ambience |
| --- | --- | --- |
| Glowing Mushroom Forest | Mycelium with moss patches | Vanilla giant red/brown mushrooms, small mushrooms, floor glow lichen, warm pink fog and soft blue sky |
| Pink Meadows | Grass over dirt | Pink petals, meadow flowers, grass, occasional cherry tree (one attempt per three chunks), bright sky and pale pink fog |
| Memory Forest | Podzol over dirt | Four vanilla taiga-tree attempts per chunk, mushrooms, grass, subtle floor glow lichen, muted blue/violet fog and darker foliage |

Submerged surfaces retain gravel; all biomes retain dirt underneath and bedrock
at the bottom. Water basins and rolling hills use the Stage 2.1 terrain contract.
Biome colours tint vanilla grass/foliage, water, sky and fog; they do not change
fog distance or add shaders. The normal day/night cycle also changes sky light.
Glow lichen emits vanilla
block light; mushrooms themselves do not emit light. No textures, music or
new mobs are introduced.

Each independent `worldgen/biome/*.json` explicitly lists features and spawns.
Shared vanilla ores/disks are retained, excluding underwater magma. These
biomes have no carvers, lava lakes/springs, dungeons or hostile spawn lists.
Glowing Mushroom Forest retains vanilla mooshroom weight 8, group size 4–8,
and mycelium suitable for vanilla spawning; moss/lichen patches reduce some
available spawn surface. Meadows/Memory Forest reuse cherry-grove passive
animals. This does not prevent commands, player-built spawners or modpack
rules from introducing entities.

Most features reuse vanilla placed features. Necessary custom placements live
under `worldgen/placed_feature/mushroom_vale/`: sparse cherry trees, moderately
spaced taiga trees, surface moss and surface glow lichen. The single custom
configured feature adapts vanilla multiface growth to allow luminous lichen on
mycelium, moss and podzol. Vanilla glow-lichen placement is underground and
vanilla cherry placement attempts roughly ten trees per chunk; those would
not meet this stage's surface light/open meadow requirements.

Future biomes need their own biome resource, climate list entry and optional
biome surface branch/features. No Java generator rewrite is required. These
resources affect only Vale; there are no global biome modifiers, vanilla
resource overrides, biome/structure tag additions or new dependencies.

Existing chunks are never regenerated. Existing worlds may retain saved biome
sources (including Stage 1 flat or Stage 2.1 mushroom fields), even in new
chunks. `/reload` is not a migration mechanism. Use a **NEW world created with
Stage 2.2 installed** for acceptance; test old-world loading separately on a
backup. Do not remove chunks or edit `level.dat` to force this stage.

Testing: [Stage 2.2 client checklist](TESTING.md#stage-22-three-mushroom-vale-biomes).
Results: [Stage 2.2 validation](VALIDATION-MUSHROOM-VALE-STAGE-2.2.md).

## Stage 2.3: signature vegetation and giant mushroom landmarks

Stage 2.3 builds on the local Stage 2.2 resources. Dimension/type IDs, climate
selection, terrain density, surface rules, water, travel/return, dialogue,
companion identities, saved data and album protocol **9** are unchanged.
Only the three Vale biomes reference the new decoration. There are no vanilla
overrides, global modifiers/tags, dependencies, structures or inhabitants.

### Blocks and assets

`MushroomValeVegetation` adds twelve stable block/item IDs under `dudunka`:
`turquoise_mushroom_cap`, `violet_mushroom_cap`, `golden_mushroom_cap`,
`vale_mushroom_stem`, `glowing_moss`, `cyan_magic_flower`, `pink_magic_flower`,
`violet_memory_flower`, `spore_bush`, `small_turquoise_mushroom`,
`small_violet_mushroom`, and `small_golden_mushroom`.
The existing deferred registers and Family creative tab expose these blocks.
They have original 16×16 pixel textures, block/item models, self-drop loot and
RU/EN names. `tools/generate_vale_vegetation.py` reproduces the checked-in
resources with the existing Python/Pillow offline toolchain. The checked-in
resources remain authoritative; running this tool intentionally rewrites the
Stage 2.3 biome decoration/palettes and supporting tree frequency.

Caps/stems reuse `HugeMushroomBlock` face states, with a cream/lilac stem and
separate gill texture. Cap light is turquoise **10**, violet **6**, golden **9**;
stem light is zero. Plants emit **3–6** block light. Light is vanilla lighting,
without shader emissive maps or bloom. Check glow in daylight and at night.
Ground plants have **no collision** and allow land pathfinding. Moss is a
one-pixel overlay; the flowers, bushes and small mushrooms use cutout crossed
planes. They survive on dirt-tag blocks (including mycelium/podzol/grass),
moss blocks and farmland, and break when support disappears. They do not grow,
spread, damage, slow, feed, provide quests or play music. Creative/manual
placement outside Vale is possible; natural placement is scoped to Vale.

### Data-driven feature architecture

Sixteen new configured/placed-feature pairs live under
`worldgen/{configured_feature,placed_feature}/mushroom_vale/`:

| Feature IDs (prefix `dudunka:mushroom_vale/`) | Codec and intended role |
| --- | --- |
| `giant_red`, `giant_brown` | Vanilla red/brown huge-mushroom codecs and blocks; red radius 2, brown radius 3 |
| `giant_turquoise`, `giant_violet`, `giant_golden` | Vanilla huge-mushroom codecs with custom state providers; turquoise flat cap radius 3, violet domed radius 2, golden flat radius 2 |
| `turquoise_landmark`, `violet_landmark`, `golden_landmark` | Flat huge-mushroom cap radius 4 (nine blocks across with clipped corners), one-block stem; placed-feature landmarks, not structures |
| `glowing_moss`, `spore_bushes` | Vanilla random patches of support-checked, non-colliding custom plants |
| `glowing_small_mushrooms`, `memory_small_mushrooms`, `pink_small_mushrooms` | Weighted random patches; dominant biome colour weight 8, other colours weight 1 each |
| `glowing_flowers`, `memory_flowers`, `pink_flowers` | Cyan, violet and pink flower patches respectively |

Huge mushrooms retain vanilla height selection: 4–6 blocks, with a 1/12 chance
of doubling to 8–12. Landmarks have wider caps rather than guaranteed extra
height; their flat canopy begins at least four blocks above the root. Domed
red/violet caps can have low skirts, so they remain sparse. Collision/ground
checks come from vanilla huge-mushroom features; no Java generator is added.
New palette variants need blocks/assets and state providers; new distributions
or biomes need configured/placed features and biome wiring.

| Biome | Attempts per chunk (not successful placement counts) |
| --- | --- |
| Glowing Mushroom Forest | Turquoise ×2; red 1/3, brown 1/4, golden 1/4; turquoise landmark 1/12; moss, cyan flowers, turquoise-dominant small mushrooms and spore bushes; cyan/green fog and foliage |
| Pink Meadows | Golden 1/4; golden landmark 1/24; cherry 1/3; pink magical flowers, pink petals, meadow flowers and golden-dominant small mushrooms; no spore bushes or moss patches |
| Memory Forest | Violet ×1; red 1/3, brown 1/4; violet landmark 1/16; spruce reduced from four attempts to one; violet flowers, small mushrooms, moss and spore bushes; muted violet fog/sky/foliage |

Giant/landmark placement uses in-square sampling, dry-surface filtering,
`OCEAN_FLOOR` heightmap and biome filtering. All biomes list shared features in
consistent relative order, with mushrooms before trees and ground flora, to
avoid feature-sort cycles and keep decoration from blocking mushroom roots.
Patch attempts are bounded (12–64 tries, spread four horizontally and two
vertically); each plant needs empty air and valid support. Older surface moss
and lichen features/IDs remain available. Meadows retain substantial open
ground. Sparse stems, elevated broad canopies and passable flora help Syusya,
but only manual walking can establish useful routes through a real landscape.
Fog changes colour only, not distance or volumetric rendering.

Use a **NEW world created with Stage 2.3 installed** for visual acceptance.
Previously generated chunks are unchanged; saved generator/biome-source
settings may remain from earlier stages. `/reload` does not migrate worlds.
Keep the new block registrations installed once saved chunks contain them.

Testing: [Stage 2.3 client checklist](TESTING.md#stage-23-signature-vegetation-and-landmarks).
Results: [Stage 2.3 validation](VALIDATION-MUSHROOM-VALE-STAGE-2.3.md).

## Stage 2.4: cozy decor and landmark cottages

Baseline is the complete uncommitted local Stage 2.2/2.3 working tree. The user
confirms both earlier stages have passed testing and visual acceptance; their
original reports remain historical records. Stage 2.4 manual acceptance is
tracked separately. Terrain, dimension/type and biome IDs, biome climate
selection, water, travel/return, dialogue, UUIDs, saved data, existing registry
IDs and album protocol **9** are unchanged.

### Blueprint placement and extension points

One additive Forge feature type, `dudunka:mushroom_vale_decor`, uses
`MushroomValeDecorFeature.Blueprint`: `radius` (1–6), `height` (1–10), and
`blocks` (a list of `offset: [x,y,z]` and vanilla `BlockState` JSON in `state`).
Eight configured/placed-feature pairs live under `mushroom_vale/`. There are
no new blocks, items, models, textures, entities, dependencies or network/save
formats. Vanilla blocks plus existing Stage 2.3 plants provide the palette.

The feature checks the **entire** square footprint plus a one-block halo:
every floor block must be sturdy dirt-tag ground or moss at the same Y; every
block above must be air or a BushBlock plant, without fluids. Occupied builds,
trees/leaves, uneven ground, submerged sites, unavailable/unwritable chunks,
world borders and build-height violations reject placement before any write.
Blueprint offsets are bounded and unique; empty or oversized lists reject.
Successful placement rotates positions and block states together to one of
four cardinal orientations. Existing plants are replaced only at blueprint
block positions. No terrain leveling, foundation fill, path carving, background
search, forced chunks, global biome modifiers or vanilla overrides are used.

| Feature ID suffix | Attempt frequency in eligible chunks | Appearance / footprint |
| --- | --- | --- |
| `lantern_post` | 1/32 | Red mushroom-capped wooden post with supported warm lantern; radius 1 |
| `direction_marker` | 1/48 | Two contrasting wooden arrow shapes on a fence marker; radius 1 |
| `bench` | 1/40 | Three-wide spruce slab seat and upright trapdoor back; radius 1 |
| `mushroom_picnic` | 1/48 | Red mushroom table and two low crimson slab stools; radius 2 |
| `flower_bed` | 1/32 | Raised moss/pink-flower bed with wooden edging; radius 1 |
| `cozy_cluster` | 1/64 | Bench, lantern, potted mushroom and golden mushroom; radius 3 |
| `pufik_cottage` | 1/48, Pink Meadows only | Small cream stem shell, spotted red dome, flowers, warm lanterns and low porch; radius 4, height 8 |
| `wise_fly_agaric_cottage` | 1/64, Memory Forest only | Larger/taller red dome, amber windows, external bookshelves, empty lectern and reading bench; radius 6, height 9 |

The six small props are shared by all three Vale biomes. Cottages precede
props, then existing Stage 2.3 vegetation, in consistently ordered biome step
9. Placed features use rarity, in-square sampling, `OCEAN_FLOOR_WG`, dry-surface
filtering and biome filtering. Java also refuses every non-Vale dimension,
including manual `/place feature` outside Vale. Frequencies are attempts,
**not guaranteed builds**: the larger flat footprint can reject many cottage
sites. Cottages are placed features, so `/locate structure` cannot find them.

Both cottages have three-wide, three-high open entrances and a half-step porch
leading to a plain floor. Shells are intentionally simple and unfurnished;
Wise's scholarly details are outside. Benches/stools are ordinary decorative
blocks without a sitting interaction. Markers have no text or guaranteed
destination. Lanterns use ordinary vanilla block light under the normal day/night cycle.
There are no NPCs, quests, final portal, audio playback or progression systems.

Add future exteriors by creating another configured blueprint and a sparse
placed feature, then adding it to eligible Vale biome lists in consistent
order. `tools/generate_vale_decor.py` reproduces all eight pairs and idempotent
biome wiring. If regenerating earlier vegetation, run the Stage 2.3 generator
first, then this generator; the earlier tool rewrites biome vegetation lists.
Checked-in JSON is authoritative. This bounded feature is intended for small
exteriors; later large builds/interiors can use separate architecture without
reinterpreting these stable IDs or adding gameplay to this placer.

Previously generated chunks are unchanged. Use a **NEW world with Stage 2.4
installed**; saved generators can retain older biome sources, and `/reload`
does not migrate them. The halo and sparse attempts help traversal but do not
prove connected routes or Syusya navigation through finished landscapes.

Testing: [Stage 2.4 client checklist](TESTING.md#stage-24-cozy-decor-and-landmark-cottages).
Results: [Stage 2.4 validation](VALIDATION-MUSHROOM-VALE-STAGE-2.4.md).


## Stage 2.4a: original voxel decor and cottage exterior polish

Supersedes the Stage 2.4 vanilla-only appearance above. The approved local
`cottages_and_decor.png`, `giant_mushrooms.png` and `plants_and_flowers.png`
were inspected as art direction; their pixels are not copied into assets.
Twelve additive static blocks/items use original 16×16 pixel textures and
cuboid JSON models. `MushroomValeDecor` registers them through existing Forge
registers; `MushroomValeDecorShapes` is generated from the same model cuboids.
Shapes, collision and block states rotate together. There are no ticking
blocks, block entities, new interactions, recipes or dependencies.

Stable new block/item IDs (all `dudunka:`): `vale_lantern`, `vale_bench`,
`vale_signpost`, `vale_mushroom_table`, `vale_mushroom_stool`, `vale_planter`,
`vale_cottage_window`, `vale_cottage_cap`, `vale_cottage_crown`,
`vale_cottage_eave`, `vale_carved_wood`, `vale_book_stack`. Each has matching
blockstate, block/item models, loot and RU/EN labels. Keep registrations once
saved chunks use them. Old vanilla-block decor already in chunks remains intact.

Slatted benches have feet and back rails; mushroom furniture has layered caps
and cream stems; lanterns have wood/brass frames and amber chambers; two-board
markers, planted boxes and stacked books strengthen small-cluster silhouettes.
Lanterns emit light 14, window inserts 9, planters 4 and cream gilled eaves 3.
Windows are opaque amber decorative inserts, not transparent glass. Lighting
uses ordinary Minecraft block light under the unchanged fixed-noon dimension.

Cottages keep their existing feature IDs, radius/height limits and biome
eligibility. Tapered red caps, low rounded crowns and cream ribbed eaves sit
above cream stem walls, carved timber bases/jambs and amber windows. Pufik
remains smaller and welcoming; Wise has a larger cap and an exterior scholarly
reading nook. Each three-wide vestibule now contains a working vanilla spruce
door in its central two blocks and permanently clear side lanes. The interior
remains a plain floor. No NPCs, quests, portals, music or progression were added.

All eight placed-feature JSON files, rarity values and biome wiring are
unchanged. The same full-site preflight, one-block halo, dimension boundary
and cardinal rotation protect procedural generation. The shared
`MushroomValeDecorFeature.placeBlueprint` is also used by the explicit developer
preview: `/dudunka_dev mushroom_vale cottage pufik|wise X Y Z north|east|south|west`.
This is available only with the existing JVM opt-in, operator permission level
2 and an executing player in Vale. It checks loaded chunks and live occupants,
then places at the exact requested floor origin or refuses before any writes.
It never clears, levels, searches for, teleports to or increases the frequency
of cottages. Preview biome/rarity filters are deliberately bypassed only for
that explicit command. There is no persistent locator index or new saved data.

`tools/generate_vale_polish.py` produces original textures/models, matching Java
shapes, loot and labels. Run it before `tools/generate_vale_decor.py`; vegetation
need not be regenerated. Optional `tools/preview_vale_decor.py` creates offline
review sheets from those resources (vanilla blocks approximated). Checked-in
resources remain authoritative. Manual Minecraft/BMC4 visual acceptance is
separate from resource checks, model baking and placement GameTests.

Testing: [IntelliJ and exact preview commands](TESTING.md#stage-24b-cottage-architecture-and-usable-benches-in-intellij-idea).
Results: [Stage 2.4a validation](VALIDATION-MUSHROOM-VALE-STAGE-2.4a.md).


## Stage 2.4b: complete cottages and functional benches

Supersedes the Stage 2.4a entrance, plain-interior and non-interactive bench
behavior above. Full cream/wood blocks close every exterior boundary, including
recessed corners, jambs and lintels. Exactly one two-block spruce door opens
each cottage. Ornamental amber windows now sit outside full glass wall blocks;
a solid ceiling seals each stem below its spotted cap. Pufik keeps a compact
rounded 21-block floor, low dome, flowers and small mushroom furniture. Wise
has a 37-block elongated library floor with an asymmetric study bay, timber
frame, covered entrance, taller offset crown, six interior bookshelves,
lectern and warm interior lamp. Central aisles remain clear with standing
headroom. Radius/height limits, eight feature IDs and natural frequencies stay
unchanged. Previously generated cottages are not rewritten.

Right-clicking an existing `vale_bench` mounts a transient `vale_bench_seat`
entity using ordinary vanilla riding packets. One player fits each bench
block; occupancy is checked on the server thread. Shift uses vanilla dismount
controls with a nearby supported clear landing position. Removal of a bench
ejects its rider immediately; empty, removed, dead or dimension-mismatched
riders discard the mount on its next tick. Vanilla logout/dimension transfer
unmount the player. The entity type cannot be saved or summoned, and explicitly
rejects serialization. No occupancy map, block entity, custom packet or new
dependency is needed. Bench and lantern geometry/textures remain unchanged.

Tests exercise complete boundary walls, door halves/opening, safe floors and
standing doorway/aisle clearance in all rotations, distinct floor plans and
library furniture, plus right-click seating, facing/height, Shift input,
occupancy, serialization refusal, breaking, actual logout and dimension
transfer. Visual seating pose and full BMC4 multiplayer acceptance remain
manual. [Exact development commands](TESTING.md#stage-24b-cottage-architecture-and-usable-benches-in-intellij-idea).
[Validation results](VALIDATION-MUSHROOM-VALE-STAGE-2.4b.md).

## Stage 2.5: magical atmosphere, music and environmental polish

Supersedes the Stage 2.3 mushroom frequency/landmark geometry above. Existing
feature IDs are retained. Ordinary huge mushrooms still use vanilla codecs;
red and brown remain occasional accents. Attempts per eligible chunk now are:

| Biome | Giant / landmark attempts |
| --- | --- |
| Glowing Mushroom Forest | turquoise 1/12, red 1/24, brown 1/32, golden 1/32; turquoise landmark 1/48 |
| Memory Forest | violet 1/16, red 1/24, brown 1/32; violet landmark 1/56 |
| Pink Meadows | golden 1/32; golden landmark 1/96 |

Small-mushroom patches retain one attempt per chunk, with 12 internal tries in
the forests and 8 in meadows (formerly 24/24/12). Weighted palettes also include
occasional vanilla red/brown small mushrooms; vanilla support/light survival
still applies. Other flora, supporting trees, terrain and cottages are unchanged.
These values count attempts, not guaranteed mushrooms.

The three stable `*_landmark` configured features now use additive feature type
`dudunka:mushroom_vale_landmark`. It decodes the existing bounded Blueprint
format, preflights the full above-ground volume plus a one-block halo, and
requires sturdy dirt/moss only beneath the root. It never levels, clears,
fills, forces chunks or partially places a rejected site. A blocked root,
fluid, occupied canopy, border or unavailable chunk rejects the entire build.
Biome/rarity/dry-surface filters still control natural placement.

Four additive static blocks/items provide model-matched voxel collision:
`turquoise_landmark_cap`, `violet_landmark_cap`, `golden_landmark_cap`, and
`vale_landmark_stem` (all `dudunka:`). Integer `profile` 0–4 selects ribbed gills,
spotted rims, stepped shoulders, low crowns, hanging luminous spores, and
fluted stem/root/neck shapes. Original 16×16 cap/glow pixel textures coordinate
with `giant_mushrooms.png`; earlier textures remain intact. Landmarks span nine
blocks, rise nine blocks (violet ten), and keep broad caps/spores at least four
blocks above ground. Cap light is turquoise 10, violet 8, golden 9. These are
static non-ticking models without block entities or dependencies. New blocks
must remain installed once saved chunks contain them. Earlier chunks retain
older mushroom shapes and frequencies.

`vale_lantern` was already a real registered block emitting 14. Stage 2.5 makes
its porous frame explicitly block zero light; geometry, textures and emission
stay intact. Normal Minecraft lighting owns propagation and removal. No
invisible light blocks, auxiliary entities, custom ticking or unrelated lighting
changes are introduced. Daylight can hide block light; check F3 **block** light
or use a roofed inspection area rather than combined sky brightness.

The standalone bench feature still attempts 1/40, and flower beds 1/32. Bench
content is three real `vale_bench` blocks, while flower beds contain planters
and moss. They never substitute for one another. Standalone bench preflight
now requires ground below its three seats rather than a perfectly flat 5×5
halo. Full above-ground clearance, fluid, border, chunk and rotation checks
remain. Other decorations and both cottage placement contracts are unchanged.
The existing seating lifecycle and transient seat entity remain unchanged.
The JVM-opt-in/permission-2/player/Vale preview route additionally accepts:
`/dudunka_dev mushroom_vale decor bench|lantern_post X Y Z north|east|south|west`.
It reuses the existing blueprint/occupant checks without raising worldgen rates.

### Native soundtrack and transition limits

Forge DeferredRegister registers `dudunka:music.mushroom_vale_theme` as a
variable-range SoundEvent. `assets/dudunka/sounds.json` maps it to the existing
unchanged `sounds/music/mushroom_vale_theme.ogg` with `stream: true`. Each Vale
biome's `effects.music` selects that event, `min_delay: 1200`, `max_delay: 3600`
(ticks, approximately 1–3 minutes at 20 TPS), and `replace_current_music: true`.
There is no client audio controller, packet, dependency or custom music tick.
Vanilla MusicManager uses the Music volume category and owns one background
track, so moving between these three biomes does not restart/overlap copies.

Local Minecraft 1.20.1 `Minecraft.getSituationalMusic()` and `MusicManager.tick()`
are authoritative for limits: Creative flight/instabuild selects Creative music
before biome music, so validate natural scheduling in **Survival**. Screens,
End boss/End and underwater music can also take priority. On entry, a different
managed background track is stopped and a delay of 0–600 ticks (up to about
30 seconds) can precede the Vale theme. A first session can use vanilla's
100-tick starting delay; 1–3 minutes describes ordinary inter-track gaps, not a
strict entry guarantee. Outside Vale, destination selection resumes immediately,
but a current Vale track may finish when the destination Music has replacement
disabled (including ordinary GAME/CREATIVE music). No forced exit stop is added.
Low TPS, pausing and Music volume zero affect perceived scheduling. Explicit
`/playsound` copies and jukebox/modpack audio are outside MusicManager's single
background slot; stop diagnostic audio before testing natural scheduling.

Use a **NEW runClient world**. No saved-generator migration, old-chunk rewriting,
terrain density/travel changes, cottage changes, companions/dialogue, quests,
NPCs, final portal, saved-data changes or protocol changes are included.
`tools/generate_vale_atmosphere.py` narrowly reproduces Stage 2.5 after older
Vale generators; do not run older generators alone and expect these overrides
to survive. Checked-in assets/configuration remain authoritative.

See [Stage 2.5 commands and acceptance](TESTING.md#stage-25-magical-atmosphere-music-and-environmental-polish)
and [validation report](VALIDATION-MUSHROOM-VALE-STAGE-2.5.md).


## Stage 2.5a: environment fixes and automatic Creative music

Supersedes Stage 2.5 frequencies and Creative/exit music limitations above.
Stable IDs, cottage blueprints, approved decoration models/textures, small
flower-bed decorations, terrain noise/density and album protocol 9 are retained.

Ordinary huge red/brown mushrooms attempt 1/6 and 1/8 in all three biomes.
Medium detailed turquoise/violet mushrooms attempt 1/4 in their forests;
golden attempts 1/6 in Glowing Mushroom Forest and Pink Meadows. These medium
features reuse the existing voxel cap/stem blocks, with seven-wide layered
caps, hanging bulbs and ground clearance. The nine-wide large landmark attempts
remain 1/48 turquoise, 1/56 violet, 1/96 golden. Placement now permits low dirt
slopes in unused ground cells below the canopy; every actual piece, canopy,
headroom, tree/building/fluid and chunk boundary remains preflighted. No terrain
is cleared, leveled or filled. Forest small-mushroom patches use 32 tries,
meadows 24; ordinary red/brown weights are 4 each. Standalone benches attempt
1/8 rather than 1/40, retaining three real sittable seat blocks and support/
clearance checks. Flower beds and cottages retain their configurations/rates.
Attempts are not successful placement frequencies; measured counts are in the
[Stage 2.5a validation report](VALIDATION-MUSHROOM-VALE-STAGE-2.5a.md).

Lanterns and hanging cap profile 4 emit maximum Minecraft block light 15.
Other cap profiles retain their color's 10/8/9 emission; porous landmark parts
explicitly obstruct zero light. Light belongs to the visible block and vanilla
updates/remove it. The actual engine regression checks 14/13 in adjacent air,
10 one block above the walking surface, three blocks from a solid/fence post,
sky light 0 and darkness after removal for all four source types. Use a roofed
space for darkness checks during daytime; the Vale day/night cycle continues.

## Stage 2.5b: ordinary mushroom and lantern frequency

Fresh chunks use 40 small-mushroom patch tries per forest chunk (up from 32)
and 30 per Pink Meadows chunk (up from 24). Existing ordinary red/brown weights
remain 4 each, preserving their balance against magical variants while raising
all small mushroom opportunities by 25%. Lantern-post rarity changes from 1/32
to 1/28 eligible chunk, a modest increase; the post blueprint, cozy-cluster and
cottage lanterns, block light and approved visuals are unchanged. These remain
attempt frequencies: site filters can reject placements, and existing chunks
are not regenerated. See the [Stage 2.5b validation report](VALIDATION-MUSHROOM-VALE-STAGE-2.5b.md).

Forge 47.4.13 has no situational-music selection event. Cached 1.20.1 sources
show Creative selected before biome music, with replacement disabled and
12000–24000-tick gaps. `client/MushroomValeMusic` uses the existing native
MusicManager slot: replace the old managed music and start the existing Vale
event once on entry, then supply 1200–3600-tick gaps if Creative's scheduler
has no Vale track. Native Survival scheduling can start the same track during
a gap; the controller detects that slot and does not start another copy.
Biome/mode changes during a track leave it running. On exit only a managed
Vale track is stopped; normal destination scheduling resumes. Paused clients
and screens with their own background music suspend starts/countdowns. Music
volume, streaming, actual sound lifecycle and the one-track slot remain vanilla.
There are no new sounds, dependencies, packets, mixins or access transformers.

Manual `/playsound` creates a separate sound and bypasses all automatic selection
and scheduling; it confirms assets/decoding/category routing only. See
[updated client commands and regression procedure](TESTING.md#stage-25a-final-environment-fixes-and-homecoming-regression).
