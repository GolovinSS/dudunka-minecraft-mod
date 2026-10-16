# Stage 4.2d — The Secret of the Silver Web

Target: Minecraft 1.20.1 / Forge 47.4.13 / Java 17 / Better MC BMC4 v55.5.
Version **0.27.1-alpha**, album protocol **10**. Existing uncommitted work preserved.

| Status | Result |
| --- | --- |
| Implementation | Chapter 4 implemented; Chapter 5 and album UI deferred |
| Final build and model/resource checks | **PASS**, `20261012T222107Z-3788735` |
| Final focused music GameTests | **PASS, 29/29**, `20261012T222201Z-3789676` |
| Final full GameTests | **PASS, 302/302**, `20261012T222329Z-3790599` |
| Manual runClient / BMC4 / real multiplayer / graphical and audio acceptance | **NOT RUN — intentionally deferred until the complete music quest chain** |
| Commit / push / publication | **NOT DONE** |

## Design and files

`ValeHarpQuest.java` binds the native `dudunka:silver_web_harp` to three existing
string columns at anchor-relative X −2, 0, +2, Z 0, Y 1–6. West is decreasing X;
east is increasing X. Each right-click cycles low, middle, high. Each selected
tone has private harp audio, 1/2/3 vertically separated white sparkles and an
explicit localized text label giving both current and required tone. Initial
tones are all middle. The spiders’ woven clue asks for west low, middle high,
east middle. The central carved stone plays the three selected tones together
and checks the harmony explicitly. No sequence or timing is involved. Wrong
chords leave every tone untouched and give a gentle hint.

Friendly mushroom spiders are the harp’s makers and clue authors. Dudunka’s
bell melody supplies the low voice, Marusya’s dance the high voice, and Syusya’s
forest memory the middle voice. Text invites the family to listen together;
NPC presence and AI positioning never gate the puzzle. Real nearby `vale_spider`
entities optionally receive private applause particles without AI/emote changes.
No naturally spawned inhabitants are required.

`ValeMusicProgress.java` adds the UUID-scoped
`Chapters/dudunka:silver_web_harp/Completed` flag to the existing SavedData key,
retaining the envelope, storage version, unknown fields and prior chapters.
Chapters 1–3 gate both tuning and playback. Attempts are transient and bound to
one saved anchor per UUID. Wrong playback preserves tones; a different harp
starts with middle tones. Leaving the area retains tuning, while logout, death,
spectator mode, dimension change and reload clear transient settings. Completion
persists. Replay allows free tuning and chords without further save mutations.

The entire native blueprint must be present. Normal glass-pane connection
properties are allowed to vary with neighbor updates; pane material and dryness
must still match. All other blueprint states match exactly. Missing starts,
changed blocks, waterlogged strings, damaged anchors and decorative cobwebs fail
closed without repair. Interactions never change shared blocks, terrain, entity
AI, Mystery progress, forced chunks or ambient music. No dependencies, registry
IDs, native worldgen geometry, album protocol or version changed.

Other files: `ValeHarpTests.java`, shared fixture dispatch in `ValeMusicTests.java`,
generated harp resolver assertions in `ValeAdventureTests.java`, matching
`en_us.json` / `ru_ru.json` strings, the five-chapter `MUSHROOM-VALE.md`, focused
scope descriptions in `DEVELOPMENT-WORKFLOW.md` / `tools/test.sh`, layout isolation
in `ValeSpiderTests.java` / `FamilyGatewayTests.java`, and this report.

## Automated coverage and commands

Ten new focused GameTests cover all 27 tone combinations, explicit center
playback, all three chapter prerequisites for tuning and checking, retained
incorrect settings, immediate retry, interleaved UUIDs, different native anchors,
compressed NBT reload, malformed and duplicate rows, unknown metadata,
idempotency and enjoyable retuning after completion. Native subscriber tests
cover private text/sound/particles, every missing blueprint block, changed and
waterlogged strings, harmless pane connections, decorative copies, offhand,
spectator, distance, wrong dimension and unloaded-position rejection. They also
compare the full landmark volume, forced chunks and Mystery data before/after,
and exercise optional real spiders and logout. The existing full native-locate
test additionally resolves the center and three strings of a naturally generated
harp, with saved native start serialization.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh build
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
git diff --check
```

Each GameTest wrapper run used a fresh isolated world. All final checks ran after
the last production and test-source edit. Only this evidence record changed
afterward. Final reports:

- Build / model and resource checks:
  `build/reports/dudunka-tests/20261012T222107Z-3788735/`.
- Focused music tests (**29/29**, including the ten new Chapter 4 tests):
  `build/reports/dudunka-tests/20261012T222201Z-3789676/`.
- Full tests (**302/302**, including naturally generated harp anchors and existing
  Mystery, family travel, worldgen and earlier music chapters):
  `build/reports/dudunka-tests/20261012T222329Z-3790599/`.
- `git diff --check`: **PASS**. JSON parsing and RU/EN harp key/format parity:
  **PASS**, 16 matching keys.

The final build includes all configured model/resource verifiers and the existing
ambient music scheduling check. These results establish automated validation;
manual gameplay and modpack acceptance remain intentionally deferred.

Validation history: the initial build/model checks and **29/29** focused tests
passed (`20261012T220807Z-3780993`, `20261012T220829Z-3781753`). The initial full
run (`20261012T220928Z-3782527`) passed **300/302**, including all music tests and
the naturally generated harp resolver assertions. It failed the existing
`peacefulEvenWhenSurprised` unchanged-block assertion and
`returnGatewayAndTwoPersonalBookmarksSurviveSerializationTogether` exact-return
assertion. Inspection found that the spider's seven-block-wide snapshot extended
outside the six-block-wide air template, while the gateway test used a layout-
dependent departure that could lie on a chunk edge (deliberately skipped by safe
return). Test fixtures were isolated: the spider now uses a restored dry stone/air
volume, and the gateway reuses the existing centered elevated fixture. Original
assertions and timeouts remain intact; production spider and travel code are
unchanged. The next build and focused run passed (`20261012T221508Z-3785051`,
`20261012T221541Z-3785912`). The next full run
(`20261012T221638Z-3786670`) passed **301/302**: both corrected regression fixtures
passed, but the new optional-spider acknowledgement assertion intermittently
failed. Its synthetic Vale observer was not registered as a real level player,
and the test did not wait for the newly added spider to be visible in entity
sections. The fixture now registers its observer and explicitly waits for the
real spider to become queryable before exercising the unchanged acknowledgement
assertion, with batch cleanup and the original timeout. Final checks are repeated
after this last test-only correction; the final build, **29/29** focused run and
**302/302** full run above all passed. Earlier failures remain recorded here and
in their original logs; assertions were not weakened and timeouts were not raised.

## Manual acceptance — NOT RUN

Intentionally defer runClient and BMC4 v55.5 acceptance until the entire music
chain is implemented. Then use a backed-up test save, complete Chapters 1–3,
and locate `dudunka:silver_web_harp` in new Memory Forest chunks. Check RU/EN
instructions, the visibility and reach of all three strings and central stone,
countable sparkles, simultaneous chord audio alongside existing ambient music,
and distinct west/middle/east labels from both sides of the harp. Try every
wrong tone, adjust strings in arbitrary order, leave and return, complete without
spiders or family present, and replay with the three family companions nearby.
Review optional real-spider applause. Interleave two real clients, save/reload,
and confirm independent tuning and completion. Damage a string or frame in a
test copy and verify inert behavior without repair. Automated results do not
establish graphical, audible, real multiplayer, old-save or full modpack acceptance.
