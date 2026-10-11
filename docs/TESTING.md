# Приемка в Better MC v55.5

## Редкие реплики Дюдюньки

1. Запустите отдельный тестовый мир через IntelliJ **Gradle → `runClient`**. Возьмите свою Дюдюньку и оставайтесь рядом в обычном режиме; после редкой паузы она должна безопасно подойти, посмотреть на игрока, выполнить WAVE и отправить одну приватную реплику с именем.
2. Проверьте русскую и английскую локализации. Без Маруси возможна реплика «Я уже большая!»; реплика про туалет допустима только когда живая загруженная Маруся того же владельца находится в пределах примерно 8 блоков.
3. Для проверки приватности подключите второго игрока; сообщение должен получить только владелец Дюдюньки. Несколько его Дюдунек не должны дублировать реплику.
4. Проверьте команды «Ждать» и «Дома», сон/режим наблюдателя, уход владельца, восстановление и уже идущую семейную сцену. Диалог не должен начинаться или должен сразу завершиться; путь должен обходить препятствия, а при недоступном пути не должно быть телепортации или сообщения.

## Stage 2.4b: cottage architecture and usable benches in IntelliJ IDEA

This supersedes the Stage 2.4/2.4a appearance and non-interactive bench checks below. Graphical Minecraft
and full BMC4 v55.5 acceptance remain **pending**.

1. IntelliJ: **Settings → Build, Execution, Deployment → Build Tools → Gradle
   → Gradle JVM: Java 17**. Open **Run → Edit Configurations → + → Gradle**;
   select this project and set **Tasks and arguments** to
   `runClient -PmushroomValeDev=true`. Launch it with normal client assets
   (do not use `-x downloadAssets`). Alternatively, the generated Minecraft
   Client application configuration needs VM option
   `-Ddudunka.devMushroomVale=true`; set it on the server JVM too for multiplayer.
2. Create a **NEW disposable Creative world**, cheats ON, seed `42`. Leave
   companions at home and the carrier empty. Stand on safe ground, then run
   `/dudunka_dev mushroom_vale enter`. The commands require permission level 2
   and the JVM opt-in; they are absent on ordinary servers. They do not add a
   portal or move companions.
3. For guaranteed inspection, explicitly create this temporary test platform
   **inside Vale in that disposable world**. The fill commands change only
   the requested test area; the cottage command itself never clears terrain:

   ```text
   /fill 0 159 0 47 159 31 minecraft:mycelium
   /fill 0 160 0 47 169 31 minecraft:air
   /tp @s 24 160 24
   /dudunka_dev mushroom_vale cottage pufik 8 160 8 north
   /dudunka_dev mushroom_vale cottage wise 32 160 8 north
   ```

   `X Y Z` is the cottage floor origin, not the ground beneath. Stand outside
   its footprint. Both cottages now exist at known coordinates without
   searching naturally rare flat sites. The direction is explicit:
   `north`, `east`, `south`, or `west`. To inspect another rotation, clear the
   same disposable platform with the second fill command and place again.
   North faces negative Z. Collect any dropped items before repeating;
   the preview refuses live entities, including dropped item entities.
   Repeated placement over a cottage must refuse.
   Preview placement bypasses biome eligibility/rarity but retains the entire
   dry/level/clear footprint, border, build-height and loaded-chunk checks.
   It also refuses any live entity in the footprint. No background search,
   persistent chunk loading or normal frequency changes occur.
4. Inspect Pufik's small spotted cap, rounded crown, gills, cream stem, carved
   doorway, amber windows and flower porch. Open/close the ordinary spruce
   door: only the central opening is passable, with full walls beside and above it.
   Inspect Wise's elongated timber library, asymmetric study bay, taller offset
   cap, covered porch, interior bookshelves, lectern and slatted reading bench.
   Use shaders OFF first. Check missing textures, overlapping faces, rotation,
   silhouette from all sides, inventory models and save/reload persistence.
5. Inspect standalone props on the same platform:

   ```text
   /place feature dudunka:mushroom_vale/lantern_post 4 160 24
   /place feature dudunka:mushroom_vale/direction_marker 10 160 24
   /place feature dudunka:mushroom_vale/bench 16 160 24
   /place feature dudunka:mushroom_vale/mushroom_picnic 30 160 24
   /place feature dudunka:mushroom_vale/flower_bed 37 160 24
   /place feature dudunka:mushroom_vale/cozy_cluster 43 160 24
   ```

   Move away from target footprints. These vanilla commands choose random
   cardinal rotations. Check slats/feet, framed lanterns, spotted tables and
   stools, two arrow boards, flower planters and book stacks. Right-click each
   bench block to sit; Shift dismounts. Each block is one seat. With two clients,
   attempt the same seat simultaneously, then adjacent seats. Check pelvis
   height, initial facing, free looking, feet, and clear dismount positions in all
   four directions. Break an occupied bench, disconnect/reconnect, change
   dimensions, and save/restart while seated: no invisible mounts should remain.
   Test repeated clicks and blocked overhead clearance. Marker/book interaction
   remains decorative. F3 block-light readings
   should show light near lanterns/windows. Daylight can hide the effect:
   for a shaded comparison, `/fill 0 170 0 47 170 31 minecraft:stone`, then
   remove the temporary canopy with the same command using `minecraft:air`.
6. Walk through/around each build and the one-block perimeter, including with
   Syusya using the existing supported companion mechanics. Test two clients:
   non-operators cannot run the preview; operators can. Test no JVM opt-in,
   placement outside Vale, occupied/wet/sloping sites and occupied entities.
   Refusal must leave blocks intact. `/dudunka_dev mushroom_vale return`
   retains the existing safe return behavior.
7. Separately explore NEW natural chunks in all three biomes at seeds `42`,
   `0`, `-71021`, then full BMC4 v55.5. Existing frequencies and cottage biome
   restrictions are unchanged. `/locate structure` cannot find these placed
   features; previews establish appearance only. Record natural density,
   clipping, routes, save/reload, multiplayer and shader/resource-pack results.

Regenerate with `python3 tools/generate_vale_polish.py`, then
`python3 tools/generate_vale_decor.py`. Optional offline geometry sheets:
`python3 tools/preview_vale_decor.py` → `build/reports/mushroom-vale-stage24a/`.
They approximate vanilla blocks and lighting; they are not game screenshots.
Results: [Stage 2.4b validation](VALIDATION-MUSHROOM-VALE-STAGE-2.4b.md).

Shell equivalent for the dev client (Java 17, normal client assets):

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew runClient -PmushroomValeDev=true
```

For fixed standalone bench orientations (same disposable platform):

```text
/setblock 14 160 24 dudunka:vale_bench[facing=north]
/setblock 16 160 24 dudunka:vale_bench[facing=east]
/setblock 18 160 24 dudunka:vale_bench[facing=south]
/setblock 20 160 24 dudunka:vale_bench[facing=west]
```

After every rider has dismounted (allow one server tick), this loaded-entity
check should produce no message; while seated it should print one:

```text
/execute if entity @e[type=dudunka:vale_bench_seat] run say Bench seat exists
```

For cottage rotations, repeat the platform clearing command, collect door drops,
then use the two cottage commands above with `east`, `south`, and `west`.
Existing cottages in generated chunks retain their old blocks; newly placed
cottages use the corrected blueprints. Existing `vale_bench` blocks gain sitting.

## Stage 2.4: cozy decor and landmark cottages

Stage 2.2/2.3 testing and visual acceptance are confirmed by the user. The
following Stage 2.4 checks remain pending. Use Java 17 and ordinary client
asset download (do not exclude `downloadAssets` for a graphical run):

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew runClient -PmushroomValeDev=true
```

In IntelliJ use the same Gradle task/arguments and Gradle JVM 17.

1. Create a **NEW** Creative world with cheats and seed `42`; later sample `0`
   and `-71021`. Start with companions at home and an empty carrier. From safe
   ground record departure, then `/dudunka_dev mushroom_vale enter`.
2. Use `/locate biome dudunka:pink_meadows`,
   `/locate biome dudunka:memory_forest` and
   `/locate biome dudunka:glowing_mushroom_forest`. Fly to results or use
   `/execute in dudunka:mushroom_vale run tp @s X 140 Z` and descend.
   Explore NEW chunks over several thousand blocks. Record seed/coordinates
   and approximate feature counts: small props should be scattered, Pufik
   cottages restricted to meadows, Wise cottages to memory forest. These are
   rare features on flat dry ground; `/locate structure` cannot find them.
3. For reliable close inspection, find an open, level, dry site. In Vale run
   `/place feature dudunka:mushroom_vale/pufik_cottage X Y Z` with Y one above
   ground. Repeat for `wise_fly_agaric_cottage`, `lantern_post`,
   `direction_marker`, `bench`, `mushroom_picnic`, `flower_bed`, `cozy_cluster`.
   Largest site needs a 15×15 level grass/mycelium/podzol/moss floor and nine
   clear blocks above it; use a separate Creative test platform if needed.
   Forced placement bypasses placed-feature rarity/biome filters, so it cannot
   establish natural distribution. Placement outside Vale should refuse.
4. Check recognizable red spotted mushroom domes, cream walls, amber windows,
   warm lanterns, Pufik's small welcoming porch/flowers and Wise's larger roof,
   external shelves, empty lectern and reading bench. Inspect each orientation,
   supports, slab/trapdoor edges, lighting and missing textures with shaders
   off, then with the target BMC4 resource/shader setup. No NPC, quests, music
   or special sitting/marker interaction is expected.
5. Walk around all props and cottages in Survival. Check the open perimeter,
   porch half-steps and three-wide entrances; inspect neighboring vegetation,
   shores and slopes for obstructions. In a separate companion test bring
   Syusya with existing supported mechanics and walk routes around cottages,
   benches and tables. Developer travel still moves only the player. Record
   any inaccessible or overcrowded routes with seed/coordinates.
6. Save/quit/restart beside a cottage. Verify geometry/light, unchanged
   vegetation, safe travel/return and companion identity/state. Check an old
   save BACKUP without deleting chunks or editing `level.dat`.
7. Repeat natural density, traversal, save/reload and travel checks in full
   Better MC BMC4 v55.5 and dedicated-server multiplayer. Record acceptance
   separately from automated placement tests. Check logs for worldgen errors.

Automated sequence: `python3 tools/validate_vale_resources.py`,
`./gradlew build verifyModels -x downloadAssets`, then one final full
`runGameTestServer` using a fresh unused working directory. Reference checks
and character model baking do not establish visual quality of vanilla-block
builds. See [Stage 2.4 results](VALIDATION-MUSHROOM-VALE-STAGE-2.4.md).

## Stage 2.3: signature vegetation and landmarks

Graphical Minecraft and full BMC4 v55.5 acceptance are pending. Use Java 17:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew runClient -PmushroomValeDev=true
```

In IntelliJ use Gradle task `runClient -PmushroomValeDev=true`, Gradle JVM 17,
and allow ordinary client asset download. Headless validation excludes assets;
the graphical run needs them.

1. Create a **NEW**, separate Creative world with cheats and seed `42`; also
   sample NEW worlds with `0` and `-71021`. Leave the family at home and empty
   the carrier for the initial developer-route checks. Record departure, then
   `/dudunka_dev mushroom_vale enter`. Check F3 dimension and safe arrival.
2. Locate all three biomes from inside Vale:

   ```text
   /locate biome dudunka:glowing_mushroom_forest
   /locate biome dudunka:pink_meadows
   /locate biome dudunka:memory_forest
   ```

   Fly to the result or, in Creative, use
   `/execute in dudunka:mushroom_vale run tp @s X 140 Z`, then descend.
   Explore at least 2048 blocks in each direction and note seed/coordinates.
3. Glowing forest: turquoise flat caps and small mushrooms, cyan flowers,
   flecked glowing moss, spore bushes, red/brown giants, occasional warm golden
   caps and broader turquoise landmarks. Memory forest: violet domed mushrooms,
   broad violet landmarks, purple flowers/small mushrooms, sparse spruce and
   soft violet fog. Meadows: open grass, pink flowers/petals, sparse cherry,
   small golden mushrooms, rare golden giants and broad golden landmarks.
   Rarity is attempts, so a small sample may contain no landmark.
4. With shaders/resource packs off, check all cap surfaces, gills, stems,
   cutout plants and item models for missing textures/black planes. All twelve
   blocks are in the Family creative tab with RU/EN names. Inspect vanilla
   block light near caps/moss/flowers, including below caps: daylight can mask
   the glow. Check by day and night; shader/bloom appearance is a separate BMC4 check.
5. Walk Survival routes in each biome and across borders, including low domed
   mushroom skirts, slopes, shores and landmark undersides. Plants must not
   obstruct or slow walking. In a separate navigation check bring a following
   Syusya using existing supported travel/transport mechanics; verify she can
   route around stems and through flora. Developer travel moves only the player.
   Record any unavoidable blockage or excessively dense seed/coordinates.
6. On spare ground test support removal, block breaking/drops and placement of
   decorations on grass/mycelium/podzol/moss. These are environmental items:
   no food effects, interactions, growth, mobs, quests or music are expected.
   Optional controlled palette inspection: `/place feature
   dudunka:mushroom_vale/turquoise_landmark` on clear dry ground; repeat for
   `violet_landmark` and `golden_landmark`. Forced placement does not establish
   natural frequency or biome filtering.
7. Place markers beside all palettes; save/quit/restart and verify new blocks,
   light, biome IDs, markers and `/dudunka_dev mushroom_vale return`. Repeat
   the existing travel safety/permissions/fallback/multiplayer checks. On
   backup copies of old saves, check retained builds, companions, bookmarks
   and other dimensions without editing `level.dat` or deleting chunks.
8. Repeat in full BMC4 v55.5 and on a dedicated server with two clients. Review
   worldgen/registry logs, biome modifiers, resource packs/shaders, save/reload
   and traversal. Record manual acceptance separately from automated tests.

Offline preflight: `python3 tools/validate_vale_resources.py` (cached vanilla
1.20.1 client-extra JAR, or pass `--vanilla-jar PATH`). Then `build verifyModels`,
then one final `runGameTestServer`, with Java 17 and a fresh unused test world.
`verifyModels` covers character models; the preflight checks block resource
references, and client acceptance checks actual block rendering.

Architecture: [Stage 2.3](MUSHROOM-VALE.md#stage-23-signature-vegetation-and-giant-mushroom-landmarks).
Evidence: [Stage 2.3 validation](VALIDATION-MUSHROOM-VALE-STAGE-2.3.md).

## Stage 2.2: three Mushroom Vale biomes

Historical Stage 2.2 checklist; use Stage 2.3 above for current vegetation.

Manual graphical and full BMC4 v55.5 acceptance are pending. Use Java 17 and
`./gradlew runClient -PmushroomValeDev=true` (IntelliJ Gradle run arguments use
the same task/property). Allow the ordinary client asset task for this run.

1. Create a **NEW**, separate Creative world with cheats, initially seed `42`;
   repeat with `0` and `-71021`. Leave companions at home and empty the carrier.
   Record safe departure coordinates and use `/dudunka_dev mushroom_vale enter`.
   Verify F3 dimension `dudunka:mushroom_vale` and a safe surface landing.
2. From inside Vale, locate all three biomes:

   ```text
   /locate biome dudunka:glowing_mushroom_forest
   /locate biome dudunka:pink_meadows
   /locate biome dudunka:memory_forest
   ```

   Travel in Creative/spectator or fly to the reported X/Z. For a distant jump,
   use `/execute in dudunka:mushroom_vale run tp @s X 140 Z`, then fly down in
   Creative; do not teleport blindly onto ground in Survival. Record coordinates
   and check F3 biome IDs. Explore within roughly 2048 blocks in each direction.
3. Inspect mycelium, giant red/brown mushrooms, moss and ground glow lichen in
   the glowing forest; open grass, pink petals/flowers and occasional cherry
   trees in the meadow; podzol, spaced spruce trees, mushrooms and darker
   violet/blue ambience in Memory Forest. Inspect each fog/water/sky palette
   with shaders off first. Daylight can make lichen light subtle; inspect
   nearby block light in F3. Confirm mooshrooms can spawn on clear
   mycelium under normal spawning rules; they need not appear immediately.
4. Walk substantial Survival routes through all three biomes and their borders.
   Check standing clearance, open meadow space, hills, shorelines, working ponds
   and water basins, tree/mushroom density and lack of exposed lava/ravines from
   these biomes. Note seeds/coordinates/screenshots of obstructed routes. The
   automated samples do not establish every route or aesthetic quality.
5. Place markers in all three, save/quit and restart. Verify markers, positions,
   biome IDs and developer return to the original departure. Check companion
   identities/ownership and run the existing safety, fallback and multiplayer
   travel checklist below. No portal, quests or music progression is expected.
6. On backup copies of Stage 1/2.1 worlds, check retained chunks/builds, player
   positions and travel/bookmarks. Legacy generators/biomes may remain. Do not
   edit saved generator settings or remove chunks. Check other dimensions too.
7. Repeat in full BMC4 v55.5 and on a dedicated server with two clients. Check
   biome modifiers, modded hazards/structures, shaders and registry logs.
   Record these separately from automated Forge results.

Architecture: [Stage 2.2](MUSHROOM-VALE.md#stage-22-three-independent-biomes).
Evidence: [Stage 2.2 validation](VALIDATION-MUSHROOM-VALE-STAGE-2.2.md).

## Stage 2.1: Mushroom Vale natural terrain

Historical Stage 2.1 checklist; use Stage 2.2 above for current fresh worlds.
This checklist is pending manual acceptance. Use JDK 17, Minecraft 1.20.1 and
Forge 47.4.13. In IntelliJ, set Project SDK and Gradle JVM to 17, then run the
Gradle task `runClient -PmushroomValeDev=true`. From a shell:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:$PATH \
./gradlew runClient -PmushroomValeDev=true
```

1. Create a **fresh** disposable Creative world with cheats enabled; record its
   seed. Never use a Stage 1 save to judge new terrain. Leave companions at home
   and empty the carrier. Stand on safe dry ground; record departure coordinates.
2. Enter with `/dudunka_dev mushroom_vale enter`. Check F3 dimension
   `dudunka:mushroom_vale`, biome `minecraft:mushroom_fields`, the normal day/night cycle, and a
   safe landing at the actual surface height. Arrival need not be Y=64. If the
   entire arrival chunk is water/unsafe, entry must refuse without moving the
   player, modifying blocks or losing an existing bookmark.
3. Explore at least 1024 blocks in each horizontal direction and repeat in
   several fresh seeds (include 0, 42 and -71021). Inspect hills, valleys,
   shoreline transitions, ponds/connected water, dirt/mycelium/gravel surfaces,
   mushroom placement and chunk borders. Walk substantial routes in Survival:
   hills and water should be easy to navigate, and local cave/canyon openings,
   lava and dense mushroom patches should have reasonable ways around them.
   Record coordinates/screenshots of excessively steep or unavoidable obstacles.
   Automated base-column samples exclude carvers and features.
4. Place a marker, save and quit inside Vale, stop/restart the client, and verify
   the marker/player position. Return with `/dudunka_dev mushroom_vale return`;
   verify departure, orientation, no fall damage, and retained companion UUIDs,
   ownership and NBT. Repeat from another dimension. Repeat blocked arrival,
   unsafe departure/return, fallback, operator permission and multiplayer
   bookmark checks from the Stage 1 checklist below using actual surface Y.
5. On a **copy** of a Stage 1 save, verify existing flat chunks, builds, travel,
   bookmarks and player positions survive; inspect newly visited chunks without
   assuming the saved generator has changed. Do not delete chunks or edit saved
   dimension settings. Check Overworld, Nether, End and other installed dimensions.
6. Repeat in a separate full BMC4 v55.5 profile and on a dedicated server with two
   clients. Review biome modifications, structures, protection hooks, shaders,
   worldgen warnings/errors and travel. Record these results separately from
   automated Forge validation.

Architecture and migration limits: [MUSHROOM-VALE.md](MUSHROOM-VALE.md#stage-21-natural-terrain-and-worldgen-architecture).
Automated results: [Stage 2.1 validation](VALIDATION-MUSHROOM-VALE-STAGE-2.1.md).

## Stage 1: Mushroom Vale in IntelliJ IDEA

Historical flat-prototype checklist; for fresh Stage 2.1 worlds use the checklist
above. The safety, multiplayer and persistence scenarios below still apply.

This is a manual acceptance plan, not a report of a completed graphical run.
Use Minecraft 1.20.1 / Forge 47.4.13 / Java 17. Keep a backup of any existing world.

1. Open the repository as a Gradle project in IntelliJ IDEA. Set both Project SDK
   and **Settings → Build Tools → Gradle → Gradle JVM** to JDK 17 and reload Gradle.
2. Create an IntelliJ **Gradle** run configuration using this project, with
   **Tasks and arguments**: `runClient -PmushroomValeDev=true`. Start it with Run.
   It uses the existing ForgeGradle `runClient` task and `run/` working directory.
   Alternatively, run `genIntellijRuns`, then edit the generated Java `runClient`
   configuration and add `-Ddudunka.devMushroomVale=true` to its **VM options**.
   A normal run without either opt-in must not expose `/dudunka_dev`.
3. Create a disposable Creative world with cheats enabled. Leave companions in
   the Overworld and empty the carrier. Stand on dry solid ground with headroom;
   record coordinates and the UUIDs/positions of any existing companions.
4. Run `/dudunka_dev mushroom_vale enter`. F3 must show `dudunka:mushroom_vale`;
   expect a flat mycelium plain at feet Y=64, the normal day/night cycle, and the vanilla mushroom
   fields biome. Check movement, lighting, and resource/log errors. No final
   forest, custom inhabitants, or portal is expected at this stage.
5. Place a distinctive vanilla block, walk away from the entry, and **Save and
   Quit** while still in Vale. Stop and restart `runClient` with the same opt-in,
   reopen the world, and verify the dimension, player location, and placed block.
6. Run `/dudunka_dev mushroom_vale return`. Verify a safe landing near the saved
   departure block, restored view direction, no fall damage, and no duplicated
   companions. Inspect their UUIDs, ownership, homes, modes, trust, and age.
   Repeat from the Nether to verify the recorded source dimension is used.
7. Try entry while riding or with an occupied/unsafe arrival area. It must refuse
   without changing terrain or bookmarks. Try entry twice, and return outside
   Vale; both must refuse. Have a second operator obstruct the departure area
   while the first player is away: return should use safe Overworld spawn and
   report the fallback. If both areas are unsafe, clear a landing and retry.
8. Repeat on a copy of a world made before this change. Check the Overworld,
   Nether, and End, existing companion saves, and the new dimension after a full
   restart. Merely reloading a datapack is not a dimension migration test.
9. For multiplayer, run `runServer -PmushroomValeDev=true` from an IntelliJ Gradle
   configuration (accept the Minecraft EULA for that development server first).
   Set `level-name=mushroom_vale_manual` in `run/server.properties` to use a separate
   disposable world rather than the generated GameTest world.
   Use the same mod on each client. Two operators must retain separate departure
   bookmarks across logout/server restart. A player without level-2 permission
   and the server console must not be able to use this player-only route. On a
   packaged server the opt-in is a Java VM option before `-jar`, not a game arg.
10. Repeat acceptance in the separate Better MC BMC4 v55.5 profile, including
    its worldgen, shaders, and protection mods. Record client/server logs and
    explicit results; automated Forge checks do not establish BMC4 compatibility.

The normal Gradle validation is:

```bash
./gradlew build verifyModels runGameTestServer
```

For a headless environment where client assets are unnecessary, the same checks
can use `-x downloadAssets`. `MushroomValeTests` covers actual JSON world loading
and noise settings, three-seed base terrain samples, command opt-in/permissions,
travel with real ServerPlayer instances
and a simulated network, unchanged companion instances/NBT, bookmark
serialization, hazards/passengers, fallback, blocked entry, and Forge vetoes.
The full suite also exercises the existing family, carrier, and album behavior.
Graphical networking, disk/server restart with actual clients, pre-change world
upgrade, and full BMC4 acceptance remain separate manual checks.

Prototype settings and compatibility limits: [MUSHROOM-VALE.md](MUSHROOM-VALE.md#stage-1-prototype-contract).

## Existing BMC4 acceptance checklist

Использовать отдельный профиль и мир. Версия Forge в профиле 47.4.13. Этот список — план ручной проверки, не отчет о выполнении.

- Клиент загружается; вкладка мода и три яйца есть; в latest.log нет ошибок ресурсов или регистрации dudunka.
- Вылупление всех трех: с правильными условиями прогресс идет, без одного условия стоит; угощения расходуются один раз; нет мгновенного появления.
- После первого угощения другой игрок не может кормить яйцо.
- Перезагрузка мира и выгрузка чанка сохраняют прогресс и угощения.
- Малыш, подросток и взрослый видны и имеют соответствующий размер коллизии.
- Ходьба, взгляд, освещение, тени, работа с включенными/выключенными шейдерами и resource pack сборки.
- ПКМ любимой едой: доверие +5, лечение, ускорение роста; повторное кормление в течение 30 с предмет не расходует.
- Shift + ПКМ пустой рукой: ожидание; еще раз — следование. Только хозяин управляет.
- Перезапуск сохраняет владельца, доверие, дом и ожидание.
- Яйца генерируются в новых сундуках нужных таблиц; содержимое других модов не исчезает; уже открытый сундук не меняется.
- Смертельное повреждение: персонаж остается жив, возвращается к безопасному загруженному дому; чужое измерение и недоступный дом не вызывают загрузку чанков.
- Лава, утопление, падение, взрыв, void; отсутствие бесконечного спама ошибок.
- Выделенный сервер загружается без client class errors; два клиента видят одинаковую стадию, положение и взаимодействия.
- 3, 10 и 30 персонажей: оценить TPS и поведение AI. Мод не содержит глобального сканирования мира, но реальный предел нужно измерить.

После ошибки сохранить latest.log и crash-report, версию JAR, версии Forge/модпака, краткие шаги. Сначала проверить воспроизводимость с Forge 47.4.13 и только этим модом, затем в BMC4.

## Автоматические проверки для разработки

```bash
./gradlew build verifyModels
./gradlew runGameTestServer
```

`verifyModels` строит деревья всех трех моделей без графического окна. GameTest проверяет паузу без воды и вылупление Сюси при восстановлении условий, а также сохранение хозяина/доверия/режима/стадии и защиту от смертельного урона. Эти проверки не заменяют тест в полном модпаке и визуальную приемку.

## Приемка 0.2.0

- Команда «жди» прекращает самостоятельные действия; переключение обратно возвращает поведение.
- Дюдюнька расходует одно лежащее яблоко за действие, не подбирает предмет с активной задержкой; сундуки сохраняют содержимое.
- Чужая Сюся не останавливает компанию; своя отстающая Сюся вызывает ожидание и прекращает его, когда подойдет.
- У Маруси бывают самостоятельные интервалы. Монстры вызывают тревогу и сообщение хозяину, не атаку; рыбы — наблюдение.
- Ночью Маруся действительно располагается на кровати/сундуке; днем встает; достижение выдается после прибытия. Особенно проверить рельеф и мебель других модов.
- В дождь доступное укрытие достижимо; нет принудительной загрузки чанков. Нет крыши — нет бесконечной попытки ходить к недоступной координате.
- При доверии 25 появляются приветствия Дюдюньки; при 0 их нет. Очки, кольцо, позы и движения требуют визуальной приемки.
- Два клиента видят одинаковую активность; перезапуск сбрасывает только текущую сцену, основные данные сохраняются.
- Достижения за печенье, взрослых персонажей и сбор семьи работают без повторного расходования еды в cooldown.

Автотесты 0.2.0 покрывают семейную фильтрацию/ожидание/приоритет «жди», расход яблок, тревогу без атаки, владельца и cooldown кормления, достижения за печенье/семью/взросление. Геометрия и все позы проверяются на конечность трансформаций. Остальные случаи выше требуют ручной приемки.

## Приемка 0.3.0

- Новые рецепты видны в JEI; три места и переноска отображаются без missing texture.
- Дом без каждого из пяти условий отказывает в привязке и сообщает недостающее; с крышей, обычной кроватью, сундуком/бочкой, факелом и тортом принимает. Инвентари не меняются.
- ПКМ привязывает своих персонажей нужного вида рядом; чужих и другой вид игнорирует. Перезапуск сохраняет владельца/дом.
- Убрать торт, крышу, свет, сундук или кровать: через 5 секунд место непригодно. Сломать маркер: персонаж не возвращается к исчезнувшей мебели.
- Другой игрок не меняет привязку и не ломает закрепленный дом/яйцо. Хозяин ломает; оператор в creative имеет обход. Проверить также поршни, TNT и взаимодействие с модами привата BMC4.
- Пустая переноска забирает только свою Сюсю без поводка; заполненная не забирает вторую. Переноска не складывается в стек.
- Сохранение/перезапуск с заполненной переноской: после выпуска та же стадия, доверие, владелец, UUID, режим и дом. Хранение в сундуке и выбрасывание/подбор другим игроком не меняет владельца.
- Выпуск в стену, воду и на магму отказывает, переноска остается заполненной. После исправления места выпуск работает один раз.
- Копия заполненной переноски в creative не выпускает вторую Сюсю, в том числе после нового захвата настоящей Сюси.
- Войти с переноской в Незер/Энд, выпустить, снова забрать и вернуться. Исходное измерение дома сохраняется; автоматического возврата к старому дому через измерения нет.
- Два настоящих клиента видят захват/выпуск без призрачных сущностей; повторные быстрые ПКМ не дублируют персонажа.

Автотесты проверяют серверную логику и сериализацию. Диск/перезапуск реального сервера, аварийное завершение, моды привата и портал с настоящим клиентом остаются ручными проверками. Уничтоженная переноска автоматически не восстанавливается.

## Регрессия 0.2.1 / 0.3.1

- Оставить около своей Дюдюньки яблоко или стек яблок. Дождаться поедания, повторить несколько раз. Нет исключения FamilyBehaviorGoal.tick, расходуется по одному яблоку за действие, дальнейший AI работает.
- Повторить после перезапуска и с Neruina из BMC4. Автотест намеренно вызывает несколько тиков завершенной цели до остановки, как допускает GoalSelector.

## Приемка 0.4.0

- Своя Маруся, пустая рука, обычный ПКМ: +2 доверия, сердечки, слышно мурчание, краткий наклон головы/движение хвоста. Через 2 секунды возвращается обычное поведение. Проверить два клиента и шейдеры.
- Чужой игрок и бесхозная Маруся не принимают поглаживание. ПКМ с предметом не считается поглаживанием.
- Повторное поглаживание раньше 30 секунд не повышает доверие; после 30 загруженных секунд снова разрешено. Перезапуск и выгрузка чанка не сбрасывают таймер.
- Shift + ПКМ пустой рукой по-прежнему переключает «жди» даже во время cooldown. Погладить ожидающую Марусю: доверие повышается, она остается ждать.
- Поглаживание не лечит/не ускоряет рост; кормление рыбой сохраняет свой отдельный cooldown. Доверие ограничено 100.
- Сюся с доверием 0/50/100: атрибут движения 0,08/0,10/0,12 при стандартной базовой скорости. Подход к владельцу остается медленным.
- Перезапуск и несколько циклов захвата/выпуска Сюси не увеличивают бонус сверх доверия. Проверить перенос в Незер и обратно.
- Эффекты скорости других модов и измененная базовая скорость не исчезают после загрузки/кормления; оценить баланс в BMC4.
- Старый мир 0.3.1: владелец, возраст, доверие, домашние места и переноски сохраняются; пустая рука на Марусе приобретает новый смысл, Shift-команда прежняя.

Автотесты проверяют владельца, cooldown и его NBT, ожидание, лимит доверия, длительность сцены/таймера на реальных тиках, восстановление модификатора и сохранение другого модификатора. Графический клиент и полный BMC4 еще требуют ручной приемки.

## Приемка 0.5.0

- Открыть сундук рядом со своей Дюдюнькой: подходит к свободному месту и смотрит. Закрыть — сцена заканчивается. Проверить обычный, ловушечный и настоящий двойной сундуки.
- Другой игрок открывает тот же сундук: ваша Дюдюнька не получает новую реакцию от его события. Собственная семья другого игрока реагирует. Маруся/Сюся не получают эту сцену.
- Режим «жди», отставшая Сюся, укрытие/ночной отдых не отменяются из-за открытия сундука. Быстрые открытия не создают бесконечных сцен; новая реакция не чаще 20 секунд, текущая не более 5 секунд.
- Закрыть или сломать сундук во время подхода, выйти/сменить измерение: нет обращения к отсутствующей цели и бесконечной навигации.
- Приват запрещает открытие: без фактического меню нет реакции. Содержимое и количества предметов до/после сцен одинаковы. Бочки и виртуальные меню не запускают реакцию.
- Поставить торт с безопасным полом рядом: Дюдюнька подходит быстрее, смотрит, кусочки остаются. Удалить торт во время подхода: прекращает движение без исключения.
- Стена/опасный пол около цели: недоступная сцена не начинается. Проверить ступени, заборы, узкие проходы, дождь и мебель модпака.
- Прибывшая Дюдюнька перестает бежать. Два настоящих клиента видят одинаковую активность; внешний вид CAKE_RUN требует визуальной приемки.

Автотесты проверяют серверные события и построение пути в простой геометрии. Они не заменяют игровой тест полной сборки, отмены открытия модами привата и визуальной навигации.

## Приемка 0.6.0

- Новый тестовый мир/новые чанки: искать яйца-блоки на траве у воды в swamp/mangrove swamp и на мху в lush caves. Записать seed, координаты, биом и число исследованных чанков для оценки редкости. Старые чанки не должны получать новые яйца автоматически.
- Найденное яйцо имеет нулевой прогресс и без хозяина. До полного набора угощений не вылупляется; первые листья закрепляют владельца, гриб завершает подготовку. Добыть и поставить дома — обычное поведение яйца.
- Яйца не заменяют сундуки, растения, постройки, воду; не оказываются на камне, под низким потолком и рядом с лавой. Проверить фактические места в сложных пещерах.
- Два игрока: первое угощение сохраняет владельца, другой игрок не кормит/не ломает закрепленное яйцо обычным способом.
- naturalEggGeneration=false, перезапуск, новые чанки: новые природные яйца не появляются; существующие яйца и прежний сундуковый лут работают.
- Ванильные равнины/обычные леса и неперечисленные модовые биомы не получают генерацию. Если пак расширяет теги, проверить только явно добавленные биомы.
- Новые чанки в полном BMC4: нет ошибок worldgen/feature order cycle, влияние на время генерации приемлемо. Сравнить варианты с включенным и выключенным naturalEggGeneration.

Для проверки самого Feature без ожидания редкой попытки можно использовать `/place feature dudunka:snail_egg` стоя в свободном месте над мхом/травой у воды (команды разрешены, naturalEggGeneration=true). Команда обходит фильтры биома/редкости placed feature; она не проверяет реальную частоту находок.

## Приемка 0.7.0

- Два своих персонажа, ровный сухой пол вокруг зажженного костра: присесть в пределах 4 блоков. Персонажи подходят к разным местам на расстоянии двух блоков и отдыхают, глядя на огонь. Повторить с синим костром и всеми тремя видами, проверить позы/скорость подхода на клиенте.
- Встать, отойти больше 4 блоков, потушить/сломать костер: отдых заканчивается, обычное поведение возвращается. Повторное приседание позволяет собраться снова.
- Один свой и один чужой персонаж не образуют семью. Два игрока со своими семьями: места/отмена принадлежат соответствующему хозяину.
- «Жди» исключает персонажа; если осталось меньше двух участников, сцена прекращается. Отставшая Сюся, монстр у Маруси и дождь сохраняют приоритет.
- Закрытый путь, магма/вода под местом, препятствие на месте или пути: нет отдыха в опасной позиции и прохода через огонь. Проверить реальные маршруты на сложном рельефе и узких проходах.
- Выгрузка области/перезаход/смена измерения: временная сцена не сохраняется, персонажи продолжают обычное поведение. Проверить полный BMC4 v55.5 и сетевую игру отдельно.

## Приемка 0.8.0

- Малыш Дюдюньки имеет прежний вид. Подросток и взрослая меняют пропорции головы/корпуса/ног, одежду и рюкзак. Общий рост около 1,3×/1,6×, ступни стоят на земле. Марусю/Сюсю сравнить с предыдущей версией: прежние модели/текстуры.
- Дождаться естественного взросления или использовать короткий growthStageSeconds в отдельном тестовом мире: модель и текстура переключаются вместе. Два клиента должны видеть одинаковую стадию.
- Перезаход и загрузка старого сохранения с подростком/взрослой: возрастный вид сразу правильный, владелец/доверие/дом сохранены.
- Проверить новые пропорции спереди/сбоку/сзади, ромашку/очки/кольцо/подвеску, карманы и фактуру; сравнить с референсами. Это первая адаптация, нужна художественная оценка.
- Ходьба, бег к торту, взмах, поправление очков, ожидание Сюси, отдых у костра и сон: части не отрываются, ноги не проваливаются, руки не проходят через голову.
- Низкие проходы, подход к мебели и толкание: новая коробка столкновения соответствует возрасту. Полный BMC4 v55.5 и сетевой клиент проверить отдельно.

## Приемка 0.9.0

- Малыш Сюси имеет прежний вид. Подросток — вытянутое тело, округлая раковина с небольшим задним завитком; взрослая — длинное тело/задний кончик, заметный конус ахатины и длинные усики. Сравнить по трем ракурсам с референсами.
- Длина примерно 1,3×/1,7× от малыша. Проверить отсутствие двойного масштабирования при взрослении; ступни/подошва на земле, тело и конус соединены.
- Коричнево-медовые полосы, кремовое тело и крапинки взрослой; оценить фактуру в игровом свете. Превью не показывает всю текстуру.
- Естественное взросление и загрузка старого мира: модель/текстура меняются вместе. Два клиента видят одну стадию.
- Взрослая Сюся с доверием: поймать в переноску, сохранить/перезапустить мир, выпустить в том же/другом измерении. Возраст, хозяин, доверие, скорость и новый вид сохраняются, копия переноски не дублирует персонажа.
- Движение к растениям, ожидание семьи, укрытие от дождя, ночной сон/отдых у костра: усики не отрываются, раковина не проваливается. Проверить воду, узкие проходы, новую коробку столкновения.
- Дюдюнька всех возрастов и Маруся выглядят как в 0.8.0. Полный BMC4 v55.5 и реальный сетевой сеанс отдельно.

## Приемка 0.10.0

- Малыш Маруси прежний. Подросток с крупной головой, небольшим воротником, тонкими лапами; взрослая с длинным корпусом, широкой грудью, массивными лапами и густым хвостом. Сравнить три ракурса с референсами.
- Корпус без головы/хвоста примерно 1,35×/1,8× от малыша. Отдельно оценить пропорции головы/воротника/хвоста. Черная шерсть остается черной, графитовые области не выглядят светло-серыми.
- Ходьба, самостоятельное перемещение, наблюдение за рыбой, тревога рядом с монстром: лапы/хвост/кисточки не отрываются. У выросшей Маруси хвост при тревоге поднимается.
- ПКМ пустой рукой взрослой своей Маруси: доверие +2, cooldown 30 секунд, мурчание. Shift-ПКМ включает «жди». Чужой игрок сохраняет прежние ограничения.
- Сон на кровати/сундуке, ожидание Сюси, отдых у костра и укрытие от дождя: оценить контакты с поверхностью и пересечения деталей.
- Естественное взросление, перезаход со старым сохранением и два клиента: модель/текстура соответствуют одной стадии, владелец/дом/доверие сохраняются.
- Размер столкновения и проходы: тело/лапки не проваливаются. Полный BMC4 v55.5 проверить отдельно. Дюдюнька и Сюся всех возрастов сохраняют вид 0.9.0.

## Приемка 0.11.0

- Обычная книга, ПКМ по своей Дюдюньке/Марусе/Сюсе: до пяти связей в чате или сообщение об отсутствии знакомств. Книга не расходуется, «жди» и доверие не меняются. Чужой игрок не видит связи; Shift пустой рукой и поглаживание Маруси прежние.
- Два своих персонажа дошли к костру и сидят: примерно за минуту подтвержденного отдыха +1 к общей связи, а не +2 от двух AI. Три персонажа получают три отдельных парных связи.
- Встать/отойти/потушить костер, дать «жди», увести участника: рост останавливается. Длительное отсутствие не добавляет очков. Вернуться — остаток минуты сохраняется.
- Один персонаж еще подходит/ждет Сюсю/реагирует на монстра/находится под дождем: его пары не получают очки. Чужие персонажи не участвуют.
- Достичь 10 пунктов, встать и снова присесть: друзья предпочитают соседние места, места разные и безопасные. Заблокировать ближайшее место/добавить воду: предпочтение не отменяет проверку пола/пути.
- Дисковое сохранение/перезапуск: score и частичная минута остаются, за время отключенной игры нет бонуса. Книга после перезапуска показывает связи.
- Сюсю с дружбой взять в переноску, перейти в другое измерение, выпустить: та же связь видна у того же хозяина. Новая Сюся из другого яйца не наследует связь.
- Два игрока и реальные клиенты в BMC4: отношения раздельны, сообщения получает только инициатор, модели/текстуры и прежний AI не изменились. Сложные маршруты/баланс проверить вручную.

## Домашние сцены 0.12.0

1. Привязать каждого к своему маркеру в доме с крышей, кроватью, сундуком/бочкой, светом и тортом. Добавить цветы/азалию, оставить проходы. Стоять рядом (до 8 блоков от маркера), режим персонажей — следование.
2. Дюдюнька должна дойти и присесть у цветов; Сюся — дойти к растениям. При движении используется обычная походка. Убрать растение во время сцены, затем повторить со Shift + ПКМ пустой рукой. Цель должна освобождаться.
3. `/time set 12500`: Маруся должна дойти до свободной кровати и лечь сверху. Проверить малыша, подростка и взрослую: подъем, отсутствие зависания и пересечения мебели. Две кошки не должны выбирать одну кровать в домашних сценах.
4. Лечь самому: Маруся прекращает домашнюю сцену. Также проверить занятую кровать, сломанную половину, низкий потолок, заблокированный путь и вторую свободную кровать.
5. Отойти от маркера дальше 8 блоков; проверить возврат к следованию. Проверить приоритеты поглаживания, монстра у Маруси, костра, открытия сундука у Дюдюньки и ожидания своей Сюси.
6. Перезапустить мир: возраст, доверие и команда сохранены, временная сцена заново выбирается только при подходящих условиях. Проверить в полном BMC4 v55.5.

## Приветствие дома 0.13.0

1. Привязать Дюдюньку к обустроенному маркеру, получить доверие ≥25. Находиться рядом хотя бы секунду, затем отойти дальше 12 блоков на 10–12 секунд, оставив чанк персонажа загруженным.
2. Вернуться в радиус 8 блоков от маркера и до 6 блоков от Дюдюньки, без преграды между вами. Она должна один раз помахать 4 секунды. При необходимости убрать цветы для отдельной проверки приветствия.
3. Повторить короткое движение у порога: нового приветствия быть не должно. Повторить полноценный выход сразу — действует минутная пауза. Через минуту новый полный выход снова дает приветствие.
4. Прервать жест командой «ждать», открытием сундука, сбором у костра; он прекращается и не повторяется для того же возвращения. Доверие и режим ожидания не меняются автоматически.
5. Проверить перезапуск мира, выход/вход игрока, переход через портал и выгрузку чанка: история не должна превращаться в ложное возвращение. Проверить жест на всех трех возрастных моделях в BMC4.

## Семейный альбом 0.14.0

1. Скрафтить альбом из книги, бумаги и чернильного мешка, либо `/give @s dudunka:family_album`. ПКМ в основной и дополнительной руке: предмет остается, открывается экран своих ближайших персонажей.
2. Проверить имена, все три стадии, доверие и режим «ждать», готовый дом и отдельно отсутствие торта/света. Открыть снова после изменения, данные должны обновиться.
3. Переименовать персонажа длинным именем, уменьшить окно/изменить масштаб GUI, переключить русский/английский. Стрелки, колесо, Esc и кнопка закрытия доступны; текст не должен выходить за панель.
4. Проверить дружбу, пустую семью, больше 12 персонажей, чужую семью рядом, дальнего персонажа и выгруженный дом. Альбом не загружает чанки и не показывает чужие данные.
5. Передать альбом второму игроку: он должен видеть свою семью. На сервере и обоих клиентах установить 0.14.0. Проверить обычную книгу — старый чат дружбы сохранен.
6. В полном BMC4 проверить открытие, совместимость с меню других модов, выделенный сервер, повторное открытие и работу игры без паузы.

## Путешествие 0.15.0

1. В режиме следования пройти с семьей по открытому пути: Сюся идет медленнее, семья ждет ее по прежним правилам. Нормальное продвижение не должно вызывать перемещение.
2. Оставить одного персонажа за препятствием, отойти на 12–64 блока и стоять на безопасном полном полу. После шести секунд активного неудачного следования он может появиться рядом. Проверить все виды/стадии и паузу между повторными догоняниями.
3. Проверить команду «ждать», поводок, посадку в лодку, пассажиров, полет/сон хозяина, чужого игрока, воду/лаву, соседний огонь/костер/магму/ягоды/рыхлый снег и занятое место. Перемещения в опасную/занятую позицию быть не должно.
4. Проверить нормальный путь через двери/лестницы и застревание на сложном рельефе. При отсутствии безопасного места персонаж остается на месте.
5. Уйти дальше 64 блоков, через портал либо выгрузить чанк: локальная механика не должна грузить мир или создавать копии. Сюсю между измерениями перевозить переноской.
6. Поставить `familyCatchUp = false` в конфигурации мира: перемещение отключено. Проверить UUID, возраст, дом, режим, доверие и дружбу после догоняния; в BMC4 провести длительную прогулку с полным набором модов.

## Команды альбома 0.16.0

1. Обновить сервер и оба клиента до 0.16.0. С альбомом в любой руке открыть своего персонажа. Нажать «Ждать здесь»: режим страницы обновляется, персонаж прекращает движение/домашнюю сцену. Перезайти в мир и проверить ожидание.
2. Нажать «Следовать»: ожидание снимается; обычные семейные приоритеты и темп Сюси действуют. Доверие/возраст/дом/дружба не меняются. Проверить прежний Shift + пустая рука.
3. Пока открыт экран, второй игрок пусть уберет персонажа переноской либо уведет дальше 64 блоков. Команда должна быть отклонена, недоступная страница исчезнуть. Убрать альбом из обеих рук: команда отклоняется.
4. Проверить две семьи с одинаковыми именами: страницы и команды относятся только к своей семье. Отправляемый ответ не обновляет чужой экран.
5. Быстро нажимать, закрыть экран сразу после команды, открыть снова: ответы старой сессии не должны открывать окно или изменять новую страницу. После минуты без действий открыть альбом снова.
6. Проверить узкое окно, длинные имена, прокрутку, русский/английский язык, отсутствие наложений кнопок/статуса, выбор прежней страницы после ответа и корректное удаление исчезнувшей страницы. Полный BMC4 и реальная сеть требуют ручной приемки.

## Совместный отдых дома 0.17.0

1. Поставить рядом личные маркеры двух/трех своих персонажей; обеспечить каждому крышу, кровать, сундук, свет и поставленный торт. Привязать персонажей, снять ожидание, оставаться до 8 блоков от каждого маркера днем без приседания.
2. Наблюдать отдельные пешие подходы и совместное сидение до 10 секунд. Проверить малыша/подростка/взрослого всех видов, препятствия и маленькие помещения. Сюся должна идти с обычной скоростью. Альбом должен по-прежнему показывать режим следования.
3. За несколько сцен проверить рост дружбы только сидящих пар; подход, паузы и одиночка не должны начислять время. Еда/торт/инвентарь, доверие и стадия не меняются. Проверить прогресс после перезагрузки, без начисления времени вне игры.
4. Во время сцены нажать «Ждать здесь» в альбоме либо Shift + пустая рука; выйти из дома, начать сон, присесть, убрать маркер, занять место или поставить воду. Участник должен прекратить сцену; оставшаяся пара может продолжить, одиночка — нет.
5. Отдалить свою Сюсю, показать Марусе монстра, погладить ее, открыть сундук, вернуть хозяина для приветствия. Приоритетные реакции сохраняются. Вечером Маруся идет к свободной кровати; Дюдюнька/Сюся могут остаться парой.
6. Проверить двух владельцев и несколько одинаковых персонажей: чужие не вступают в группу/дружбу; более восьми не резервируются. У выгруженных/дальних домов сцена не запускается и чанки не подгружаются.

## Яйца BMC4 / путеводитель 0.18.0

1. Обновить сервер и клиента до 0.18.0. Войти старым игроком без альбома: должен появиться один предмет и сообщение. Перезайти, умереть, сменить измерение — новых экземпляров нет. Игрок с альбомом не получает второй.
2. Войти с заполненным инвентарем: нет выброшенного/потерянного предмета, есть сообщение. Освободить ячейку и перезайти — выдача выполняется. Потерянный после выдачи альбом сделать рецептом.
3. Проверить вкладки, шесть страниц, колесо/стрелки, русскую/английскую локализацию и небольшое окно. Без персонажей сначала открыт поиск, со своими рядом — семья; команды работают как раньше. Читать обе вкладки во время ответа/закрытия.
4. В BMC4 искать конкретные структуры/контейнеры из BMC-EGGS.md. Проверять таблицу добычи до первого открытия (например, временно включив showcontainerloottable конфигурации Loot Integrations), затем вернуть настройку. Не каждый сундук одной структуры имеет нужную таблицу.
5. На копии старого сохранения открыть ранее не сформированный подходящий сундук после установки. Повторное открытие уже сформированного сундука не добавляет яйца. Отдельно проверить индивидуальную добычу/контейнеры модов.
6. Исследовать новые Bayou/Marsh/Wetland и ванильные болота/lush caves. Яйца только на траве/мху у воды, не заменяют постройки; уже готовые чанки не обновляются. Оценить фактическую редкость, учитывая малое число подходящих мест.
7. Сравнить ванильный Forge и полный BMC4: без сторонних модов их места скрыты; настроечные eggLootMultiplier=0/naturalEggGeneration=false видны в путеводителе после повторного открытия. Прежние ванильные способы работают при включенных источниках.
8. Измерить частоту/число яиц с Loot Integrations/RS importModdedItems: наш собственный модификатор не добавляет второе уже присутствующее яйцо того же вида, но чужие моды могут менять результат. Не считать приведенный базовый шанс гарантией общей вероятности контейнера.


## 0.19.0 — возврат и потерянная переноска

1. Обновить сервер/клиенты, оставить один JAR. Загрузить старых персонажей хотя бы раз; открыть «Возврат», проверить отсутствие чужой семьи. Переоткрыть альбом для нового снимка.
2. Оставить ожидающего персонажа вдали, дождаться выгрузки чанка, уйти в другое измерение. Встать на землю с безопасным полом рядом, вернуть через альбом. Проверить одну сущность с прежним возрастом/именем/доверением/домом и сохраненным ожиданием.
3. Проверить отказ в полете/воде/сне, на опасном полу и при отсутствии свободного места. Поводок/транспорт персонажа запрещают перенос. Старый неизвестный реестру персонаж появляется в списке после загрузки его чанка.
4. Захватить Сюсю новой переноской, оставить предмет в сундуке (имитация потери), восстановить через альбом. Старый предмет не выпускает Сюсю, новый выпускает ровно один раз. Повторная выдача с действующей переноской в инвентаре запрещена. Полный инвентарь без пустой одиночной переноски отклоняет выдачу.
5. Проверить серверный перезапуск между захватом и восстановлением, между восстановлением и выпуском. Старая переноска 0.18 без backup: выпустить и снова забрать; уже потерянная старая копия не восстанавливается.
6. Два игрока: чужие команды/копии переноски не дают доступа; смена измерения/выход игрока во время поиска снимает билет. Закрытый альбом не открывается поздним ответом. Проверить серверные лаги/временные билеты и территориальные моды BMC4.


## 0.20.0 — причины отказов и записки

1. Обновить сервер и оба клиента, оставить один JAR. Вкладка «Поиск яиц» содержит десять страниц: шесть прежних и четыре истории; первая история открыта, остальные скрыты до записок. Проверить небольшое окно, длинные имена, прокрутку и ru/en.
2. Прочитать записки 3 → 1 → 2: найденное вне порядка сохраняется, текст открывается по порядку. Повторное чтение ничего не меняет, предмет остается. Передать записку второму игроку: у него свой прогресс. Перезапустить сервер, умереть и сменить измерение — прогресс сохраняется.
3. На копии мира проверить не сформированные сундуки пяти типов деревенских домов, общую и цветочную добычу MVS. Готовый сундук не пополняется. Без MVS все три записки доступны в деревнях, текст альбома соответствует отсутствию MVS.
4. trailNotes=false прекращает новые добавления, но сохраняет чтение старых записок и прогресс. Настройка видна после переоткрытия альбома. Проверить Loot Integrations/RS и фактическую частоту в BMC4 отдельно.
5. Возврат из воздуха/воды, тесное место, поводок/лодка, неизвестное последнее место: сообщения объясняют причину и действие. Пауза показывает оставшиеся секунды. Длинные ответы не перекрывают кнопки и сведения.
6. Восстановление переноски: действующий предмет во второй руке, полный инвентарь, старая переноска без backup. После отказов прежний билет действует; после успеха старая копия не выпускает Сюсю. Поздний ответ не открывает закрытый экран.
7. Найденное до завершения истории яйцо вылупляется обычным способом. Полная цепочка не выдает яйцо автоматически. Прежние проверки 0.19, UUID/дом/ожидание/дружба и доступность яиц остаются обязательными.

## Ручная приемка 0.21.0: уютный дом

- Обновить один JAR на сервере и каждом клиенте; проверить старые домашние места и владельцев, новый северный вид старых блоков.
- Скрафтить три предмета по COZY-HOME.md; проверить все четыре направления, модели в инвентаре/руке, текстуры и добычу при ломании.
- В готовом доме назначить одному своему другу ближайшую мебель через альбом. Проверить отказ чужого игрока, другой вид, занятую мебель и освобождение Shift+ПКМ при выгруженном друге.
- Переключить «Дома», увидеть все фазы у малыша/подростка/взрослого; у Маруси слышно тихое мурчание, у Дюдюньки лист появляется лишь при показе, Сюся не пересекает стены.
- Уйти хозяином: друзья в HOME не следуют/не догоняют. Переключить «Следовать» и «Ждать здесь» во время каждого занятия. Препятствие перед мебелью, вода, поводок/транспорт и разрушение блока прерывают сценку.
- Перезапустить мир, сохранить назначения/режим, перенести Сюсю и выпустить в другом измерении; получить статус «в другом мире» для мебели без загрузки ее чанка.
- Два настоящих клиента: ответы альбома приватные, чужое назначение/ломание/освобождение не проходят. Проверить минимальный размер окна и крупный GUI, две строки кнопок, длинные имена и прокрутку.
- Подтвердить отсутствие новых ресурсов/кормления/прироста доверия от мебели. Полный BMC4 требует отдельной приемки.

## Приёмка 0.22.0 в игре

- Для каждого возраста: три последовательных занятия Дюдюньки и Маруси, рисунок стола совпадает с листом, лист виден только при показе; разные позы не проходят сквозь мебель.
- Сюся: спокойный взгляд, любимая еда в обеих руках владельца (усики оживляются, еда не исчезает), дождь/вода/отдых (усики втягиваются), затем нормальная длина. Проверить глаза всех возрастов.
- Найти/выдать шесть записок, прочитать в порядке 3–1–2 отдельно для обоих друзей; будущий текст скрыт до предыдущей записки. Два игрока не делят прогресс; повтор, переподключение и перезапуск сохраняют результат.
- Старый мир с тремя записками Дюдюньки: прежняя история остаётся открыта. Проверить отключение `trailNotes`, пути с MVS/Better Dungeons и без них, все 18 страниц и шесть иконок.
- Обновить сервер и клиенты до 0.22.0 (протокол 7), проверить альбом и команды. Полная BMC4, игровой GUI и художественная приёмка остаются ручной проверкой.

## 0.23.0 — совместные сценки у мебели

1. Двум-трем своим друзьям сделать обустроенные личные места рядом, назначить мебель хозяину и включить всем «Дома». У гостей мебель не обязательна. Стоять рядом, оставить свободный пол перед входом.
2. Дождаться показа рисунка Дюдюньки: друг должен подойти пешком, сесть на отдельное место, смотреть; показ может продлиться до 12 секунд. Рисунок на столе/в руке остается согласованным. Проверить Марусю и медленную Сюсю вместе.
3. Проверить гостей возле отдыхающей Маруси и выглядывающей Сюси. У Сюси после встречи продолжается путь к листочку. У Маруси чужой гость не занимает подушку/когтеточку.
4. Проверить ориентации мебели и взрослых, расстояние до личного дома гостя (6 блоков), тесный/занятый проход, стены между участниками, воду/опасный пол. Встреча может не состояться при отсутствии безопасного места; переносов сквозь препятствия нет.
5. Начавшееся собственное занятие гостя не прерывается. «Ждать», «Следовать», поглаживание, угроза, разрушение мебели и уход хозяина прекращают участие. Повторная встреча не запускается сразу.
6. В альбоме проверить сохранение возраста/доверия/дома/режима, рост парной дружбы только после фактического прибытия. Предметы не производятся, еда не расходуется. Проверить перезапуск, смену измерения и двух разных владельцев; обновить все клиенты/сервер до одного JAR.

## 0.24.0: прогулки и просьбы

Полный набор содержит 124 GameTests. Перед ручной проверкой заменить JAR сервера и всех клиентов (протокол 8).

1. Режим Follow, день, сухая погода, маленький цветок: остановиться на три секунды с Дюдюнькой и другим другом; проверить разные места, подход Сюси и выход после движения владельца.
2. Прервать дождем, ночью, ожиданием/домашним режимом, опасностью, исчезновением цветка. Проверить узкую тропу, стену и отсутствие подходящих мест; никаких телепортаций и разрушения растений.
3. Альбом → друг → Просьбы: принять и принести цветок; проверить один расход, один подарок, +2 доверия и воспоминание. Угощение через альбом и обычную правую кнопку, в том числе при сытости.
4. Дом Маруси: незаконченный/чужой/без мебели не подходит; уже готовый личный дом с лежанкой подходит. Пропуск без штрафа; десять минут игрового времени до нового предложения.
5. Перезапуск, переноска Сюси и возврат: принятое дело/воспоминания сохраняются. Открыть и передать подарок; чтение не выполняет чужую просьбу и не открывает записки о яйцах.
6. Маленькое окно, несколько GUI Scale, длинные имена, переключение семейной страницы/просьб, полный инвентарь при награде. Альбом обновляется новым открытием.

## 0.25.0: приветствия и дождливый дом

- Низкое/среднее/высокое доверие, все три вида; настоящий уход за 12 блоков и возвращение в 8, разные фазы выборки и отсутствие повторного приветствия. Выгрузка/вход не считаются экскурсией.
- Реальные подходы всех трех друзей в «Дома», личные приветствия и общая фаза при доверии 60 и парной дружбе 10; команды, уход игрока, угрозы и поводки прерывают.
- Окно во внешней стене с видимым дождем, обычный/пустой/опасный горшок, Маруся рядом с другом; безопасные отдельные места и сухой путь. Нет дождя или нет подходящих интересов — сохраняются обычные занятия.
- Разрушить окно/горшок/личное место, закончить дождь, сменить режим, вернуть друга: временные цели освобождаются, сохраненные назначения остаются. Общая дружба только после прибытия, один парный счетчик.
- Вручную проверить BMC4, стекло модов, сложный дом/лестницы, визуальный взгляд в окно, кошачью позу на полу, Сюсю у горшка и возврат между измерениями при загруженном доме. Протокол альбома остается 8.

## 0.26.0: воспоминания и вся семья

- Реальное вылупление и возрастные переходы дают первые записи; загрузка уже выросшего друга не выдумывает прошлое. Перезапуск и переноска не дублируют журнал.
- Знакомство только с видимым другом владельца в шести блоках; общая сценка только после прибытия. Нет предметных наград и дополнительного начисления дружбы.
- Сводка: следование/ожидание/дома, домашний режим вдали от дома, выгруженный друг, другое измерение, старая и новая переноска, действующая переноска при себе. Чтение не грузит чанки и не меняет билет.
- RU/EN, длинные имена, пустые списки, 18 воспоминаний/32 члена семьи, GUI Scale и прокрутка. «Открыть друга» выбирает верную карточку. Протокол 9 на сервере и всех клиентах.

## Stage 2.5: magical atmosphere, music and environmental polish

**Updated by Stage 2.5a below:** Creative and Survival now use the Vale track,
lanterns/bulbs emit 15, medium detailed mushrooms and standalone benches are
more discoverable. The historical Stage 2.5 validation report is not current acceptance.

Run Java 17 / Forge 47.4.13 / Minecraft 1.20.1:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
python3 tools/validate_vale_resources.py
./gradlew --offline build verifyModels -x downloadAssets
python3 tools/validate_vale_resources.py --mod-jar build/libs/dudunka-0.26.0-alpha.jar
./gradlew runClient -PmushroomValeDev=true
```

Create a **NEW world**, cheats enabled. Enter while standing safely on solid
Overworld ground, with an empty carrier and companions left at home:

```text
/dudunka_dev mushroom_vale enter
/gamemode creative
/locate biome dudunka:glowing_mushroom_forest
/locate biome dudunka:memory_forest
/locate biome dudunka:pink_meadows
```

Use the returned biome positions to explore fresh chunks, checking moderately common
red/brown mushrooms, medium turquoise/violet/golden detailed mushrooms,
uncommon exceptionally large landmarks, separated benches/flower beds, open routes and natural terrain.
`/locate structure` cannot find these placed-feature landmarks. Fixed benchmark
coordinates in automated reports depend on that test world's random seed.

For deterministic model/interaction inspection, explicitly build a temporary
platform high above terrain in this disposable world (these `/fill` commands
are test preparation, never production generation). Keep the player outside
preview footprints:

```text
/fill 0 159 0 79 159 15 minecraft:mycelium
/fill 0 160 0 79 175 15 minecraft:air
/tp @s 56 160 14
/place feature dudunka:mushroom_vale/turquoise_landmark 8 160 8
/place feature dudunka:mushroom_vale/violet_landmark 24 160 8
/place feature dudunka:mushroom_vale/golden_landmark 40 160 8
/dudunka_dev mushroom_vale decor bench 56 160 8 north
/dudunka_dev mushroom_vale decor lantern_post 64 160 8 north
/place feature dudunka:mushroom_vale/giant_red 72 160 8
```

Inspect silhouettes from above/below: ribbed gills, hanging spores, raised
spots, layered shoulders/crowns, cream fluted roots/stems, colored caps, real block
light and four-block canopy clearance. At another clear supported site use
`/place feature dudunka:mushroom_vale/giant_brown X Y Z`. Repeat bench/lantern
previews at fresh supported positions with east/south/west. Crowded or uneven
sites can refuse; move/clear the test platform explicitly. Right-click each
bench seat; check facing, free looking, occupancy with a second real player,
Shift dismount, breaking an occupied bench, logout/reconnect, dimension change
and save/restart. Seating should match Stage 2.4b.

For visible lantern lighting, roof the separate test corner and block skylight:

```text
/setblock 63 160 8 minecraft:air
/fill 60 166 4 68 166 12 minecraft:stone
/fill 60 160 4 60 165 12 minecraft:stone
/fill 68 160 4 68 165 12 minecraft:stone
/fill 60 160 4 68 165 4 minecraft:stone
/fill 60 160 12 68 165 12 minecraft:stone
/tp @s 64 160 10
```

Use F3 client **block light**, not sky/combined light. The lantern occupies
`64 163 8`; adjacent clear air should receive 14 and two blocks away 13, unless
another source contributes. Check east/west/up rather than the supporting
post. Remove it, wait for normal lighting updates, and confirm darkness returns:

```text
/setblock 64 163 8 minecraft:air
/setblock 64 163 8 dudunka:vale_lantern[facing=north]
```

For immediate asset/volume diagnosis only, set Music above zero and run:

```text
/playsound dudunka:music.mushroom_vale_theme music @s ~ ~ ~ 1 1
/stopsound @s music
/gamemode survival
```

Then close menus and enter Vale afresh to test **automatic** music, without
`/playsound`. The theme starts on the first unpaused client tick in both
Creative and Survival. Let the whole track finish: expect approximately 1–3
minutes of silence at 20 TPS. Switch Creative/Survival and cross all three Vale
biome borders during playback: no restart or doubled music. Test Music volume
zero/restoration independently from other categories. Return from safe ground:

```text
/dudunka_dev mushroom_vale return
```

The Vale managed track stops on exit; ordinary Minecraft music resumes through
its normal scheduler. Test Overworld/Nether travel, disconnect/reconnect,
save/reload and two actual clients with different Music slider values.

`/playsound ... music` proves registration, resource lookup, decoding and volume
routing only. It creates a separate command sound, bypasses biome selection,
Creative priority, MusicManager's current-track slot, delays and dimension exit.
Stop command-created audio before testing automatic music; issuing `/stopsound`
in mid-track can stop audible playback while MusicManager still temporarily
owns that slot, so a fresh dimension entry is the clean scheduling check.

Automated coverage includes configured mushroom placement/obstruction/clearance,
sparse frequency and reference checks, actual threaded engine light propagation
and removal, natural benches in finished chunks, preview permissions and existing
seating lifecycle, loaded biome SoundEvent/delay/replacement configuration,
OGG/resource/JAR checks. Full GameTests must use a fresh isolated working directory
and run once after focused corrections. The recorded init scripts/logs live in
`build/reports/mushroom-vale-stage25/`; choose an unused directory before rerunning.

Manual Minecraft visual/audio acceptance and full **Better MC BMC4 v55.5**
acceptance remain separate: shaders/resource packs, natural distribution over
multiple seeds, companion walking routes, seat poses/multiplayer, old-save
loading and real playback/transition timing require graphical testing.


## Stage 2.5a: final environment fixes and homecoming regression

Use Minecraft 1.20.1, Forge 47.4.13, Java 17 and a **new disposable world**.
Existing chunks retain their mushrooms/decorations. No generator migration or
terrain density change is performed. All previous Stage 2.2–2.5 work stays local.

Reproducible offline checks (the script selects Java 17 and refuses reused worlds):

```bash
python3 tools/validate_vale_resources.py
python3 tools/run_vale_stage25a_checks.py focused
python3 tools/run_vale_stage25a_checks.py survey --seeds 0 42 -71021
python3 tools/run_vale_stage25a_checks.py build
python3 tools/run_vale_stage25a_checks.py full
python3 tools/validate_vale_resources.py --mod-jar build/libs/dudunka-0.26.0-alpha.jar
./gradlew runClient -PmushroomValeDev=true
```

`focused` includes six live three-character homecoming repetitions, deterministic
bed-edge arrival, rain/homecoming sampling, real threaded lighting/removal,
natural bench sitting and the existing decoration/preview/seating checks.
`survey` scans normally generated FULL chunks without preview placement or
modified worldgen probabilities. Reports include seeds, biome-column denominators,
individual small mushrooms, complete mushroom roots by color/size, bench seats,
standalone three-seat benches and first-found coordinates. Survey/helper classes
are compiled only by the script's temporary init file and excluded from normal builds.
Logs and init scripts: `build/reports/mushroom-vale-stage25a/`.

Inspect natural scenery with the biome locate commands above. For reliable bench
interaction/model inspection the existing guarded preview remains:

```text
/dudunka_dev mushroom_vale decor bench 56 160 8 north
/dudunka_dev mushroom_vale decor lantern_post 64 160 8 north
```

Requires JVM opt-in `-PmushroomValeDev=true`, permission level 2, a player in
Vale, a supported clear site and no live occupants. It refuses unsafe sites.
This preview is separate from the survey's naturally generated benches.

For naturally generated examples, create a **new seed `0` world with this build**,
enter Vale, then use Creative inspection positions (no preview placement):

```text
/gamemode creative
/tp @s -1153 69 -523 facing -1151 67 -523
/tp @s -1146 82 -623 facing -1152 83 -629
/tp @s -1127 76 -564 facing -1133 77 -570
/tp @s -1074 79 -429 facing -1080 80 -435
```

These inspect, in order, a Memory bench, a turquoise detailed mushroom, a violet
detailed mushroom and a Pink Meadows golden detailed mushroom. Run one teleport
at a time. The bench itself is at `-1151 67 -523`; right-click a seat and Shift
out. These coordinates come from the final natural census and require current
worldgen in a clean seed-0 world. Other seeds use the survey's `SURVEY found` lines.

## Stage 2.5b: mushroom and lantern distribution

For current fresh-chunk rates and measured per-biome counts, see
[VALIDATION-MUSHROOM-VALE-STAGE-2.5b.md](VALIDATION-MUSHROOM-VALE-STAGE-2.5b.md).
Existing generated chunks retain their previous distribution. On a new world,
walk ordinary routes in all three biomes with adult Syusya and check that the
additional mushrooms leave useful walking lanes and do not obscure magical
mushrooms. Check that lantern posts remain occasional accents and retain their
approved model, warm appearance and light. Full BMC4 v55.5 visual acceptance
remains a separate manual check.

On a separate clear mycelium platform, compare medium detailed mushrooms:

```text
/fill 0 159 32 55 159 47 minecraft:mycelium
/fill 0 160 32 55 175 47 minecraft:air
/place feature dudunka:mushroom_vale/giant_turquoise 8 160 40
/place feature dudunka:mushroom_vale/giant_violet 24 160 40
/place feature dudunka:mushroom_vale/giant_golden 40 160 40
```

The three nine-wide `*_landmark` commands above remain the uncommon large size.
Check routes around stems and under the caps with adult Syusya. Preserve and
inspect nearby flower beds, small mushrooms and cottages.

For a hanging bulb independent of other light, use the roofed corner above:

```text
/setblock 64 163 8 dudunka:turquoise_landmark_cap[profile=4]
/setblock 64 163 8 dudunka:violet_landmark_cap[profile=4]
/setblock 64 163 8 dudunka:golden_landmark_cap[profile=4]
/setblock 64 163 8 minecraft:air
```

Each visible bulb is its own level-15 source. Verify F3 block-light spread and
removal, then check complete naturally generated mushrooms. Other cap parts
also emit light, so removing one bulb from an intact glowing cap does not make
the entire area dark. Vale's dimension type fixes the sky at noon: `/time set
midnight` cannot produce a Vale night. Use a fully roofed/enclosed space with
F3 sky light 0 for darkness; repeat the same source test in the Overworld at
`/time set midnight` for a real night. No invisible light helper is installed.

For homecoming, keep three valid personal markers loaded, choose HOME, wait
nearby, walk more than 12 blocks away for at least 10 seconds, then return within
8 blocks. Require WAVE/PURR/PEEK with trust >=25, including walking off bed/marker
edges; modes and bindings must remain HOME. Move away, issue stay, change the
home or introduce a threat to verify cancellation. Friendship must accrue only
while genuinely settled together. Real Minecraft/BMC4 acceptance remains manual.

## Stage 3.1: Pufik

Use Java 17 and a **new world** for natural spawning. Existing generated chunks
remain intact. In IntelliJ IDEA, open the Gradle tool window, set the Gradle
JVM to Java 17, create a Gradle run configuration with **Tasks and arguments**
`runClient -PmushroomValeDev=true`, then Run. Create a singleplayer Creative
world with cheats enabled. Stand on safe solid ground and run:

```text
/dudunka_dev mushroom_vale enter
/locate biome dudunka:glowing_mushroom_forest
```

Travel to the located forest coordinates in Creative or use `/tp X Y Z` with a
safe surface Y. On a clear dry patch, inspect the model directly:

```text
/summon dudunka:pufik ~ ~ ~
```

Watch its idle breathing and cap movement, small walking steps, stopping and
looking toward a player. Wait for the occasional happy hop or shy cap tilt;
right-click for the localized greeting and happy pose. Inspect from front,
side, behind and above in daylight and shade. Check that it stays on dry safe
paths, avoids shorelines, lava and cliffs, and never attacks. Then roam newly
generated glowing mushroom forest chunks at normal difficulty to assess
natural groups of 1–2 and their frequency; `/summon` is only a visual fixture.
Check that mooshrooms and existing flora remain present. Name one Pufik with a
name tag, save and quit, restart the same world, and verify its name and
identity remain. Ordinary unnamed Pufiks may despawn with vanilla creature
rules. Use `/dudunka_dev mushroom_vale return` to leave.

Repeat visual, pathfinding, interaction and natural-spawn acceptance in Better
MC BMC4 v55.5. Automated Gradle and GameTests do not establish graphical
quality, actual client animation timing or modpack spawn balance.

### Pufik deterministic look-up preview

Launch `./gradlew runClient -PmushroomValeDev=true` with Java 17, or use the
same Tasks and arguments in IntelliJ's Gradle run configuration. The existing
developer flag enables a client command; no extra launch property is needed.
On a flat clear patch in a Creative world with cheats, create a stationary
fixture two blocks south of yourself, facing you:

```text
/summon dudunka:pufik ~ ~ ~2 {NoAI:1b,Rotation:[180f,0f],Tags:["pufik_pose_fixture"]}
/tp @s ~ ~ ~ 0 30
/dudunka_pufik_pose rest
/dudunka_pufik_pose look_up
```

`rest` and `look_up` force fixed rendered poses on **all visible Pufiks**, with
idle, walking, emote, AI-look and proximity inputs bypassed for the preview.
They change only this client's rendering. Compare the two from a normal
standing camera at about 1–2.75 blocks; inspect both eyes, cheeks and smile,
and check the cap/stem join from the side. The preview holds head yaw at zero,
so approach the fixture from its front. At very close distances directly
above the cap, some occlusion is expected.

```text
/dudunka_pufik_pose off
```

`off` restores all live visual animations, including smoothed tracking toward
nearby player eyes. Approach and back away from the fixture to inspect the
transition; this look-up also works with the fixture's AI disabled. Summon an
ordinary Pufik separately to inspect walking, happy/right-click and shy poses
alongside tracking. The preview commands are absent without the developer
launch flag. No pose mode is saved in entity NBT or sent over the network.

## Stage 3.2a: mixed-color natural groups

At night, check newly generated loaded chunks in all three biomes. Natural
packs should contain 3–5 fireflies, with at least two colors whenever two or
more members spawn. Look for golden/turquoise plus occasional violet in Glowing
Forest; golden with some turquoise/violet in Pink Meadows; violet with some
turquoise/golden in Memory Forest. Spawn weights were lowered from 3 to 2 to
offset the larger pack size and limit overall population growth. Use fresh
chunks/world for a fair natural density comparison. `/summon` with explicit
`ValeVariant` NBT remains a separate visual/persistence check. Run the focused
and full multi-seed checks below; these exercise the actual group data selector.

## Stage 3.2: magical fireflies

Launch Minecraft 1.20.1 / Forge 47.4.13 using Java 17:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew runClient -PmushroomValeDev=true --offline
```

IntelliJ Gradle Tasks and arguments: `runClient -PmushroomValeDev=true --offline`;
Gradle JVM: Java 17. If ForgeGradle's preliminary certificate probe fails in
this cached environment, append `-Dnet.minecraftforge.gradle.check.certs=false`
(the offline GameTest runner already does this). Use a **new disposable world**,
seed `0`, cheats enabled,
shaders OFF initially. Leave companions at home. Enter Vale from safe ground:

```text
/dudunka_dev mushroom_vale enter
/time set midnight
/gamerule doDaylightCycle false
```

The current dimension type has a normal day/night cycle (the older Stage 2.5a
fixed-noon testing note no longer applies). For a deterministic fixture, these
commands deliberately replace the indicated small area in this disposable world:

```text
/fill 0 159 0 31 159 31 minecraft:mycelium
/fill 0 160 0 31 169 31 minecraft:air
/tp @s 8 160 8
/summon dudunka:vale_firefly 7 161 10 {NoAI:1b,ValeVariant:0,CustomName:'{"text":"Golden"}'}
/summon dudunka:vale_firefly 8 161 10 {NoAI:1b,ValeVariant:1,CustomName:'{"text":"Turquoise"}'}
/summon dudunka:vale_firefly 9 161 10 {NoAI:1b,ValeVariant:2,CustomName:'{"text":"Violet"}'}
```

Inspect from front/side/rear, close and several blocks away: tiny rounded body,
face, thin wings, gold/turquoise/violet color and independent soft abdomen
pulses. NoAI holds position but client wing animation continues. Check F3 block
light away from other luminous blocks: fireflies must not illuminate terrain.
Try a roofed dark area too. Repeat with the target shader/resource packs.

For **actual server flight**, use ordinary entities with AI enabled:

```text
/summon dudunka:vale_firefly ~ ~1 ~
/summon dudunka:vale_firefly 17 162 16 {ValeVariant:0}
/summon dudunka:vale_firefly 18 162 16 {ValeVariant:1}
/summon dudunka:vale_firefly 19 162 16 {ValeVariant:2}
```

Watch at least two minutes for gentle hovering, bobbing, short flights and
occasional loose groups, staying within six horizontal/three vertical blocks of
each home. Add a solid wall, water and lava nearby in the disposable fixture;
they must stop/choose another target without entering hazards. Move to a loaded
chunk edge and lower render/simulation distance to check stopping near unavailable
terrain. Set `/time set noon` and compare slower movement; set midnight again.
Name a moving firefly, save/quit/reopen, and confirm color, name, UUID (`/data get
entity @e[type=dudunka:vale_firefly,name=Golden,limit=1] UUID` for the named fixture)
and home NBT persist. Ordinary unnamed fireflies despawn beyond 48 blocks from every player;
named/persistent entities and Forge despawn vetoes are respected.

For natural spawning, locate each biome, travel to its actual surface and let
loaded terrain 24–48 blocks away tick at midnight. Compare the amount of
distinct groups encountered while exploring equal distances in the same fresh
world; natural rates are stochastic, so repeat across several Vale regions.
Groups should now be more common in open meadow clearings because the first-air
height over the ground is accepted; Stage 3.2c also accepts safe understory beneath
mushroom canopies and frees the cap when ordinary groups fall behind the player.

```text
/locate biome dudunka:glowing_mushroom_forest
/locate biome dudunka:pink_meadows
/locate biome dudunka:memory_forest
```

Confirm groups of 3–5, each mixed across at least two colors. Expect golden /
turquoise / violet in Glowing Forest, mostly golden with occasional turquoise
and violet in Pink Meadows, and mostly violet with occasional turquoise and
golden in Memory Forest. Summons do not test natural rates. Repeat
in daylight and the Overworld; no natural firefly spawn should occur there.
Inspect a pre-stage world too: existing Pufik, family UUIDs, album, music, saved
Vale terrain and travel must continue working. Repeat night flight, save/reload,
spawn density and two-player synchronization in Better MC BMC4 v55.5.

```text
/gamerule doDaylightCycle true
/dudunka_dev mushroom_vale return
```

Automated checks (graphical/BMC4 acceptance is separate):

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build verifyModels verifyPufik verifyValeFirefly --offline
python3 tools/run_vale_firefly_checks.py focused
python3 tools/run_vale_firefly_checks.py full
```

The runner creates a fresh seed-0 world and log under
`build/reports/mushroom-vale-stage32/` each time; focused wrapper classes/templates
are excluded from normal builds and the production JAR. The full run executes
all registered required GameTests, including the firefly variant, palette,
natural-spawn opportunity, and bounded-flight checks.

## Companion catch-up regression checks after Stage 3.2b

Run repeated travel checks in fresh worlds using Java 17 and cached Forge:

```bash
python3 tools/run_family_travel_checks.py isolated
python3 tools/run_family_travel_checks.py parallel
```

Each runs the counter, identity/occupied-landing, hazard/config rejection,
actual following-goal and ordinary snail-walking tests three times. Original
assertions and deadlines are retained. Logs are in `build/reports/family-travel/`.
The configuration refusal test uses a transient in-memory config copy so the
Forge file watcher cannot leak a disabled catch-up setting to later tests.

To reproduce the original contaminated configuration deliberately:

```bash
python3 tools/run_family_travel_checks.py isolated --repeat 1 --catch-up-disabled
```

That command intentionally starts a separate disposable world with catch-up
disabled and is expected to fail the two original assertions (0/2). It is a
diagnostic, not a passing regression gate. Complete suite command:
`python3 tools/run_vale_firefly_checks.py full`. Results and root cause are in
[the catch-up investigation](VALIDATION-FAMILY-TRAVEL-STAGE-3.2B.md).

## Stage 3.2c: natural flight and density acceptance

Launch with the target Java 17 runtime:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew runClient -PmushroomValeDev=true --offline -Dnet.minecraftforge.gradle.check.certs=false
```

Use a disposable normal world, first seed `0`, then repeat with `42` and `2026`.
Set render/view distance and simulation distance to **8 chunks**. Begin with
shaders off. These commands prepare natural nighttime observation:

```text
/gamemode creative
/difficulty normal
/dudunka_dev mushroom_vale enter
/gamerule doMobSpawning true
/gamerule doDaylightCycle false
/time set 18000
/locate biome dudunka:glowing_mushroom_forest
/locate biome dudunka:pink_meadows
/locate biome dudunka:memory_forest
```

Travel to each returned biome and descend to its walkable soil **beneath** the
mushroom canopy. Observe in Creative or Survival; Spectator does not drive
natural mob spawning. Wait about ten seconds for the nearby spawn ring to fill,
then explore for two minutes, stopping to look at nearby groups. Use ordinary
generated terrain throughout this density check. No summons are needed.

The natural spawn ring is 24–48 blocks. Packs request 3–5 and remain mixed when
at least two members successfully spawn; incomplete packs remain possible under
vanilla terrain/collision rules. Look for several groups over the exploration
route, not a guaranteed fixed count at every position. Check all three palettes.

Count loaded nearby natural residents without altering them:

```text
/scoreboard objectives add vale_probe dummy
/scoreboard objectives setdisplay sidebar vale_probe
/execute store result score nearby vale_probe if entity @e[type=dudunka:vale_firefly,distance=..48]
/execute store result score golden vale_probe if entity @e[type=dudunka:vale_firefly,distance=..48,nbt={ValeVariant:0}]
/execute store result score turquoise vale_probe if entity @e[type=dudunka:vale_firefly,distance=..48,nbt={ValeVariant:1}]
/execute store result score violet vale_probe if entity @e[type=dudunka:vale_firefly,distance=..48,nbt={ValeVariant:2}]
/execute store result score bats vale_probe if entity @e[type=minecraft:bat,distance=..128]
```

Repeat the five `execute` commands after moving and after standing still for a
minute. These count nearby entities, including any hidden by foliage; compare
with the groups actually visible. Vanilla's ambient cap is still 15 shared with
bats/other ambient mobs, with normal small-pack overshoot. Counts should settle
rather than increase indefinitely. Record BMC4's competing ambient population too.

Follow one **naturally spawned** firefly without naming it or changing its AI:

```text
/tag @e[type=dudunka:vale_firefly,sort=nearest,limit=1,distance=..48] add flight_probe
/data get entity @e[type=dudunka:vale_firefly,tag=flight_probe,limit=1] Pos
/data get entity @e[type=dudunka:vale_firefly,tag=flight_probe,limit=1] Motion
/data get entity @e[type=dudunka:vale_firefly,tag=flight_probe,limit=1] NoGravity
/data get entity @e[type=dudunka:vale_firefly,tag=flight_probe,limit=1] OnGround
/data get entity @e[type=dudunka:vale_firefly,tag=flight_probe,limit=1] ValeVariant
```

Watch for at least a minute: takeoff from the surface, normal hover around 1–3
blocks above nearby safe terrain, changing X/Y/Z, gentle turns/circles and loose
pack cohesion. Repeat `Pos`/`Motion` queries after five seconds. `NoGravity`
should be `1b`; after takeoff `OnGround` should normally be `0b`. Follow beside
walls, mushroom stems and water: the firefly should turn or retry without entering
blocks/fluids. Keep within 48 blocks to retain an ordinary tagged resident.

Save/quit/reopen nearby and verify its tag, UUID, variant and flight survive.
Set `/time set 6000`: new natural spawning stops, while existing residents hover
and wander more slowly instead of settling. Set midnight again. Walk away more
than 48 blocks and return: ordinary residents may be replaced by fresh groups.
Finally remove the probe and restore the clock:

```text
/tag @e[type=dudunka:vale_firefly,tag=flight_probe] remove flight_probe
/scoreboard objectives remove vale_probe
/gamerule doDaylightCycle true
```

Repeat with two players and in BMC4 v55.5, checking smooth synchronized positions,
the approved colors/wings/glow, bounded population, and save compatibility. Client
rendering and BMC4 performance are manual acceptance, separate from headless tests.

Automated commands:

```bash
python3 tools/run_vale_firefly_checks.py focused
python3 tools/run_vale_firefly_checks.py population --seed 0
python3 tools/run_vale_firefly_checks.py population --seed 42
python3 tools/run_vale_firefly_checks.py population --seed 2026
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build verifyModels verifyValeFirefly --offline -Dnet.minecraftforge.gradle.check.certs=false
python3 tools/run_vale_firefly_checks.py full
```

Population tests use unmodified generated Vale terrain, a real server player,
8-chunk simulation/view distances and normal engine natural spawning for 1,200
ticks per biome-start route. The observer moves 16 blocks every 200 ticks.
Join events record completed spawns and their shared pack data. Fifty snapshots
measure nearby populations and a visibility proxy (within 48 blocks, within 8
vertical blocks, with line of sight), not actual client pixels. Tests restore
clock/rules/distances/listeners and discard their observers/residents, including
after failure via batch cleanup. The generated startup hook changes Minecraft's
otherwise hardcoded GameTest seed before worlds load; `server.properties` alone
does not change GameTest terrain. It is excluded from ordinary builds/JARs.

[Stage 3.2c results and limitations](VALIDATION-MUSHROOM-VALE-STAGE-3.2C.md).

## Stage 3.2d natural landmarks and Pufik acceptance

Use Java 17, Minecraft 1.20.1 and Forge 47.4.13. For the development client:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew runClient
```

Create a **new Creative world** with seed `0`, then repeat with `42` and `2026`.
Enable cheats and use view/simulation distances of 8 chunks for the matched
observation route. Enter Vale using the existing opt-in travel flow, or move only
the observer with:

```mcfunction
/execute in dudunka:mushroom_vale run tp @s 0 150 0
/gamerule doMobSpawning true
/time set day
/locate biome dudunka:glowing_mushroom_forest
```

Teleport to the reported biome coordinates at a safe height, then descend to
walkable soil. Explore near ground level through fresh terrain for several
minutes, walking at least 160 blocks and pausing periodically. Spectator mode
cannot drive normal spawning. High Creative flight can move the player beyond
the 128-block spawn/despawn range of ground creatures; dense foliage, hills and
Pufik's small size also affect visibility. Both runtime and chunk-generation
spawning obey the real safety rules. Existing mooshrooms/other passive animals
can fill the shared cap; do not remove animals or alter caps for acceptance.

One measured dry forest route in seed **42** starts at X=8192, Z=8192.
Move the observer with `/execute in dudunka:mushroom_vale run tp @s 8192 96 8192`,
descend to soil, then walk roughly 160 blocks east over two minutes. Counts vary;
this route is an observation aid, not a guaranteed spawn point.

This read-only diagnostic counts loaded Pufiks within 128 blocks:

```mcfunction
/execute as @e[type=dudunka:pufik,distance=..128] run data get entity @s Pos
```

Pufiks naturally belong to Glowing Mushroom Forest. A naturally born Pufik can
later wander across a biome boundary. Repeat daytime and nighttime observation;
Pufik has no night-only gate. Do not use `/summon`, spawn eggs or manufactured
spawn floors. Nighttime firefly acceptance is already established and unchanged.

For cottages, use `/locate biome dudunka:pink_meadows` (Pufik cottage) and
`/locate biome dudunka:memory_forest` (Wise Fly Agaric cottage), then explore
new ground over several hundred blocks. Cottages remain sparse landmarks and
are placed features, so `/locate structure` cannot find them. Do not use
`/place feature` or the developer cottage/decor preview for natural acceptance.
Measured natural specimens include a Pufik cottage near **-1187, 70, 635** in
seed **42**, and a Wise cottage near **-1061, 100, -914** in seed **0**. Teleport
the observer safely above these sites and descend; generation order can change
which nearby sites succeed. These are naturally generated specimens, not preview
commands. Inspect doors from both sides, step through entrances, inspect furnished
interiors and walk around the preserved exterior terrain. Test a save/reload.

Look for lantern posts and three-seat benches in all three Vale biomes.
Confirm warm block light at night, functional right-click seating and safe
normal dismount. Bring Syusya through several naturally decorated areas and
check routes around props/cottages. Flower beds and small mushrooms retain their
approved appearance. Repeat in BMC4 v55.5, with shaders/settings normally used,
and with two players. Keep previously visited chunks intact: never delete
region files, reset chunks or expect existing terrain to be regenerated.

Offline reproducible checks (each survey uses a new isolated test world):

```bash
python3 tools/validate_vale_resources.py
python3 tools/validate_vale_stage32d.py
python3 tools/run_vale_stage32d_checks.py focused
python3 tools/run_vale_stage32d_checks.py survey --baseline --seeds 0 42 2026
python3 tools/run_vale_stage32d_checks.py survey --seeds 0 42 2026
python3 tools/run_vale_stage32d_checks.py build
python3 tools/run_vale_stage32d_checks.py full
```

Survey baseline is pinned to published `65ac290`; instrumentation/seed hooks
are compiled only in the isolated runner and removed afterward. Never package
survey helpers. Run the ordinary build after surveys and verify the packaged JAR
with `python3 tools/validate_vale_stage32d.py --jar build/libs/dudunka-<version>.jar`.

## Stage 3.3 friendly magical spider

Use Java 17, Minecraft 1.20.1 and Forge 47.4.13. From the repository root:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew runClient -PmushroomValeDev=true
```

The Gradle property enables the existing permission-2 developer travel commands;
there is no new portal. Create a new world with cheats, stand on safe ground,
and enter Vale:

```mcfunction
/dudunka_dev mushroom_vale enter
/summon dudunka:vale_spider ~ ~ ~
```

For a named visual specimen that stays across ordinary despawning:

```mcfunction
/summon dudunka:vale_spider ~ ~ ~ {CustomName:'{"text":"Little Vale friend"}',PersistenceRequired:1b}
```

Inspect at ground level from front, sides, back, and a standing player's normal
camera at about 1–3 blocks. Check the ivory stepped silhouette, rust-red cap,
eight separate short legs, large highlighted eyes, rosy cheeks, smile and texture
seams. Watch gentle breathing, coordinated walking and the curious upward look.
Right-click with an empty main hand: a short localized greeting and single-leg
wave should appear in English and Russian. Observe occasional autonomous greetings
and approaches; sprint close to it, then stop, to observe a short shy dip/retreat
and recovery (cooldown about eight seconds). Confirm smooth animation, readable
eyes beneath the brim, stable feet and no stretching, flicker or disconnected
geometry. Use ordinary lighting and BMC4 shaders/settings. Check no targeting,
bites, webs, block destruction, taming, trading or progression UI. Test name,
UUID and health across save/reload. A summon works outside Vale for inspection;
natural spawning remains restricted to the two Vale forests.

For **natural acceptance**, use separate fresh worlds with seeds `0`, `42` and
`2026`, view/simulation distance 8, a non-spectator player, and ordinary spawning:

```mcfunction
/gamerule doMobSpawning true
/time set day
/locate biome dudunka:memory_forest
/locate biome dudunka:glowing_mushroom_forest
```

Descend to dry soil and explore fresh terrain, pausing to observe at ground
level. Do not summon specimens, manufacture spawn floors, remove competitors or
change caps in these worlds. A reproducible Memory-start route in seed `42`
begins near **7648, 8832** (X,Z) and runs 352 blocks east. From inside Vale, use
`/tp @s 7648 110 8832`, descend safely to soil, then walk and pause over about
four minutes. Natural outcomes vary. The server surveys include generated
animals and ordinary despawning: runtime spawning alone can stop when the
creature cap is full, and foliage/hills can obscure nearby spiders. Rare secondary
forest encounters and zero-encounter routes are retained in the report.

Read-only inspection of loaded residents:

```mcfunction
/execute as @e[type=dudunka:vale_spider,distance=..128] run data get entity @s Pos
```

Repeat at night with the existing fireflies, with two players, and in BMC4 v55.5.
Check Pufik greetings/movement, firefly flight/palettes, music, companions/album
and old-save UUID/data compatibility. Previously generated terrain must remain
intact. New biome entries do not guarantee that an already cap-filled old area
will produce a spider. Return using `/dudunka_dev mushroom_vale return`.

Reproducible offline/server checks, each server run in an isolated fresh world:

```bash
python3 tools/run_vale_stage33_checks.py focused
python3 tools/run_vale_stage33_checks.py survey --seeds 0 42 2026
python3 tools/run_vale_stage33_checks.py full
python3 tools/run_vale_stage33_checks.py build
python3 tools/preview_vale_spider.py
python3 tools/validate_vale_resources.py --mod-jar build/libs/dudunka-0.26.0-alpha.jar
python3 tools/validate_vale_stage33.py --jar build/libs/dudunka-0.26.0-alpha.jar
```

The build mode runs `build`, `verifyModels`, `verifyPufik` and `verifyValeSpider`;
`check` also runs the existing firefly/music checks. `verifyValeSpider` bakes the
client model and validates anatomy, original atlas, UVs, locales, renderer/layer
references and bounded rigid poses. The dedicated-server GameTests cover entity
registration, actual registered spawn rules, both habitats and excluded biomes,
hazards/fluids/clearance/cliffs, non-hostile behavior, localized interaction,
vanilla NBT round-trip, shared creature caps and ordinary/protected despawning.
Survey code and seed hooks live outside production sources, are opt-in, and are
removed from build outputs before ordinary packaging. A final JAR audit verifies
this. Automated checks and offline preview do not replace Minecraft/BMC4 visual,
multiplayer or old-world acceptance; see the [validation report](VALIDATION-MUSHROOM-VALE-STAGE-3.3.md).

## Stage 3.4: Wise Fly Agaric

In IntelliJ, select Java 17 for Project SDK and Gradle JVM. Create a **Gradle**
Run Configuration targeting this project, Tasks and arguments:
`runClient -PmushroomValeDev=true --offline -Dnet.minecraftforge.gradle.check.certs=false`.
Run it. Alternatively, run `genIntellijRuns` and add
`-Ddudunka.devMushroomVale=true` to the generated Java `runClient` VM options.
The property must be present in the integrated server JVM. Terminal equivalent:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew runClient -PmushroomValeDev=true --offline -Dnet.minecraftforge.gradle.check.certs=false
```

Use a fresh Creative world with commands enabled for inspection:

```mcfunction
/dudunka_dev mushroom_vale enter
/summon dudunka:wise_fly_agaric ~ ~ ~
```

On dry open ground, stand 1, 1.5, 2 and 3 blocks in front, then circle him.
Compare the red cap/spots, eyes, mouth, beard, cloak, pouch, staff and lantern
against `reference/mushroom-vale/wise_fly_agaric.png`. Check face visibility,
texture seams, feet contact and actual scale. Right-click with an empty main
hand: verify a gentle localized line and wave/bow; clicking repeatedly within
four seconds must not spam dialogue. Switch between English and Russian.
Observe idle breathing, walking, thought and reading pauses. Approach while
standing to check LOOK_UP; use two elders/players to check independent poses
and multiplayer synchronization. Check no attacks, item pickup or terrain edits.
Save/restart and check UUID, name and health. Summoning is also permitted outside
Vale; natural spawning remains restricted.

For ordinary natural encounters, use separate fresh seeds `0`, `42`, `2026`,
view/simulation distance 8, a non-spectator player and ordinary mob spawning:

```mcfunction
/gamerule doMobSpawning true
/time set day
/locate biome dudunka:memory_forest
```

Explore new dry forest terrain and pause at ground level. Do not summon, remove
competitors, manufacture floors or change caps in natural-acceptance worlds.
Read-only inspection of loaded elders:

```mcfunction
/execute as @e[type=dudunka:wise_fly_agaric,distance=..128] run data get entity @s Pos
```

Cottages have no automatic residents in this stage. Existing populated chunks
can remain cap-filled; new exploration supplies chunk-generation opportunities.
Repeat in BMC4 v55.5, with shaders and multiplayer, and check Pufik, spiders,
fireflies, companions, album, cottages and music. Preserve old terrain and saves.
Return with `/dudunka_dev mushroom_vale return`.

Reproducible checks (isolated disposable worlds; Java 17 and cached Forge):

```bash
python3 tools/run_vale_stage34_checks.py focused
python3 tools/run_vale_stage34_checks.py survey --seeds 0 42 2026
python3 tools/run_vale_stage34_checks.py full
python3 tools/run_vale_stage34_checks.py build
python3 tools/preview_wise_fly_agaric.py
python3 tools/validate_vale_resources.py --mod-jar build/libs/dudunka-0.27.0-alpha.jar
python3 tools/validate_vale_stage34.py --jar build/libs/dudunka-0.27.0-alpha.jar
```

`verifyWiseFlyAgaric` bakes anatomy and checks atlas, UVs, localized lines,
renderer wiring, rigid poses, prop/reset behavior and triangle camera sightlines.
Focused GameTests cover registration/attributes, all biome/dimension exclusions,
hazard/clearance/cliff spawn rules, actual AI movement, dialogue cooldown,
non-hostility, NBT round-trip, unchanged cap and protected despawning. The opt-in
survey records completed engine births and standing-observer sightline proxies;
its helpers are excluded from the shipped JAR. Automated NBT and geometry checks
are separate from full client save/restart and visual acceptance.

Stage 3.4 survey commands require at least one completed birth per measured
route and return nonzero for zero-birth routes (seed 0 did so in validation).
Keep those negative results. Seed 42's measured encounter route starts near
X=7648, Z=8832 and continues 352 blocks east. The current full-suite report
includes one unchanged firefly takeoff failure; see the dedicated validation
report before treating this stage as full regression acceptance.

## Stage 3.4a: Wise cottage residency

Use Java 17, a disposable world/copy, and the existing developer opt-in:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --offline -Dnet.minecraftforge.gradle.check.certs=false -PmushroomValeDev=true runClient -x downloadAssets
```

Enter with `/dudunka_dev mushroom_vale enter`. For a discovered cottage, find its
floor origin (the blueprint center at the floor block Y) and use
`/dudunka_dev mushroom_vale resident X Y Z`. This read-only developer command
reports recognition, rotation, issued UUID, home and whether that UUID is loaded.
`loaded=false` deliberately does not distinguish unload from removal.
Cottages are features: `/locate structure` cannot find them. Natural discovery:
`/locate biome dudunka:memory_forest`, then explore on foot.

For a repeatable **disposable** fixture, first stand nearby to load the area,
then prepare the platform, select Memory Forest, and stand outside the footprint:

```mcfunction
/tp @s 32 161 24
/fill 20 159 -4 44 159 20 minecraft:mycelium
/fill 20 160 -4 44 171 20 minecraft:air
/fillbiome 16 144 -16 47 175 31 dudunka:memory_forest
/dudunka_dev mushroom_vale cottage wise 32 160 8 north
/dudunka_dev mushroom_vale resident 32 160 8
```

Stay nearby in Creative/Survival (not Spectator) for the nine-slot scan (up to
nine seconds for one visitor; more visitors share the bounded scan). Inspect:

```mcfunction
/dudunka_dev mushroom_vale resident 32 160 8
/execute as @e[type=dudunka:wise_fly_agaric,distance=..32] if data entity @s WiseCottageOrigin run data get entity @s UUID
/execute as @e[type=dudunka:wise_fly_agaric,distance=..32] if data entity @s WiseCottageOrigin run data get entity @s WiseCottageOrigin
/execute as @e[type=dudunka:wise_fly_agaric,distance=..32] if data entity @s WiseCottageOrigin run data get entity @s WiseCottageRotation
/execute as @e[type=dudunka:wise_fly_agaric,distance=..32] if data entity @s WiseCottageOrigin run data get entity @s PersistenceRequired
```

The command prints the precise resident UUID; `/data get entity UUID` with that
printed UUID shows its complete NBT even if independent elders are nearby.
Do not summon a second elder to test automatic residency. Observe a few trips:
he should walk through the door, descend the porch, explore nearby, return and
sometimes think/read. The approved appearance and all six English/Russian
interaction messages should remain intact. Verify player access, door operation,
water/cliff avoidance and no teleporting in all four cottage rotations.

Record the UUID, leave beyond simulation distance, return, then save/quit and
restart the same world. Reinspect the same origin; the UUID must remain identical.
Repeat with two simultaneous players. On a **copy**, deliberately kill the UUID,
leave/reload/restart, and verify no replacement. Also visit an intact old furnished
cottage in a Stage 3.4 save: it should enroll without modifying blocks. Older
unfurnished shells or broken structural fingerprints remain uninhabited.
Run these acceptance checks in Forge and Better MC BMC4 v55.5 separately.

Automated commands (fresh isolated worlds, actual completion summaries required):

```bash
python3 tools/run_vale_stage34a_checks.py residency
python3 tools/run_vale_stage34a_checks.py focused
python3 tools/run_vale_stage34a_checks.py firefly --seed -4853257702107486027
python3 tools/run_vale_stage34a_checks.py build
python3 tools/run_vale_stage34a_checks.py full
```

The residency tests cover structural recognition, automatic nearby discovery,
concurrent visitor requests, durable issuance/entity reload, real chunk unload
and disk reload, deliberate removal, interrupted issuance, existing-world block
preservation, unloaded-boundary rejection and live door/porch/home navigation.
They do not replace a real process restart or graphical multiplayer/BMC4 checks.

## Stage 3.4b: Pufik cottage residency

Use a disposable world with the existing developer opt-in enabled. Enter
Mushroom Vale and prepare a Pink Meadows fixture near the player:

```mcfunction
/tp @s 32 161 24
/fill 20 159 -4 44 159 20 minecraft:mycelium
/fill 20 160 -4 44 171 20 minecraft:air
/fillbiome 16 144 -16 47 175 31 dudunka:pink_meadows
/dudunka_dev mushroom_vale cottage pufik 32 160 8 north
/dudunka_dev mushroom_vale resident pufik 32 160 8
```

Remain within 48 blocks and wait for the bounded visitor scan. Reinspect with
the last command; the resident UUID should remain stable. Check its association
and persistence directly:

```mcfunction
/execute as @e[type=dudunka:pufik,distance=..32] if data entity @s PufikCottageOrigin run data get entity @s UUID
/execute as @e[type=dudunka:pufik,distance=..32] if data entity @s PufikCottageOrigin run data get entity @s PufikCottageOrigin
/execute as @e[type=dudunka:pufik,distance=..32] if data entity @s PufikCottageOrigin run data get entity @s PufikCottageRotation
/execute as @e[type=dudunka:pufik,distance=..32] if data entity @s PufikCottageOrigin run data get entity @s PersistenceRequired
```

Observe the resident inside, walking through the spruce door, exploring the
porch and returning home. Check that he avoids hazards, does not jump by
teleporting, occasionally uses existing emotes, looks toward approaching
players and keeps the localized greeting. Repeat inspection and visit with two
players; only one entity should carry this origin. Save, quit and restart, then
check the same UUID. On a world copy, remove that UUID and verify subsequent
visits do not recreate it. Visit an existing intact generated cottage to check
old-world enrollment. Confirm a wild Glowing Mushroom Forest Pufik has no
`PufikCottageOrigin` and remains despawnable. Repeat in BMC4 v55.5.

Focused automated coverage is in `PufikCottageTests` and the existing Pufik and
Wise cottage test classes. External validation commands:

```bash
./tools/test.sh build
./tools/test.sh gametest
```

These commands have not been run by Codex for Stage 3.4b.

## Stage 3.5: Fluffy Jumpers and residency/travel regressions

This task explicitly permits Codex to run builds and GameTests. Subsequent tasks
retain the normal `DEVELOPMENT-WORKFLOW.md` roles. Use Java 17:

Recorded result (2026-10-11): build and all model/resource verifiers **PASS**;
focused residency/Jumper/travel set **30/30 PASS**, alternate-seed travel
**7/7 PASS**, final full suite **217/217 PASS, 0 FAIL**. Graphical Minecraft,
BMC4 and two real clients remain **NOT RUN**. Detailed logs and earlier failed
diagnostic runs are recorded in the linked validation report below.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh build
python3 tools/run_vale_stage35_checks.py all
python3 tools/run_vale_stage35_checks.py travel --seed 42
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh all
python3 tools/preview_fluffy_jumper.py
```

`regressions` runs the three existing live shared-furniture tests plus peaceful
Wise/Spider interaction checks.

Focused groups are `pufik`, `wise`, `travel`, `jumper`, `regressions` and `all`; `--method`
selects one actual test method. The runner creates isolated worlds and a temporary
namespace wrapper around the real methods, preserving their timeouts and cleanup
hooks. Wrappers use distinct batch names, and generated build-output classes and
templates are removed on exit. Both runners refuse overlapping runs and require
an explicit completed passing GameTest summary. Logs and focused input manifests
are under `build/reports/mushroom-vale-stage35/`; wrapper reports remain under
`build/reports/dudunka-tests/`.

The full wrapper and focused runner default to seed `-255494441983840131`;
`DUDUNKA_TEST_SEED` overrides the full wrapper seed. Forge's GameTest launch through
`Main` reads `server.properties` before constructing its `GameTestServer`, unlike
the unused vanilla static `GameTestServer.create` seed constant. That bypass also
omits vanilla's no-random-mobs/no-weather-cycle GameTest rules. A startup hook
limited to `GameTestServer` now supplies those intended rules, with a required
regression. Natural-population tests explicitly enable spawning and restore its
prior state. Production servers keep their own game rules. Travel command
tests own and restore a small arrival surface at `(8,64,8)`, so safe transfer,
veto, fallback and blocked-arrival assertions do not rely on generated terrain.
The blocked-arrival scenario obstructs both the near-anchor fixture and all real
heightmap candidates. Production landing checks still reject hazards and never
terraform. Terrain/biome/generation tests continue to exercise real worldgen.
Shared-furniture fixtures seed entity RNG and suppress unrelated idle roaming,
keeping all live home/family goals and original assertions. Wise interaction
checks use a reversible supported roofed room and still compare every block.
Pufik fixtures restore blocks, original biome quart samples and prior forced
chunk flags after each batch; fixture players and entities are discarded on
success or failure through cleanup hooks.

`FluffyJumperTests` covers seven scenarios: registration/attributes/assets/family
independence; actual biome spawn predicates, weights, groups and unsafe surfaces;
localized interaction/cooldown, save/reload and natural despawn policy; repeated
real hops/landings/rests with per-tick bounds and no clipping/damage; blocked swept
flight, hazards and cliffs; two real server players with peaceful loose group
hops; disabled AI and actual vanilla far-distance despawn. Pufik movement is
covered in four cottage rotations alongside ledger uniqueness and chunk reload.
All eight Wise residency regressions and seven travel/bookmark/permission
scenarios are included in the focused `all` selection. `verifyFluffyJumper`, wired
into Gradle `check`, bakes the actual model and checks anatomy, three atlases,
UVs, localization, renderer wiring, 300 finite poses, squash/stretch/reset and
nine exported preview poses. The preview is an offline render, not a screenshot
from Minecraft.

### Exact manual Minecraft / Better MC BMC4 v55.5 acceptance

Use a disposable test world and the built jar with Minecraft 1.20.1 / Forge
47.4.13 / Java 17. Start with JVM flag `-Ddudunka.devMushroomVale=true` (or
`./gradlew -PmushroomValeDev=true runClient`); developer commands require operator
permission. This does not authorize publishing or modifying a production save.

1. Stand on safe solid ground and run `/dudunka_dev mushroom_vale enter`.
   Run `/locate biome dudunka:pink_meadows`; travel to the returned biome in
   Creative, land on dry open soil, then switch to Survival. Explore fresh chunks
   in daylight for natural groups of 2–4. Count nearby wildlife if needed with
   `/execute in dudunka:mushroom_vale as @e[type=dudunka:fluffy_jumper,distance=..64] run data get entity @s Pos`.
   Confirm other meadow inhabitants, Pufik cottages and fireflies remain present;
   natural groups are probabilistic and share the vanilla creature cap.
2. On a clear meadow, run `/summon dudunka:fluffy_jumper ~2 ~ ~ {FluffyColor:0}`;
   repeat with color 1 and 2. Inspect front/side/back and close player-eye views:
   round tufted silhouette, tiny paws, friendly visible face, three pastel colors,
   no missing texture, clipping, severe stretching or animation state leakage.
3. Watch each for at least 60 seconds: small hops, grounded pauses, occasional
   higher jump, soft landing squash and smooth return to idle. Approach slowly,
   then move around the group. Confirm curious looking and occasional loose
   coordinated hops. Right-click empty-handed: cheerful sway and one brief
   friendly message. Repeat with a held item and check it is retained. Switch
   language between English and Russian and repeat after the two-second cooldown.
4. In a disposable fenced test area, provide dry grass/moss with safe room, then
   nearby water, magma/fire, a two-block drop and a low ceiling. Confirm normal AI
   refuses unsafe launch corridors, cannot suffocate itself or walk off the edge,
   and rests when confined. Do not judge external pushing or mid-flight edits as
   an AI-planned route. Inspect nearest entity with
   `/data get entity @e[type=dudunka:fluffy_jumper,sort=nearest,limit=1]`.
5. Hit one gently in Survival, observe players and nearby creatures remain
   unharmed, and check no attack, taming, breeding, trade or inventory UI appears.
   With two real clients on a dedicated server, have both right-click the same
   group; confirm identical palette/movement/emotes, independent localized
   messages and no duplication or client/server crash.
6. Inspect `UUID`, `FluffyColor`, `Health` and `Pos` using `/data get entity`;
   name one with a name tag, save/quit/reload and confirm identity/color/name/health.
   Leave an unnamed group beyond ordinary despawn range, return, and ensure there
   is no forced persistence or global replenishment service. Name tags retain
   normal vanilla persistence.
7. Find an intact Pink Meadows cottage, visit as a non-spectator and inspect
   `/dudunka_dev mushroom_vale resident pufik <originX> <originY> <originZ>`.
   Observe a full departure/return (up to 90 seconds), door opening/closing, slab
   transitions and no teleport. Repeat for rotated cottages; a safe disposable
   preview can be placed with
   `/dudunka_dev mushroom_vale cottage pufik <x> <y> <z> north` (also east/south/west).
   Preview requires a clear supported footprint, loaded chunks, an appropriate
   biome and the player standing outside it. Record the resident UUID, leave until
   the chunk unloads, return and reload the world: exactly the same resident must
   remain. Deliberately removed residents must never be reissued.
8. Repeat departure/return and UUID reload in a Memory Forest Wise cottage;
   `/dudunka_dev mushroom_vale resident <originX> <originY> <originZ>` inspects it.
   Confirm existing Pufik, Spider, Firefly and Wise models/interactions are unchanged.
9. Run `/dudunka_dev mushroom_vale return`; confirm safe departure bookmark,
   identity, family companions and album behavior. Test an existing saved world
   in a copy: old cottages remain eligible and existing resident UUIDs/ledger,
   biome IDs and album protocol 9 remain compatible.

Automated results, real-client/BMC4 acceptance and publication are separate
statuses in [the Stage 3.5 validation record](VALIDATION-MUSHROOM-VALE-STAGE-3.5.md).

## Stage 3.6: Living Mushroom Vale

Run the focused population tests and complete required validation from the
repository root with Java 17:

```bash
./tools/test.sh all
```

`ValeWildlifePopulationTests` checks all three biome creature lists, absence of
vanilla creature competitors, exact magical habitats/weights/groups, independent
species and total limits, cooldown behavior, `doMobSpawning=false`, and the
Mushroom Vale dimension gate. Existing Pufik, Vale Spider, Fluffy Jumper,
firefly, Wise residence, Pufik residence, and shared creature-cap assertions
remain in their existing GameTests. The entity-specific spawn tests continue to
exercise safe ground, headroom, hazards, cliffs, and dimension restrictions.

### Manual Survival encounters (no `/summon`)

Use a fresh disposable world first and a copy of an existing save second. Keep
view and simulation distances at ordinary values (8 chunks is the test target),
leave `doMobSpawning` enabled, and explore on foot at normal speed. Do not use
commands, spawn eggs, artificial spawn floors, or spectator mode to create the
observed wildlife.

1. Locate Glowing Mushroom Forest and walk across several dry forest regions.
   Look for Pufik regularly and Vale Spiders occasionally. Follow the route into
   Memory Forest and verify spiders appear there as well.
2. Walk through open Pink Meadows and look for Fluffy Jumpers in small groups.
   Confirm their colors vary and their short playful hops remain safe on the
   terrain. Keep moving between observations; idle players should not trigger
   population assistance.
3. Repeat in Survival in both fresh terrain and already generated terrain. In
   the existing save, check that mooshrooms, rabbits, sheep, pigs, and other
   animals already present remain alive and unchanged. Explore for several
   minutes so the bounded assistance can run under normal conditions.
4. Repeat with two players walking through overlapping areas. Confirm the Vale
   population grows gradually rather than producing duplicate bursts. Then set
   `/gamerule doMobSpawning false`, continue moving, and confirm no new wildlife
   appears; restore the gamerule afterward.
5. Check all three biomes' magical habitats, fireflies, cottages and Wise/Pufik
   residents. Ensure no newly spawned vanilla passive animals appear in Vale
   while Overworld animal spawning remains normal. Save, quit and reload, then
   confirm existing entity identities and cottage resident UUIDs remain intact.

New chunks get exclusive magical creature lists during generation. Existing
chunks retain all terrain and already living vanilla animals; future natural
rolls use the updated biome settings, and exploration assistance has a separate
nearby wildlife budget so a pre-existing full vanilla creature cap does not
permanently prevent encounters. Assisted spawns remain stochastic and require
safe loaded terrain. Record the time, biome, player count, simulation distance,
and visible/nearby counts in each world. Automated GameTests do not replace this
Survival/BMC4 acceptance.

The short implementation record is
[VALIDATION-MUSHROOM-VALE-STAGE-3.6.md](VALIDATION-MUSHROOM-VALE-STAGE-3.6.md).
