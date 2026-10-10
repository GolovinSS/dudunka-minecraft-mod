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
