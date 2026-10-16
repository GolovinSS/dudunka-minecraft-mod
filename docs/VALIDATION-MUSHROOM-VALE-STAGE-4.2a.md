# Stage 4.2a — Where Did the First Note Go?

Target: Minecraft 1.20.1 / Forge 47.4.13 / Java 17 / BMC4 v55.5.
Version remains **0.27.1-alpha**; album protocol remains **10**.
Local uncommitted Stage 4.1 implementation was inspected and retained.
User explicitly requested build, model checks, focused tests and full GameTests.

| Status | Result |
| --- | --- |
| Implementation | First music chapter implemented; four later puzzles deferred |
| Build/model/resource verification | **PASS**, `20261012T201444Z-3731917` |
| Focused GameTests | **PASS**, **7/7**, `20261012T201504Z-3732806` |
| Full GameTests | **PASS**, **280/280**, `20261012T201602Z-3733629` |
| Manual runClient / BMC4 / real multiplayer / restart | **NOT RUN** |
| Publication / commit / push | **NOT DONE**, not authorized |

## Design and changed files

- `ValeMusicProgress.java`: independent UUID-keyed, additive/version-tolerant
  SavedData; transient landmark-specific attempts; idempotent completion.
- `ValeMusicQuest.java`: loaded native-start/blueprint resolution, right-click
  stems/caps/lights, discoverable crystal/root clue, private vanilla harp notes
  and note particles, RU/EN feedback in Dudunka/Pufik’s narrative voices.
- `ValeMusicTests.java`, `data/dudunka_music/structures/empty.nbt`: seven focused
  GameTests using pure sequence checks and restored native structure fixtures.
- RU/EN language resources: six matching feedback messages per language.
- `build.gradle`, `tools/test.sh`: include music namespace in full validation;
  `DUDUNKA_TEST_MUSIC=1` selects only this stage in a fresh world.
- `MUSHROOM-VALE.md`, `DEVELOPMENT-WORKFLOW.md`, this report: five music chapters,
  implementation contract, focused command and acceptance procedure.

No Stage 4.1 structure generation files, companion/Pufik AI, family travel,
Mystery/album/network code, registry IDs or dependency versions are changed by
Stage 4.2a. No terrain/light block mutations occur in production quest code.

## Automated evidence

- Build/check plus explicit model/Pufik verification passed on JDK 17. This
  includes all nine family age models, Pufik, firefly, Vale music scheduling,
  spider, Wise Fly Agaric and Fluffy Jumper verifiers.
- Focused fresh-world run: **All 7 required tests passed**.
- Full fresh-world run: **All 280 required tests passed**, including Stage 4.1
  native locate/generation, Stage 4.0 family travel and the new music tests.
  Report: `build/reports/dudunka-tests/20261012T201602Z-3733629/summary.txt`.
- Static checks: **PASS**, `git diff --check`, wrapper shell syntax and six
  matching RU/EN quest-message keys.
- Initial build `20261012T201300Z-3730777` failed on the test fixture’s missing
  `PacketFlow` import. The import was fixed before the passing build above.
- Reports/logs: `build/reports/dudunka-tests/<run-id>/`; isolated test worlds:
  `run/dudunka-test-<run-id>/`. No production save was used.

## Coverage

1. All 27 three-note combinations; only 0–1–2 completes; wrong order retries immediately.
2. Independent simultaneous player attempts/completion; no cross-landmark splicing.
3. Compressed NBT round-trip, UUID isolation, transient attempt reset, idempotent
   repeated completion without dirtying data.
4. Unknown chapter/root/owner fields, future versions, malformed completion type,
   unrecognized rows and duplicate owner monotonic completion.
5. Actual right-click subscriber on native restored Stage 4.1 stems, without
   Pufik/family entities; private packets, shared blocks/Mystery/tickets unchanged.
6. Crystal’s three ordered pitches, transient logout reset, persisted completion.
7. Decorative replicas/missing starts, changed blocks/root, offhand, spectator,
   other dimensions and unloaded chunks cannot trigger the quest.

The native fixtures use the existing Stage 4.1 geometry and its save loader;
manual naturally generated landmark acceptance remains distinct. The Stage 4.1
full-suite locate test continues to verify actual generated native starts.

## Commands

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh build
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
git diff --check
```

## Manual runClient acceptance (NOT RUN)

1. Start `runClient` in a backed-up test world. Enter the Vale through the
   existing family travel route; use a previously located Stage 4.1 bells site,
   or `/execute in dudunka:mushroom_vale run locate structure dudunka:music_bells`.
   Existing chunks are not retrofitted. Reach the actual three-bell clearing.
2. Approach within 12 blocks. Check the discovery text in English and Russian.
   Right-click the amethyst crystal or central carved stone: hear low/middle/high
   and see small note particles above turquoise/violet/golden bells.
3. Click the stems in wrong order: gentle retry, no lost items/health or delay.
   Then click turquoise → violet → golden. Check chapter completion and melody.
   Repeat, and replay the clue; ensure the site’s blocks remain unchanged.
4. Complete with no Pufik or family nearby. Repeat with naturally present Pufik
   and your family; their behavior, ownership, UUIDs, homes and travel stay normal.
5. Two players alternate notes at the same site. Each must finish independently;
   the other player must not receive private dialogue/notes/particles or completion.
6. Logout and restart the server after saving; completion remains. Partial tunes
   can restart freely. Revisit another bells site; completion stays idempotent.
   Confirm the existing album still shows its original five Mystery chapters.
7. Right-click matching decorative mushrooms elsewhere and the four other
   landmarks: no new puzzle. Verify audio/particles in full BMC4 v55.5 separately.

## Known limits

Destroyed or replaced puzzle blocks are never auto-repaired. Breaking the central
root disables that site; another intact bells landmark can complete the chapter.
Audio and particles depend on client settings/resource packs; text gives the same
sequence. No new album integration, later chapter puzzles or Stage 4.2b work.
