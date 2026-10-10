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
scale 1, Overworld sky/effects/infiniburn, fixed noon (6000), skylight, no ceiling,
normal temperature, zero ambient light, working beds, disabled respawn anchors,
and disabled raids. Monster light limits use vanilla Overworld values (block
light 0, uniform sky light threshold 0–7). The dimension and type IDs are stable
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
