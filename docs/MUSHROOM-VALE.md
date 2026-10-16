# Mushroom Vale (Грибная долина)

## Approved design

Mushroom Vale is a cozy, magical dimension for Dudunka Family on Minecraft 1.20.1, Forge 47.4.13, and Java 17. The target modpack is Better MC BMC4 v55.5. Its landscapes include glowing mushroom forests, pink meadows, and a mysterious memory forest. Friendly inhabitants are Pufik, a small living mushroom; the Wise Fly Agaric; magical fireflies; and fluffy jumping creatures.

The story, **Restore the Music of the Valley**, unfolds across five chapters. Access opens through cooperation between grown-up Dudunka, Marusya, and Syusya after sufficient family friendship. The experience stays gentle: combat is never mandatory, and failure or missed activities do not punish the player. An original instrumental soundtrack has been prepared separately. Approved visual concepts are artistic references only; they are not production-ready models or textures. Do not assume reference images or audio assets are present in Git.

The approved music storyline follows the five native musical landmarks:

| Chapter | Landmark | Story role / implementation status |
| --- | --- | --- |
| 1. “Where Did the First Note Go?” / «Куда пропала первая нотка?» | `dudunka:music_bells` | Dudunka and Pufik help restore the first three-note melody; Stage 4.2a |
| 2. “Dance of the Fluffy Jumpers” / «Танец пушистых прыгунов» | `dudunka:jumper_dance` | Marusya guides repeated flower tap groups; Stage 4.2b |
| 3. “The Forest That Remembers” / «Лес, который помнит» | `dudunka:memory_stone` | Syusya and the Wise Fly Agaric guide a quiet firefly memory; Stage 4.2c |
| 4. “The Secret of the Silver Web” / «Тайна серебряной паутинки» | `dudunka:silver_web_harp` | Friendly mushroom spiders join the family’s three melodies through harmonic tuning; Stage 4.2d |
| 5. “A Song for All Friends” / «Песня для всех друзей» | `dudunka:singing_mushroom` | A private, replayable family concert celebrates all four restored motifs; Stage 4.2e |

This music story is separate from the five existing **Mystery of Mushroom Vale**
album chapters. Stages 4.2a–e introduce no album UI or protocol change.

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

**Scope:** Implement the five chapters of **Restore the Music of the Valley**. Use exploration, friendly encounters, family cooperation, and restoration of musical elements to advance the story. Integrate the prepared instrumental soundtrack only from assets explicitly included and tracked for the implementation.

**Acceptance:** All five chapters can be completed in order, explain their goals in accessible language, and preserve progress across save/reload. Players can pause or leave and return without losing progress. No chapter requires combat, time-limited attendance, consumable progression items, or punitive failure. Existing requests, keepsakes, memories, and album progress remain intact.

**Compatibility risks:** New quest progress must use additive, version-tolerant saved data and must not reinterpret existing memories or request state. Audio registration and playback must tolerate a missing optional asset during development and avoid interrupting unrelated game audio. Do not presume the separately prepared soundtrack or concept art is committed.

### 6. Final testing

**Scope:** Validate the complete dimension and progression on the required Minecraft/Forge/Java versions, first with focused automated checks and then in a graphical client and full BMC4 v55.5. Include multiplayer and old-world upgrade scenarios.

**Acceptance:** Appropriate Gradle build, model/resource checks, and relevant GameTests pass with results recorded. Manual Minecraft testing confirms all three environments, inhabitants, five chapters, portal requirements, travel and return, save/reload, multiplayer ownership, and gentle failure handling. Full BMC4 acceptance is recorded separately from automated results. Existing IDs, UUIDs, saves, protocol behavior, and established mechanics pass regression checks.

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

## Stage 3.1: Pufik

`dudunka:pufik` is the first independent Vale inhabitant. Its 0.68-wide,
0.62-high creature type, attributes and ground spawn placement are registered
through Forge; it is not a `Kind` or `Companion`. The approved
[character reference](reference/mushroom-vale/pufik.png) guides a dedicated
43-part, layered voxel model and original 128×128 pixel-art atlas. The wide
red cap has cream raised spots and a cream underside; the small body has a
face, rosy cheeks, hands and tiny feet. Idle breathing, brisk steps, a happy
hop and a shy cap tilt use bounded procedural part transforms and synchronized
emote timing. Right-click sends a localized short greeting and triggers the
happy pose. There is no ownership, growth, friendship, inventory or reward.

The glowing mushroom forest biome adds Pufik to its existing creature list at
weight 5, groups of 1–2. The spawn predicate requires both
`dudunka:mushroom_vale` and `dudunka:glowing_mushroom_forest`, dry sturdy ground,
standing clearance and a nearby floor without a two-block drop. Wander targets
are chosen within five loaded blocks of the current position; normal ground
navigation and water/fire path penalties guide travel. Named Pufiks resist
ordinary distance despawning. Ordinary Pufiks retain vanilla creature despawn
behavior and are not marked permanently persistent. Vanilla entity NBT saves
identity, name and health. `/summon dudunka:pufik ~ ~ ~` remains available for
inspection in any dimension; natural spawning is Vale-only.

No dimension/worldgen IDs, existing entity IDs, family UUIDs, save formats,
network protocol 9 or existing biome spawn entries change. The new biome entry
is additive. See [testing steps](TESTING.md#stage-31-pufik) and
[validation](VALIDATION-MUSHROOM-VALE-STAGE-3.1.md).

Pufik's close-range look-up is a client pose on a shared stem/face/cap pivot.
It tracks interpolated nearby player eyes with per-entity smoothing, fully
engages within 2.5 blocks and eases out to 3.5 blocks. The cap moves slightly
back and up to open the sightline beneath its brim. The existing developer
launch flag enables `/dudunka_pufik_pose rest`, `look_up` and `off` for fixed
pose comparison; see the deterministic preview steps in `TESTING.md`.

## Stage 3.2a: naturally spawning mixed-color firefly groups

`dudunka:vale_firefly` is an independent 0.2×0.2-block ambient flying mob,
separate from `Kind` and `Companion`. The original small stepped voxel body,
head, eyes, antennae and thin translucent animated wings follow the firefly
panel in `creatures_and_props.png`; the six original 32×16 pixel atlases are
reproducible with `tools/generate_vale_firefly.py`. Golden, turquoise and violet
abdomen masks use vanilla's additive `RenderType.eyes` pipeline at full render
brightness. UUID-based phases pulse independently between 45% and 100%; base
render block light is clamped to 8 for face/wing readability. These are render
values only: no actual light source, block, light-engine update or packet.

Each of the three existing Vale biomes adds ambient weight 2, groups 3–5,
retaining bats and all creature entries. `AMBIENT` shares vanilla's cap of 15
with bats, rather than competing with Pufik/animals for the creature cap of 10.
Vanilla eligible-chunk, minimum player distance and local caps still apply;
full BMC4 spawn balance needs manual acceptance. Natural spawning requires the
Vale dimension AND one of its three biome IDs, loaded clear dry surface air
(at the first air block through four blocks above a safe nearby floor, including
understory up to 12 blocks beneath a mushroom canopy), time
12000–22999 and a player within 48 blocks. Deep caves, high air and fluid surfaces are excluded.
Weighted palettes are Glowing Forest 55% golden / 30% turquoise / 15% violet,
Pink Meadows 70% golden / 20% turquoise / 10% violet, and Memory Forest 70%
violet / 20% turquoise / 10% golden. NaturalSpawner passes `FireflySpawnGroup`
through `SpawnGroupData`; it chooses the first color by palette weight and forces
the second successfully spawned member to a different palette color. Later
members use normal weights. The biome entries request groups of 3–5 at weight 2
(previously 2–4 at weight 3), keeping weighted group size near its previous level
while making groups visibly mixed. The ambient cap remains shared with bats.
Explicit `/summon` EntityTag values still set their own `ValeVariant`; the saved
variant and existing assets/rendering are unchanged. Default manual summon is
golden.

Server flight uses smoothed velocity up to 0.055 blocks/tick at evening/night,
0.018 by day, with gentle vertical bobbing. Target selection tries at most six
local candidates every 40–99 active ticks (100–179 by day), also refreshing on arrival.
One in four active selections can bias toward a loaded pack member within four blocks.
Occasional small circular flights vary their altitude. Home is bounded
to horizontal radius 6 and ±3 vertical blocks; both targets and each swept
movement volume must stay within it. Chunk availability is checked before
block/collision reads, followed by fluid, fire, wall and build-limit checks.
Blocked movement stops and retries; there is no expensive pathfinding, chunk
request or terrain modification. External relocation farther than 16 blocks
establishes a new local home. There is no Pufik interaction or family behavior.

Vanilla entity NBT preserves UUID/name/health; additive `ValeVariant` and
`FlightHomeX/Y/Z` preserve color and local bounds. Missing variant defaults to
golden, invalid values clamp, and invalid/nonfinite homes are ignored. Ordinary
ambient saving and idle despawning apply, with ordinary unnamed fireflies released
beyond 48 blocks of every player so distant groups do not occupy their shared cap.
Naming, explicit persistence and Forge despawn vetoes protect against this release.
No taming, ownership, item pickup, hostile target, attack, quest or progression
is added. Existing Pufik, family UUIDs, save data, worldgen, music and album
protocol **9** remain unchanged. Tests and manual commands are in
[Stage 3.2 testing](TESTING.md#stage-32-magical-fireflies) and the
[Stage 3.2a validation report](VALIDATION-MUSHROOM-VALE-STAGE-3.2A.md) and
[Stage 3.2b validation report](VALIDATION-MUSHROOM-VALE-STAGE-3.2B.md).

## Stage 3.2c: sustained flight and visible natural groups

Natural first-air spawns now take off instead of failing a downward-inflated
collision check. Steering retains its own momentum so Minecraft's small-velocity
cutoff does not freeze gentle daytime movement. Normal targets hover about 1–3
blocks above nearby terrain; takeoff/reloaded ground homes shift their flight
area upward without moving or teleporting the entity. Exact swept-body checks,
clearance at destinations, loaded-column probes and world-border checks protect
each flight. Steering remains server-authoritative; ordinary entity tracking
and client interpolation carry position/velocity, while existing wings and glow
remain client rendering.

Natural spawning retains `AMBIENT`, cap 15, biome weights 2, requests for 3–5,
and all three palettes. Its 24–48-block local spawn ring and matching despawn
range replace distant invisible residents as players explore. Same-biome
newborns from adjacent vanilla spawn loops in the same server tick can share
`SpawnGroupData`, capped at five, so failed positions need not leave neighboring
singletons as separate groups. No additional members are created to fill packs;
vanilla safety checks can still leave incomplete groups. Pack identity is
transient server state; colors and existing home NBT persist normally.

[Stage 3.2c measurements and validation](VALIDATION-MUSHROOM-VALE-STAGE-3.2C.md)
distinguish actual completed natural spawns from the earlier eligible-position
estimate. Minecraft/BMC4 visual and multiplayer acceptance remains manual.

## Stage 3.2d: naturally discoverable cottages and Pufiks

This section supersedes the earlier placement frequencies and full-square flat
foundation rule. Approved blueprints, block models, textures, doors, interiors,
seating, lighting and all IDs/codecs remain unchanged. Existing chunks are never
rewritten. New generation retains the same biome lists and feature ordering.

Cottages keep flat, dry, sturdy soil beneath **every ground-level blueprint
piece** and a level approach to the half-step porch. Exterior space and the one-block halo may follow a dry one-block rise
or drop; all terrain remains untouched. The complete checked volume still
rejects fluids, trees/leaves, other structures, unavailable/unwritable chunks,
world-border and build-height violations. Only existing BushBlock vegetation
at explicit blueprint positions may be replaced. Every rejected site is atomic.
A chosen cottage chunk tries the original position, then at most 15 local
positions in that same chunk and biome, each with the complete safety preflight.
Explicit developer previews keep their exact requested position and orientation.

| Feature | Attempt filter | Site behavior |
| --- | --- | --- |
| Pufik cottage | 1/24, Pink Meadows | Level actual foundation; bounded local search |
| Wise Fly Agaric cottage | 1/32, Memory Forest | Level actual foundation; bounded local search |
| Bench | 1/8, all Vale biomes | Three supported seats; dry one-step exterior slopes allowed |
| Lantern post | 1/64, all Vale biomes | Supported post/planter; dry one-step exterior slopes allowed |

The lantern filter is deliberately rarer than Stage 2.5b's 1/28: the old full
5×5 flat-site rule rejected most attempts. Better site acceptance increases
successful placement, while the rarer filter keeps the resulting density modest.
Direction markers, flower beds, picnic/cozy clusters and small mushroom
configuration are unchanged. Terrain and vegetation density are unchanged.

Pufik remains `dudunka:pufik`, category `CREATURE`, groups **1–2**, with ordinary
vanilla distance/idle despawning and name/persistence/Forge protections. The
Glowing Mushroom Forest creature weight is **24**, from 5; mooshrooms retain
weight 8 and groups 4–8. The shared creature cap remains **10**. Spawn placement,
spawn Y sampling, the Vale + forest restriction, safe ground, nearby cliffs,
fluids/hazards and clear body/head checks are unchanged. Runtime creature spawning
still uses vanilla's 400-tick cadence; generated passive animals can fill the cap.
No custom spawner, forced population, chunk request or persistent NPC is added.

[Stage 3.2d validation](VALIDATION-MUSHROOM-VALE-STAGE-3.2D.md) distinguishes
completed `CHUNK_GENERATION` births from runtime `NATURAL` births, placement
attempts from successful sites, and server visibility proxies from manual client
acceptance. [Natural exploration instructions](TESTING.md#stage-32d-natural-landmarks-and-pufik-acceptance)
cover fresh worlds and unvisited terrain. Fireflies and music are unchanged.

## Stage 3.3: friendly magical spider

`dudunka:vale_spider` is an independent peaceful `PathfinderMob`, category
`CREATURE`, width 0.8 and height 0.5 blocks. It follows the inspected approved
[friendly spider reference](reference/mushroom-vale/friendly_spider.png): stepped
rounded ivory body, eight short paws, large highlighted eyes, rosy cheeks,
a little smile and a layered rust-red cap. Its dedicated vanilla model/renderer
and original 128×64 pixel atlas add no dependency. The atlas is reproducible with
`tools/generate_vale_spider.py`. The [offline geometry preview](VALE-SPIDER-PREVIEW.png)
uses actual baked vertices and sampled texture pixels; client visual acceptance
remains separate.

Small rigid transforms animate breathing, all eight walking legs, a curious
look up toward nearby player eyes, a single front-leg greeting and a brief shy
body dip. Client look smoothing is per entity; server-synchronized emote type and
start time provide a 32-tick envelope. No existing network packet or protocol
changes. Emotes/social cooldowns are transient; vanilla entity NBT retains UUID,
name, health and ordinary entity state without family tags.

Server AI chooses at most eight nearby loaded columns, checking same-height and
one-step ground candidates with the existing `Pufik.safeGround` safety helper
and ordinary reachable ground paths. Paths expire after a short interval, avoid
water/fire and drops, and occasionally approach a player while keeping room.
A nearby sprinting player can prompt a brief shy retreat at normal stroll speed,
with an eight-second cooldown. Nearby relaxed players sometimes receive a wave.
Right-click sends an English/Russian friendly greeting and waves. It cannot set
an attack target, perform a melee attack, trample farmland, create webs or modify
blocks. There is no taming, trading, inventory, quest or progression system.

Natural spawning adds **weight 32 / group 1–2 in Memory Forest**, and
**weight 6 / group 1–2 in Glowing Mushroom Forest**. Pink Meadows receives no entry.
The predicate requires the Vale dimension AND an intended biome, loaded dry
sturdy ground, body/head clearance, safe neighboring floors without two-block
drops, the world border, and dirt/mycelium or moss soil within twelve blocks of
the surface heightmap. Mushroom understory is eligible; roofs, mushroom-cap
floors and deep caves are excluded. Existing animals, Pufik/firefly entries,
worldgen, music and all prior registry IDs remain intact. Vanilla chunk-generation
births and 400-tick runtime creature spawning apply; the shared creature cap is
still ten. Ordinary distance/idle despawning applies, with names, persistence
and Forge vetoes protected. No additional population is created outside vanilla
spawning, and existing animals can prevent runtime replenishment.

The [Stage 3.3 validation report](VALIDATION-MUSHROOM-VALE-STAGE-3.3.md) records
completed births, competing animals, caps and visibility across three actual
world seeds, including routes without an observed encounter. Terrain/foliage
and the small creature silhouette affect sightlines; measured births are not a
promise of encounters at every location. Existing Companion UUIDs/save code,
Pufik/firefly implementations, dimension/biome IDs and album protocol **9** are
preserved. [Exact commands and manual checks](TESTING.md#stage-33-friendly-magical-spider)
cover client, BMC4, multiplayer and existing saves.

## Stage 3.4: Wise Fly Agaric

`dudunka:wise_fly_agaric` is an independent friendly `PathfinderMob`, category
`CREATURE`, collision size 0.72×1.15 blocks, health 12 and movement speed 0.12.
He is separate from `Companion` and `Kind`. The inspected approved character
concept guides his red spotted dome, cream stem, highlighted eyes, white brows,
moustache and tapered beard, leafy cloak, leather belt/pouch and wooden crook
with an amber lantern. The original 128×64 atlas is reproducible with
`tools/generate_wise_fly_agaric.py`. The lantern is a model prop, without world
lighting or an inventory. A small open book appears during reading.

A shared head pivot carries face, hair, beard and cap. Client rendering provides
gentle rigid breathing, slow alternating steps, thoughtful head tilt, greeting
wave/bow, reading and a visible look toward nearby player eyes. Look smoothing
is per entity in a weak-key map; each render resets every part and book visibility.
Close look lifts the head and shifts the brim upward/back. Server-synchronized
emote type/start time uses ordinary entity metadata; album protocol stays **9**.
Think/read last 100 ticks, greeting 40 ticks, with sine envelopes. Main-hand
right-click chooses one of six English/Russian lines, with an 80-tick server
cooldown. No quests, trades, rewards, inventory, ownership or progression.

Server AI samples at most eight local columns and three nearby heights, requires
safe dry ground, a reachable path and safe ground at every path node, then follows
that checked path for at most 80 ticks. It rechecks the next node while moving.
Water, lava, fire and other damaging path types are avoided; FloatGoal provides
recovery if externally pushed into water. No hostile targets, attacks, item pickup,
trampling, block changes or obstacle placement are added. External pushes and
terrain changes are not a guarantee of invulnerability.

Natural spawning adds **weight 10, group 1–1, Memory Forest only**, requiring
both the Vale dimension and biome. Dry sturdy soil/moss, clear body/head space,
nearby safe floors, world border and a position within twelve blocks under the
surface heightmap are required. Mushroom understory is allowed; roofs/caps and
deep caves are excluded. Vanilla chunk-generation spawning and 400-tick runtime
creature spawning remain in use. Runtime Y is sampled across world height, so
most trials never land on eligible ground. The shared creature cap remains 10;
existing passive animals and spiders can suppress runtime replenishment.
Ordinary despawn applies, with names, persistence and Forge vetoes respected.
All prior spawn entries/weights, terrain, decorations and music are preserved.

Cottage residency is deferred: `MushroomValeDecorFeature` places bounded JSON
block blueprints, without structure starts, resident markers, ownership or
entity lifecycle records. The approved Wise cottage still has its library,
lectern, bench and unchanged 1/32 placement filter. Reliably associating one
resident across chunk unload/reload and despawn needs a separate saved residency
contract. Stage 3.4 adds no scans, forced chunks or entities during worldgen.
Manual summons beside a cottage are available for inspection.

See [testing instructions](TESTING.md#stage-34-wise-fly-agaric),
[offline preview](WISE-FLY-AGARIC-PREVIEW.png) and
[actual validation results](VALIDATION-MUSHROOM-VALE-STAGE-3.4.md).

## Stage 3.4a: Wise cottage residency

Cottages remain worldgen placed features. `MushroomValeDecorFeature`, placement
rates and all blueprint JSON are unchanged. No entity is created during feature
placement. The approved Wise model, texture, renderer and animation code are
unchanged, as are the six bilingual dialogue lines and ordinary natural spawning.

`WiseCottageResidency` runs on the server thread, once every 20 ticks in the Vale.
It rotates through one non-spectator visitor's nine nearby chunks, reads only
already loaded chunks, examines at most 64 block entities, and tests lecterns
against four rotated structural fingerprints of the existing furnished Wise
blueprint. It issues at most one resident per pass, within 48 blocks of that
visitor, in Memory Forest. The fingerprint checks the birch floor/stem, red
mushroom roof, bookshelves and lectern; door/book/redstone state and movable
props do not affect recognition. No structure lookup, terrain rewrite, world
scan, chunk ticket or background worldgen mutation is used. With many visitors,
each visitor receives a nine-second turn; densely modified chunks with more
than 64 block entities may not automatically enroll a cottage. This deliberately bounds server work.

The Overworld saved-data ledger `data/dudunka_wise_cottages.dat` stores cottage
floor origin, cardinal rotation and issued resident UUID. Entity NBT stores
`WiseCottageOrigin` (packed BlockPos long) and `WiseCottageRotation` (0–3), in
addition to vanilla UUID and persistence. Issuance is written through a compressed
NBT temporary file, flushed, and atomically renamed **before** adding the entity.
A failed write creates no entity and retains an in-memory reservation for the
session. Normal server-thread serialization makes simultaneous visits idempotent.
There is no lookup-based replenishment: a missing loaded UUID can mean an
unloaded chunk, death, `/kill`, deliberate removal or cancelled entity join.
All consume the original issuance permanently. An interruption after issuance
but before entity saving can leave an empty cottage; it cannot trigger an
automatic replacement. No reset/replacement command is supplied. Backups must
retain both world entity/chunk data and this ledger together.

Previously generated **furnished cottages matching the current blueprint** gain
a resident on a nearby visit, without markers or block changes. This includes
Stage 2.4b–3.4 cottages and developer previews in Memory Forest. Very old exterior
shells predating the furnished layout, damaged structural fingerprints, and
player builds without the full fingerprint are left unchanged and uninhabited.
Existing naturally spawned elders retain ordinary AI/despawn behavior and are
not adopted or removed. Consequently an independent wanderer may also be nearby;
only the ledger UUID is the designated resident.

Residents are persistent, take checked local walking trips through the existing
entrance/porch, return to a reading position inside, and use the existing think
and read emotes while resting at home. No teleport recovery is used. Unloaded
pathfinding regions and unsafe/damaged routes are refused and retried later.
The existing wooden cottage door is opened before contact and held until he clears
the doorway. Waypoints align with the free opening beside the hinged panel,
so his approved 0.72-block collision size can pass without changing the model
or cottage. No door is broken.
Navigation does not promise recovery from player confinement or external pushes.

See [Stage 3.4a testing](TESTING.md#stage-34a-wise-cottage-residency) and
[validation report](VALIDATION-MUSHROOM-VALE-STAGE-3.4a.md).

## Stage 3.4b: Pufik cottage residency

Pink Meadows Pufik cottages enroll one permanent resident when an intact,
loaded cottage is discovered near a non-spectator visitor. No entity is created
during blueprint placement or world generation. The existing server-thread
residency service checks the visitor's loaded chunk on its rotating nine-chunk
schedule. Pufik entrances are recognized in a bounded vertical band around the
visitor; each candidate must match the complete rotated birch, mushroom-roof
and spruce-door fingerprint. Enrollment is dimension- and Pink Meadows-specific.
Existing generated cottages and developer previews are eligible without block
markers or edits.

Pufik records share `data/dudunka_wise_cottages.dat` in an additive
`PufikCottages` list. Existing `Cottages` Wise records retain their original
format. Each record permanently reserves the cottage origin, rotation and
resident UUID; it is flushed before entity creation. Entity NBT stores
`PufikCottageOrigin` and `PufikCottageRotation`. Unloaded, killed, deliberately
removed or rejected residents are never replaced. Natural Glowing Mushroom
Forest Pufiks keep their ordinary despawn rules and are never adopted.

Residents use bounded dry-ground routes through their spruce doorway, wander a
small area beside home and return to rest. Their existing happy, shy, walking,
look-up and greeting behavior remains in use. No teleport movement, new
interaction system, registry ID or protocol change is introduced. The existing
Pufik model, texture, renderer, spawn weights and personality are preserved.
Discovery remains visitor-driven and does not load chunks. A missing/damaged
fingerprint or unsafe interior prevents enrollment. At most 16 spruce-door
candidates are fingerprinted per visited chunk pass; unusually door-dense,
player-built chunks may need a later visit or remain unrecognized. As with Wise
issuance, an interruption after the durable reservation but before entity save
can leave an empty cottage permanently. See [Stage 3.4b testing](TESTING.md#stage-34b-pufik-cottage-residency).

## Stage 3.5: Fluffy Jumpers

`dudunka:fluffy_jumper` is independent friendly wildlife, approximately half a
block tall. The original code-native voxel model follows creature 4 of
`docs/reference/mushroom-vale/creatures_and_props.png`: layered round body,
all-round cuboid wool tufts, four tiny paws, large dark eyes with bright glints,
rosy cheeks and a small smile. Three original opaque pixel atlases provide
peach pink, lavender and pale blue variants. Vanilla entity data synchronizes
color and animation timing; the existing client layer/renderer system adds idle
breathing, curious looking, tucked paws, smooth flight stretch, landing squash
and cheerful greeting sway. No external animation library is used.

Jumpers rest for 18–40 grounded ticks between short physics hops. Most hops use
an upward impulse of 0.30; occasional excited hops use 0.42. Nearby players
encourage curious approaches and sideways play, and an excited jumper can
encourage nearby grounded jumpers within three blocks, with a short stagger.
The server checks a bounded swept flight volume and supported corridor before
each launch; water, fire, damaging blocks, insufficient headroom, unloaded
chunks and ledges are refused. Vanilla gravity, friction and collisions move
the creature. Confined jumpers simply rest. There is no teleportation, attack,
target, trampling, taming, inventory, breeding, trading, quest or companion
ownership. External pushes and blocks changed during flight remain subject to
ordinary Minecraft physics.

Pink Meadows alone adds a standard `creature` spawn entry, weight **24**, group
**2–3**, using `ON_GROUND` placement. Stage 3.6 removes vanilla passive animals
from all three Vale creature lists. Pufik remains weight **40 / 1–2** in Glowing
Mushroom Forest; Vale Spider is **44 / 1–2** in Memory Forest and **20 / 1–2**
in Glowing Mushroom Forest. Wise Fly Agaric, magical fireflies, and cottage
residents keep their existing entries and behavior. Spawn candidates keep the
existing dimension, habitat, ground, headroom, neighboring support, and hazard
checks.

Right-click with the main hand gives a short localized friendly message and
cheerful sway, and schedules an excited hop if a safe route exists. A 40-tick
server cooldown suppresses duplicate clicks. English and Russian are supplied.
The only custom saved field is additive `FluffyColor` (0–2); vanilla UUID,
health, name, location and despawn handling remain intact. Unnamed wild jumpers
can naturally despawn; named jumpers follow vanilla persistence. New summon:

```mcfunction
/summon dudunka:fluffy_jumper ~2 ~ ~ {FluffyColor:0}
```

Set `FluffyColor` to 1 or 2 for lavender or blue. See
[Stage 3.5 acceptance and commands](TESTING.md#stage-35-fluffy-jumpers-and-residencytravel-regressions),
[offline model preview](FLUFFY-JUMPER-PREVIEW.png) and
[validation evidence](VALIDATION-MUSHROOM-VALE-STAGE-3.5.md).

## Stage 3.6: Living Mushroom Vale

The three Vale biomes reserve the natural `CREATURE` lists for magical wildlife
and the Wise Fly Agaric. Mooshrooms, pigs, rabbits, sheep, and any other vanilla
creature entries no longer naturally spawn there. This changes only future
natural spawning: existing animals are never killed, replaced, despawned by the
mod, or rewritten, and other dimensions retain their original animal lists.
Decor, cottages, landmarks, vegetation, fireflies (`AMBIENT`), and Wise cottage
residency are unchanged.

Prior surveys separated the causes. Stage 3.3 recorded **101 chunk-generation
births versus 7 runtime natural births** across six routes; the shared creature
cap was full in **999/1,320 (75.7%)** snapshots. The 400-tick runtime attempt
and broad vertical sampling limit eligible candidates, while rolling terrain,
foliage, and small silhouettes reduce line-of-sight sightings. Stage 3.2d also
observed a cap full in 100/100 snapshots on one forest route. Chunk-generation
spawns are distinct from runtime cap-limited attempts. Removing passive animals
improves future generated populations, but cannot free capacity in old chunks
where those animals are already alive.

For those existing-world cases, `ValeWildlifePopulation` provides a small,
server-side exploration assist in Mushroom Vale. It checks one moving
non-spectator player per Vale level at most once per 400 ticks, samples up to 12
positions in already-loaded chunks, and creates at most one nearby pack (up to
three members) per successful cycle. The path respects `doMobSpawning`, the
original biome spawn predicates, Forge placement/position/finalize hooks,
collision, player clearance, and the world border. Its local 48-block budget is
4 Pufiks, 4 Vale Spiders, 6 Fluffy Jumpers, and 12 total. Existing named or
persistent creatures count toward those limits and are never altered. It does
not use the shared category cap, change entity categories, or load chunks.
Normal biome spawning remains active too; these limits bound only assistance.

New chunks use the exclusive lists and revised weights/groups. Already generated
chunks keep terrain and existing animals, but use the revised biome spawn data
for later normal rolls and can receive bounded assistance while players explore.
Idle players do not trigger replenishment. Full GameTests and natural encounters
in Survival/BMC4 remain pending for this stage; see
[Stage 3.6 verification](TESTING.md#stage-36-living-mushroom-vale).

Stage 3.4b doorway correction: a block-centered 0.68-wide resident body clipped
the open spruce door panel by approximately 0.0074 blocks in the measured failing
approach (0.0275 blocks when perfectly centered). Navigation and movement control now share the aperture-centered
waypoint and advance each checked node in order; floor validation uses actual
bottom-slab elevation. All four cottage orientations are tested. The existing
residency UUID/ledger, Wise residents, wild Pufik behavior and approved Pufik art
remain intact.

## Stage 3.7 family portal (design Stage 4)

Implementation status: implemented. The developer reports Stage 3.7 GameTests passed
and gateway appearance, activation and entry worked in local `runClient`.
Dedicated BMC4 acceptance is postponed. Stage 3.7b build and all 245 required
GameTests passed; the user confirmed local manual acceptance of both gateways. Real multiplayer remains **NOT RUN**.
This implements the family portal from design Stage 4;
the music quest chapters remained deferred at that stage. User instructions, the exact recipe and
entry/exit steps are in [FAMILY-PORTAL.md](FAMILY-PORTAL.md). Deployment and real
restart acceptance are in [TESTING.md](TESTING.md#stage-37-family-portal-on-an-existing-dedicated-bmc4-save).

### Gateway and ownership contract

Additive block/item/block-entity IDs: `dudunka:family_gateway`. The craft uses five
vanilla stone, a red mushroom, a brown mushroom and an amethyst shard. It needs no
Vale resources or new Overworld generation. A fixed north/south gateway occupies
twelve cells (3 wide along X, 4 high, 1 deep). Placement preflights a loaded 3 × 3
sturdy floor and air above it for four blocks, world border/build limits, player
placement permissions and entity clearance. Even replaceable vegetation is
rejected. Forge's normal multi-block placement event supports territory veto and
snapshot rollback without consuming the item. Breaking the structure drops only
the root item and removes loaded matching frame pieces; no player blocks are
removed. The item does not carry unlock NBT when broken/replaced.

Stepped mushroom caps, white spots, stems, moss footing and a small turquoise
crystal use vanilla textures and native voxel models. Inactive gateways have an
empty opening and light level 2; an active gateway has a translucent pink veil,
colored motes and light level 12. Colored particles supply the pink/violet/turquoise
palette; ordinary Minecraft block light itself has no RGB color. No new renderer,
dependency, mixin, entity type, packet or album protocol is required.

Main-hand right-click starts a 60-tick ritual only for a living, non-spectator
player beside a complete Overworld gateway. `FamilyGatewayRules` holds the tuning
constants: adult `stage == 2`, trust ≥ 20, all three pair friendship scores ≥ 10,
and spherical radius 8 from the root centre. Exactly one live, loaded, owned
companion per kind is selected. Carried, removed, dead, distant and other-dimension
entities cannot substitute. Repeated kinds, repeated UUIDs, noncanonical entity
instances, loaded UUID duplicates across levels and conflicting indexed identity
are rejected. The registry is an identity cross-check, never a source for spawning,
recovering or loading companions. Missing conditions go privately to the player's
localized chat with the relevant names and current/required scores.

The friends face the gate and use existing WAVE/PURR/CURIOUS poses, connected by
colored particles, while remaining at their locations. A temporary goal reserves
them without changing home/follow/stay assignments or introducing activity ordinals.
One gateway processes one ritual; a friend cannot join two rituals at once.
Eligibility and participant UUIDs are checked during the scene. Departure, unload,
restart, a broken gate or a missing participant cancels incomplete progress.
Trust and friendship are read without mutations, penalties or offerings.

The bottom centre block entity stores `AwakenedOwners`, a list of UUID compounds
in normal chunk NBT. There is no destructive migration or new global family save
format. Malformed owner rows are ignored; duplicate rows collapse. Completed
access is per owner **and per placed gateway** and survives normal save/reload,
reconnect and server restart. The visible active state shows that somebody has
awakened this gate; it does not grant access to another owner. That owner needs
their own family ritual. Removal/replacement of the gate clears that gate's access.

### Travel, return and existing saves

Historical Stage 3.7 behavior in this section is superseded by
[Stage 4.0](#stage-40-scope) for family travel; its old protocol number belongs
to that historical stage. Current album protocol remains 10.

A subsequent explicit interaction authorizes the awakened owner's player-only
trip; family presence is no longer required. Collisions never trigger travel.
The gateway and existing opt-in developer commands share `MushroomVale` travel
checks and `MushroomValeReturns` bookmarks, retaining their previous file/NBT format.
`dudunka:mushroom_vale`, all existing biome/entity IDs and protocol **9** are unchanged.
The developer route retains its JVM opt-in and permission checks.

With `-Ddudunka.devMushroomVale=true` on the local server JVM, an operator in the
Overworld can run `/dudunka_dev mushroom_vale prepare_family` once per player per
world. It creates a nearby adult, trusted Dudunka/Marusya/Syusya test family and
seeds their pairwise scores in `FamilyFriendships`; it does not unlock a gateway.
The command refuses when one of that player's companions is already within 16
blocks, when no safe dry positions are available, or after a previous successful
run. It never edits an existing companion or terrain. This route is unavailable
unless the explicit JVM property is enabled.

`/dudunka return` is always registered, needs no operator permission, and only works
from Vale. A clickable localized hint appears on gateway entry and login in Vale.
It provides an exit anywhere, including after reconnect, after a server restart,
or after the departure gate is broken. It first checks the saved departure area,
then the safe Overworld spawn fallback. No landing means no move and no bookmark
consumption. Forge travel veto preserves the current location and old bookmark.
Entry also leaves both untouched if Vale is unavailable or its arrival is unsafe.
No terrain is manufactured or overwritten to force a successful trip.

Activation, nearby entity checks and placement never load chunks. Explicit travel
may normally load/generate its one destination chunk; return may check one saved
chunk and one fallback spawn chunk. Cold Stage 4.0 family transfer uses a temporary
region ticket while the selected destination reaches entity-ticking status; the
ticket is released on completion, cancellation or timeout and is never persisted
as forced. Searches remain bounded. Existing carrier, recovery, album, memories,
inventory behavior and other dimensions retain their own established behavior.
Companions are never transferred, cloned, reconstructed or respawned by the gate.

An existing BMC4 save needs the same new JAR on server/all clients and a full server
restart so Forge loads additive registrations. No Overworld regeneration, `/reload`
migration, playerdata rewriting or friendship rebalance is needed. Previously saved
Vale terrain/generator settings remain authoritative. GameTests cover serialization
boundaries; an actual dedicated server restart with players inside Vale must still
be accepted separately. See [VALIDATION-MUSHROOM-VALE-STAGE-3.7.md](VALIDATION-MUSHROOM-VALE-STAGE-3.7.md).

## Stage 3.7b physical return gateway

Implemented; build and all 245 required GameTests **PASS**.
The user confirmed local manual acceptance of both gateways on 2026-10-12.
Real multiplayer and dedicated BMC4 testing remain **NOT RUN**. Quests and music restoration remain deferred.

After a successful physical Family Gateway entry, `MushroomValeReturnGateway`
reuses a complete gateway in Vale's origin arrival chunk or attempts to place one
near the player's safe arrival. The existing `dudunka:family_gateway` block,
block entity, twelve-part shape, active models, light and particles are reused.
The dimension determines the purpose: Overworld gates retain their owner ritual;
complete Vale gates call `MushroomVale.returnPlayer` for the clicking player, with
no companion, trust, friendship, owner unlock or consumable requirement.
Collisions do not transfer players. Developer travel does not create gates.

The gate persists as ordinary block states and a root block entity in the Vale
chunk. Discovery reads only that already loaded chunk's block-entity positions,
including deferred saved NBT before live block entities are instantiated. Re-entry
and new visitors reuse it, including after NBT reload; a partial saved gate
prevents a second automatic frame and is left for manual repair/removal.
Completely removing a gate permits another safe placement on later physical entry.
New entry first checks six standing-player positions two blocks north/south of
the existing root, then the original bounded surface search. Gate blocks are
excluded as landing floors, so the mushroom frame is not an arrival platform.

Placement checks radius 0–15 in X/Z from arrival, following each column's terrain
height entirely inside chunk (0,0). At most 120 root candidates can pass the chunk-interior bounds.
Its loaded-area preflight checks the 3 × 3 sturdy, dry support, border, permissions
and entities. The actual twelve frame cells must be air. Replaceable, noncolliding
natural flora and floor-level moss carpet may remain in the gateway's unused
side-clearance cells; block entities, fluids, hazards and other colliding blocks
still reject a site. The player-placed gateway
retains its stricter air-only clearance rule. Additional standing-player safety
checks reject hazards at the root and require at least one safe terrain-relative
approach among six positions two blocks north/south of the frame. No terrain, plants,
fluids, containers or entities are replaced; no platform, chunk ticket or
background task is added.
The shared Forge snapshot transaction honors multi-place protection veto and
restores all twelve cells on failure without consuming any held item. A veto
ends the attempt; no further placement events probe other territory.

Placement occurs after successful transfer, so denied entry cannot mutate Vale.
If no safe site exists or placement is vetoed, entry and the player's bookmark
remain valid; a localized failure message explains that the return gateway was
not created and points to `/dudunka return`. A broken gate,
blocked approaches, unavailable saved destination, unsafe fallback or denied
return retain the existing safety/command behavior. Failed transfer never consumes
the bookmark. Every visitor uses their UUID-keyed entry in the unchanged
`dudunka_mushroom_vale_returns` SavedData; the shared block stores no destination.

No registry IDs, block properties, NBT keys/formats, player/companion UUIDs,
ownership, recovery, carrier, homes, friendship or protocol 9 are changed.
Existing saves obtain the gate on their next successful physical entry if a safe
site exists; there is no startup scan or destructive migration. Existing players
already in Vale retain `/dudunka return` and can obtain the gate on a later visit.
Normal save/reload persists the chunk and individual bookmarks. Minecraft still
saves chunks, playerdata and SavedData separately; partial backup/crash recovery
is not atomic. Actual restart, multiplayer and full BMC4 checks remain manual.

Coverage and remaining risks: [VALIDATION-MUSHROOM-VALE-STAGE-3.7b.md](VALIDATION-MUSHROOM-VALE-STAGE-3.7b.md).

## Stage 3.8 — Mystery of the Forgotten Arch

Implemented adventure: **«Тайна забытой арки»**. The existing family album now has
**«Тайна Грибной долины» / “Mystery of Mushroom Vale”**. This is the bridge between
the completed family portal and the later music-restoration story. Stage 4 music
restoration remains deferred. Validation and publication have separate statuses in
[the Stage 3.8 report](VALIDATION-MUSHROOM-VALE-STAGE-3.8.md).

### Play together

1. Bring your Dudunka within 32 blocks. Stay together for up to five seconds,
   then open the album's **Vale mystery / Тайна долины** tab. She shares an
   illustrated legend about a mossy mushroom arch.
2. Grow all three friends and gather them nearby. The next chapter explains
   cooperation, trust 20 for each adult and friendship 10 for every pair.
3. Visit an Overworld forest. After the next observation, reopen the album for
   forest clues and **Listen to the forest / Прислушаться к лесу**. Explore forests
   you have not visited before. An old arch has the same mushroom frame as your
   home gateway, with mossy ruined stones, moss carpets and vines.
4. Bring your three eligible adults within eight blocks of either an ancient arch
   **or a crafted gateway**. Right-click, stay nearby for three seconds, and keep
   the illustrated awakening memory. No one owns a naturally generated arch until
   their family completes its own ritual; other players need their own family.
5. Right-click again from safe ground to visit Mushroom Vale. Its new album picture
   introduces Pufik, the Wise Fly Agaric, fireflies and fluffy jumpers. Return through
   the physical return gateway, or use `/dudunka return` from anywhere in the Vale.

No arch nearby? Keep exploring new forest land and listen again, or craft a gateway
at home from five stone, both mushrooms and an amethyst shard. Both routes share
all safety, friendship and personal return-bookmark rules. Old placed gateways and
old albums work too. Previously unlocked gateway owners gain the awakening chapter
on their next actual interaction. An observed dimension entry or login in Vale also
records the visit, including players already there after an upgrade. Old family
memories are not guessed or overwritten.

### Generation and save contract

Additive native structure type, structure and piece ID:
`dudunka:forgotten_mushroom_arch`; structure set:
`dudunka:forgotten_mushroom_arches`. Forge's ordinary structure registry/start/
reference pipeline places a single surface piece during chunk generation.
`#dudunka:has_structure/forgotten_mushroom_arch` includes `#minecraft:is_forest`;
the placement also checks the actual forest biome and Overworld dimension.
Random spread uses spacing **24**, separation **8**, salt **38170841**: one potential
start per 24×24 chunk region, further filtered by biome and safe terrain. This is
not a promise of an arch in every forest. World structure generation must be enabled.

The piece fits wholly within its owning chunk and has no terrain adaptation.
It attempts at most 64 terrain-relative sites in that unfinished chunk. Actual
writes require air, a natural dirt-family sturdy floor, dry clearance, world-border/
build bounds and no block entities. It neither clears trees/builds nor flattens
terrain. It refuses ordinary ServerLevel placement, legacy below-zero upgrading chunks and
chunks at FEATURES or later;
there is no chunk-load, login, startup, tick or `/reload` placement callback.
Existing saves gain arches only as new chunks generate. Old generated chunks,
including built areas, are never automatically retrofitted. Adjacent old chunks
are not part of the write footprint. Other worldgen can obstruct a potential site;
that start may then remain empty rather than overwrite another mod's work.

Frame blocks are the unchanged `dudunka:family_gateway`; optional root NBT boolean
`ForgottenArch` marks natural origin for discovery. Missing means an ordinary old
or crafted gateway. `AwakenedOwners` and all companion/bookmark formats are unchanged.
Breaking and re-placing the dropped item creates an ordinary crafted gate with no
inherited owner access or natural-origin marker.

### Bounded guidance and progression

The locator is a local echo, not vanilla's potentially generating `/locate` search.
Only on an explicit album request does the server inspect up to **13×13 already
loaded chunks** around the player via `getChunkNow`, and at most **64 block-entity
positions per chunk**. It reports only a complete, live, natural-marked frame
inside the border. It generates/loads no chunks, adds no tickets, predicts no
unexplored coordinates and stores no stale destination in SavedData. A broken or
unloaded frame cannot be returned by a fresh search. No echo means no verified
local target, not proof that a distant or unloaded forest has no arch.

The album shows verified coordinates, a direction and approximate distance as a
snapshot of the last listen. Moving or editing the area requires listening again.
Wrong-dimension, locked-story, not-yet-searched and no-echo states have child-readable
feedback. Session UUID, authenticated player, held album, dimension, life/spectator,
monotonic request and rate checks remain server-side. Search also has a five-second
session cooldown; no client sends a target coordinate or chapter unlock.

Progress lives in new Overworld SavedData `dudunka_vale_mystery`, keyed by player
UUID. Five sequential chapters persist monotonically; a real later event retains
its introduction too, so the crafted route never requires natural discovery.
Malformed rows cannot unlock chapters; duplicate owner rows retain the greatest
valid count. Existing `dudunka_family_memories`, requests, notes and ownership
records are untouched. Future chapter titles/pictures are hidden behind a generic
waiting page until their event occurs. Reading the album alone awards no events.

**Album protocol 10** is required because the private snapshot now includes the
chapter count and typed optional locator result, plus a new explicit request.
It is intentionally incompatible with protocol 9 clients; update server and all
clients together. Historical Stage 3.7 reports correctly retain protocol 9 for
that published source. The mod version/JAR release has not been incremented.

## Stage 4.0 scope

Safe explicit family entry/return is implemented separately from future Stage 4
adventure work. See [Family portal](FAMILY-PORTAL.md), [transfer semantics](FAMILY-TRAVEL.md)
and [validation](VALIDATION-FAMILY-JOURNEY-STAGE-4.0.md). The five Stage 3.8 album
chapters, awakening ritual, protocol 10, worldgen and music remain unchanged.

## Stage 4.1 — native adventure landmarks

Five additive native `Structure` entries use shared type/piece
`dudunka:vale_adventure_landmark` and StructureSet
`dudunka:vale_adventure_landmarks`. They are discoverable by vanilla
`/locate structure`; they are distinct from the older decorative placed-feature
mushrooms and cottages.

| Structure ID | Eligible biome | Visual identity |
| --- | --- | --- |
| `dudunka:music_bells` | `dudunka:glowing_mushroom_forest` | Three turquoise/violet/golden mushroom bells of different heights, with glowing undersides |
| `dudunka:jumper_dance` | `dudunka:pink_meadows` | Terrain-following allium/tulip circle, four glowing mushroom posts, open dance space |
| `dudunka:memory_stone` | `dudunka:memory_forest` | Crying-obsidian monolith, amethyst crest and four outlying crystal stones |
| `dudunka:silver_web_harp` | `dudunka:memory_forest` | Nine-block silver quartz frame, glass strings and amethyst web crosspieces |
| `dudunka:singing_mushroom` | `dudunka:glowing_mushroom_forest` | Giant golden bell canopy above a concert clearing and crescent of stone seats |

The shared random-spread set attempts **one** of these landmarks per **10×10
chunk region** (160×160 blocks), separation **4**, salt **41410731**, equal
weights. Vanilla retries the other entries when a chosen entry is biome/site
ineligible. At most one of the five starts occurs per region; the two forest
biomes split their accepted starts between two landmarks. This is an attempt
frequency, not a guaranteed density or maximum travel distance. Water, slopes
and biome boundaries reduce actual frequency; seed-dependent exploration and
BMC4 visual acceptance remain separate from automated discovery evidence.

Start selection requires the Vale's noise-settings key and the exact intended
biome throughout a 13×13 footprint. All noise-surface columns must be dry, above
sea level, and within two blocks of each other. Geometry fits inside one chunk
with an empty margin, follows the saved terrain profile, and places supported
posts down to the existing surface. There is no terrain adaptation, flattening,
excavation, water filling, cross-chunk writing or background chunk loading.
Structures run before vegetation; existing feature preflights reject overlapping
solid landmark geometry. Nearby vegetation can still decorate unused air.
Neighboring chunks can decorate before the owning chunk; predicted starts are
not evidence that placement ultimately succeeded.

Placement additionally checks the actual `dudunka:mushroom_vale` dimension,
world border, build height, writable generation region, unfinished/non-upgrading
chunk, soil support, fluids, block entities and the entire air/plant volume.
Trees and builds reject the whole placement; only small `BushBlock` vegetation
may be replaced at blueprint positions. Rejected attempts in new chunks invalidate
their native start and refresh vanilla's presence cache; failed writes restore
this attempt's touched cells. Ordinary exploration uses vanilla chunk generation.
The five exact `/locate structure dudunka:<music ID>` commands finish native
candidate chunks and verify all blueprint blocks before returning the saved
anchor X/Z, with at most 16 physical candidates and vanilla's 100-region search
bound. Other IDs/tag selectors retain vanilla behavior. These landmarks install
no persistent forced chunk tickets. Old missing/damaged starts are skipped by
these five commands without rewriting old starts, terrain or chapter progress.

Each piece persists its kind, root `Anchor` and 169-column `Ground` profile in
native structure NBT. A visible chiseled-stone-brick block at that root is the
reserved Stage 4.2 interaction anchor. It has no quest/block entity state yet;
there are no music puzzles, new chapters, progression or added sounds here.
Version remains **0.27.1-alpha**, album protocol **10**. Cottages, Forgotten
Arch, gateway state and Stage 4.0 travel code retain their existing IDs/behavior.

Existing completed chunks are never retrofitted. Structures require enabled
world structure generation and new chunks with the current Vale generator.
Legacy flat Vale generators do not gain these starts. Datapacks that clone the
Vale noise settings/biomes into another dimension are unsupported: Minecraft's
start-generation context has no dimension key, so such a clone could expose
starts to `/locate`, but the placement dimension guard refuses writes. Changed
worldgen or third-party structures may invalidate an otherwise predicted site;
placement prioritizes preserving terrain/builds. Full modpack verification is
required before claiming all third-party conflicts absent.

The 2026-10-14 source investigation confirmed that the previous placement early
returns could leave valid, saved, empty starts for all five IDs. The exact cause
at the two manually reported Bells sites still needs seed/site evidence. New
native-generation regressions and the read-only developer `inspect_music x z`
command are documented in [native landmark validation](VALIDATION-MUSHROOM-VALE-NATIVE-LANDMARKS.md).
Build, GameTests and manual acceptance for this correction are **NOT RUN**.

Commands and acceptance: [Stage 4.1 validation](VALIDATION-MUSHROOM-VALE-STAGE-4.1.md).


## Stage 4.2a — Where Did the First Note Go?

Implementation binds existing Stage 4.1 blocks to the saved native
`dudunka:music_bells` piece in an already loaded chunk of Mushroom Vale. It does
not change world generation or retrofit chunks. An intact central chiseled-stone
root must exist, and clicked blocks must match the piece’s blueprint; identical
decorative mushrooms outside that native start do nothing. Root-relative bell
centers are turquoise **(-3, 0)**, violet **(0, 2)** and golden **(3, -1)**.
Their existing stems, caps and underside lights can be played. The central root
and the amethyst listening marker at **(0, 1, -3)** replay the clue.

On approach (within 12 blocks), a private localized message explains right-click
interaction. Dudunka invites the player to listen to Pufik’s clue; the crystal
plays **turquoise → violet → golden**, low → middle → high. Vanilla harp notes
use pitches **0.75, 1.0, 1.25**, volume **0.45**, 12 ticks between demonstration
notes. Private note particles identify the sounding bell, with no light blocks,
terrain changes, damage or forced chunk tickets. RU/EN text also spells out the
colors/order for players who cannot hear the clue. No natural Pufik or companion
entity is required, summoned, moved or modified.

Correct notes advance the player’s own attempt. Wrong order gently resets it;
the player can immediately begin again, without a timer, consumable, combat,
penalty or reward farming. Completing the sequence plays the melody and stores
completion once. Bells remain playable afterward and the crystal can replay the
melody. Listening again resets only the incomplete attempt. Logout, dimension
change or leaving the area discards partial notes; completed chapters persist.
Attempts cannot combine notes from different landmarks.

New Overworld SavedData **`dudunka_vale_music`** uses a `Version` envelope and
`Owners` rows keyed by player UUID. Each row has a `Chapters` compound keyed by
stable resource IDs, starting with `dudunka:music_bells` / `Completed` boolean.
Stage 4.2b adds `dudunka:jumper_dance`; the remaining three landmark IDs
are reserved for later chapters. Unknown envelope/owner/chapter data
and future version values survive round-trips; duplicate owner rows cannot
revoke valid completion. Attempts/playback are transient, and idempotent replay
does not dirty completion data. Existing Mystery progress, album protocol **10**,
entity/home/travel formats and version **0.27.1-alpha** remain unchanged.

Broken/replaced bell blocks are inactive; breaking the root disables the site.
The quest never repairs or overwrites shared player builds. Restore those blocks
manually or use another intact native bells landmark. Client audio/particle
settings and resource packs can affect presentation. SavedData uses ordinary
Minecraft saving, not a transaction across unrelated world files.

Validation and short runClient acceptance: [Stage 4.2a report](VALIDATION-MUSHROOM-VALE-STAGE-4.2a.md).


## Stage 4.2b — Dance of the Fluffy Jumpers

Marusya narrates the fluffy jumpers’ dance at the existing native
`dudunka:jumper_dance` flower ring. Neither Marusya nor naturally spawned jumpers
are required, spawned, moved or changed; their involvement is narrative.
All existing alliums and pink tulips become playable, each with a distinct gentle
vanilla harp pitch and private note/sparkle particles. No geometry, assets,
registrations, world-generation data or shared blocks are changed.

Right-click any central carved-stone pillar block to watch six demonstrated taps:
**north twice → east once → south twice → west once** (two hops, bow, two hops,
bow). These four flowers lie at root-relative X/Z **(0,-5), (5,0), (0,5), (-5,0)**,
using the blueprint’s actual terrain-following Y. Demonstration taps are one
second apart; text names the directions and explains them. The player’s input
has **no timing requirement**. Repeat taps on the same flower and spatial tap
groups distinguish the dance from Chapter 1’s ascending three-bell melody.
Other flowers sound too; a different sequence simply invites an immediate retry.
Listening again restarts only that player’s unfinished dance. Private approach
text discovers the interaction within 12 blocks. RU/EN text and visual clues
support playing without audio; audio supports following the sparkling flowers.

Chapter 1 must already be complete before any dance input can advance Chapter 2.
The existing `dudunka_vale_music` ledger adds `Chapters/dudunka:jumper_dance/Completed`
per UUID, preserving unknown fields, existing music progress and duplicate-row
monotonic completion. Bell and dance attempts are separate and transient, bound
to one native anchor. Logout, dimension change or leaving the site clears only
partial dance state. Completed players can freely play every flower and replay
the demonstration without dirtying progress or receiving duplicate rewards.
No reward item is introduced.

Binding checks the already-loaded Vale native start, intact root and exact
blueprint state. Decorative copies, replaced flowers, offhand clicks, spectators
and unloaded chunks cannot advance it. Broken flowers/root are not repaired;
use another intact naturally generated site. No chunks are forced or searched
through generation. Mystery chapters, family travel, version **0.27.1-alpha**,
protocol **10** and the album UI remain unchanged. Chapters 3–5 remain deferred.

Validation and manual acceptance: [Stage 4.2b report](VALIDATION-MUSHROOM-VALE-STAGE-4.2b.md).


## Stage 4.2c — The Forest That Remembers

Chapter 3 is bound to the saved native `dudunka:memory_stone` piece in an
already loaded Mushroom Vale chunk. Right-click the bottom-center crying obsidian
block, just above the mossy base (`anchor + (0,1,0)`), to start a private,
repeatable listening scene. Its north and south faces are exposed; the hidden
chiseled-stone anchor still identifies the saved site. Its patient story follows
Syusya home by firefly light, with the Wise Fly Agaric narrating and Syusya reflecting on
kindness. Three short chimes and private firefly-like particles mark the
memory. The player then interprets one of three existing blueprint symbols:
the single crest crystal, the paired side crystals, or the little corner
lights. The crest remains symbol 0, the paired crystals symbol 1, and the
corner lights symbol 2: the correct shared-light interpretation. The listening
block is separate from all choice targets.

Both earlier music chapters must be complete before an interpretation can
restore the forest voice. Listening is explicit; choices before listening,
at another anchor, or before the prerequisites are met cannot advance. A wrong
choice gives Syusya's gentle hint and leaves the player free to listen again or
retry indefinitely. Correct completion is saved once in the existing
UUID-keyed `dudunka_vale_music` ledger under
`Chapters/dudunka:memory_stone/Completed`. Listening state is transient and
private. Completed players can replay the story without changing saved data.

The resolver requires the loaded native structure start, the original anchor,
and every block in its saved Stage 4.1 blueprint to match. A missing or changed
piece disables the interaction without repair. No chunk is forced, no terrain
or landmark block is changed, and naturally spawned fireflies or Wise Fly
Agarics are not required. Feedback is private to the interacting player. Album
protocol **10**, version **0.27.1-alpha**, the five Mystery chapters, family
travel, prior music chapters and soundtrack remain unchanged.

Validation and manual acceptance: [Stage 4.2c report](VALIDATION-MUSHROOM-VALE-STAGE-4.2c.md).


## Stage 4.2d — The Secret of the Silver Web

The friendly mushroom spiders have woven a harp that can join the bell melody,
the fluffy jumpers’ dance and the forest memory. Dudunka offers the deep bell
voice, Marusya the high dancing voice, and Syusya the quiet middle voice. Text
encourages bringing all three companions to enjoy the scene together, without
requiring their presence, AI movement or exact positioning. Nearby real peaceful
`vale_spider` entities receive optional private sparkle applause at a successful
chord; missing spiders never block progression. The woven clue speaks for its
makers even when no spiders have spawned.

The existing native harp has three interactive string columns at root-relative
X **−2 / 0 / +2**, Z **0**, Y **1–6**. Other glass columns remain decorative.
West is decreasing X; east is increasing X. Right-click a string to cycle
**low → middle → high → low**. All three start at middle. Their current tones
have explicit RU/EN names and **1 / 2 / 3** vertically separated white sparkles;
no pitch or color recognition is needed. Each feedback line also names the
string’s chapter and its desired tone. The spiders’ clue asks for **west low,
middle high, east middle**. Strings can be adjusted in any order, with no
sequence, memory choice, rhythm or time limit.

Right-click the existing central chiseled-stone anchor to read the full family
clue, hear all three selected tones together and explicitly check the harmony.
Correct tuning restores the Silver Web’s voice. A mismatch gently suggests
comparing current and desired tones, preserving every setting. All three earlier
chapters must be complete before tuning or checking. No item, combat, spawned
inhabitant or companion positioning is required.

Completion is saved once under `Chapters/dudunka:silver_web_harp/Completed` in
the existing UUID-scoped `dudunka_vale_music` ledger. Tuning is private, transient
and bound to one saved anchor per player; switching harps starts fresh middle
tones. Logout, death, spectator mode or dimension change clears transient
settings. Leaving the area does not clear the selected tones. Save/reload keeps
completion, while unfinished tuning starts again at middle. Completed players
can retune freely and play both matching and mismatched chords without changing
persistent progress or duplicating completion.

Resolution uses only the loaded native `dudunka:silver_web_harp` start and its
stored anchor, with the entire blueprint checked against actual blocks. Damaged
or incomplete structures and decorative copies are inert; there is no repair,
shared block state, terrain edit, forced chunk loading or entity AI mutation.
All quest text, chords and effects go only to the interacting player. Existing
ambient music, Chapters 1–3, Mystery, travel, worldgen, album protocol **10** and
version **0.27.1-alpha** are preserved. Chapter 5 and album UI remain deferred.

Validation: [Stage 4.2d report](VALIDATION-MUSHROOM-VALE-STAGE-4.2d.md).
Manual runClient/BMC4 acceptance is intentionally deferred until the full music
quest chain is implemented.


## Stage 4.2e: A Song for All Friends / «Песня для всех друзей»

The fifth chapter concludes **Restore the Music of the Valley** at the native
`dudunka:singing_mushroom` in Glowing Mushroom Forest. Chapters 1–4 must be
complete. Discovery gives a private invitation; right-click the original central
carved stone to explicitly start a 440-tick (about 22-second) concert. Repeated
clicks during playback do nothing, so the song cannot stack, restart or skip.

Dudunka and Pufik’s three ascending bell notes open the song. Marusya’s paired
fluffy-jumper rhythm follows, then Syusya’s shared-light memory with the Wise Fly
Agaric and fireflies. The friendly spiders’ low/high/middle harp chord introduces
the reunion. A short interwoven reprise and final chord celebrate the player and
family bringing the Valley’s music back. RU/EN narrative names all participants
without pretending absent NPCs are physically present. Nearby real owned family
and inhabitants optionally receive private applause sparkles; no AI, emote,
owner, home or entity state changes. No NPC presence or precise positioning is
required. Existing vanilla harp and amethyst sounds accompany restrained private
colorful notes and light particles; the ambient soundtrack is untouched.

`ValeConcertQuest` verifies the persisted native start and every blueprint block
at activation and each active tick, using loaded chunks only. Seats, the secondary
amethyst marker and giant stem are decorative. Missing/damaged pieces fail
closed without repairs. Moving beyond 16 blocks, death, spectator mode, logout,
dimension change or reload discards playback. Nothing is lost: earlier chapters
remain saved, and another explicit click starts fresh. Completion is awarded
only after the entire concert at a still-valid site. Each UUID has its own score;
players can start and finish independently at the same mushroom.

The additive `Chapters/dudunka:singing_mushroom/Completed` flag lives in the
existing `dudunka_vale_music` ledger. Unknown metadata and duplicate-row monotonic
completion are preserved. Subsequent clicks replay the whole song without save
mutation or repeat rewards. No terrain, biome, native geometry, registries,
dependencies, album UI/protocol **10** or version **0.27.1-alpha** change.

See [Stage 4.2e validation](VALIDATION-MUSHROOM-VALE-STAGE-4.2e.md) and
[manual music-chain acceptance](TESTING.md#stages-42ae-complete-music-chain).
Implementation and automatic validation are separate from manual acceptance.
Manual runClient/BMC4 testing remains **NOT RUN**, intentionally deferred by the
user for this milestone until the five chapters are ready for manual review.

Stage 4.2e automatic evidence: build/model/resource checks **PASS**, focused music
**40/40 PASS**, full suite **312/313 PASS with 1 unresolved failure** in the
existing live flower-walk test. All music tests and native concert-anchor checks
passed. Full regression acceptance remains pending; see the validation report.

## Stage 4.3a — visual polish for the first two music landmarks

Newly generated `dudunka:music_bells` uses three distinct gilled bell silhouettes
and static glowing clappers in a mossy, crystal-and-spore clearing. Newly generated
`dudunka:jumper_dance` replaces its vanilla flower ring with four modeled fantasy
blooms, soft glowing centers and a moss/quartz center composition. Quest targets,
flower voice order and all Stage 4.2 mechanics stay unchanged.

Native pieces save a visual revision; pieces without it retain their old geometry
and interactions. There is no retrofit of existing worlds. One block with four
states/models is added, reusing existing textures. No version/protocol, audio,
other music landmarks or Stage 4.3b changes are included. Automated and manual
acceptance are **NOT RUN**. See [Stage 4.3a validation](VALIDATION-MUSHROOM-VALE-STAGE-4.3a.md)
for the stage's file list, compatibility details and external validation commands.

## Stage 4.3a.1 — fantasy visual refinement

New first-two native pieces use visual revision 2: smaller modeled bell mushrooms
with thin stems, hanging luminous gills and spores; volumetric flowers with Forge
fullbright centers and real block light 10; richer natural clearings and a connected
bell listening alcove. Original low bell targets, both clue stones, the complete
flower ring and six-tap pattern are retained. Saved revisions 0/1 keep their layouts;
shared existing flower models/light improve without a terrain retrofit. Decorative
loss no longer invalidates the otherwise playable first-two puzzle integrity checks.

A loaded, simulated music clearing near a player can receive a bounded top-up to
three existing Vale fireflies at evening/night. Assistance respects mob-spawning
rules, server animal spawning, Forge vetoes, safe volumes and the existing population,
without chunk tickets or persistence flags. Quest completion is not required for
ambient wildlife. Other landmarks, music progress, version and protocol are unchanged.

Build/GameTests and graphical acceptance of this refinement are **NOT RUN**.
See [Stage 4.3a.1 handoff](VALIDATION-MUSHROOM-VALE-STAGE-4.3a.1.md).
