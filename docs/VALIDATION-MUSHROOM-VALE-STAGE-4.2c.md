# Stage 4.2c — The Forest That Remembers

Target: Minecraft 1.20.1 / Forge 47.4.13 / Java 17 / Better MC BMC4 v55.5.
Version **0.27.1-alpha**, album protocol **10**. Existing local work was kept.

The exposed listening target correction is implemented; its builds, GameTests
and manual acceptance are **NOT RUN**. The PASS results below predate it; see
the correction section at the end of this report.

| Status | Result |
| --- | --- |
| Implementation | Chapter 3 implemented; Chapters 4–5 deferred |
| Focused music GameTests on final source | **PASS**, **19/19**, `20261012T215729Z-3776505` |
| Final full build and model/resource checks | **PASS**, `20261012T215711Z-3775762` |
| Full GameTests | **PASS**, **292/292**, `20261012T215219Z-3772934` |
| Manual runClient / BMC4 / real multiplayer / graphical review | **NOT RUN** |
| Commit / push / publication | **NOT DONE** |

## Implementation

Chapter 3 uses the existing `dudunka_vale_music` UUID-keyed SavedData ledger.
The additive `Chapters/dudunka:memory_stone/Completed` flag preserves the
existing storage key, version envelope, other chapters and unknown NBT fields.
Listening state is transient and private. Both Chapters 1 and 2 must be
complete before an interpretation can advance.

The quest binds to the already-loaded saved native `dudunka:memory_stone`
structure start, its stored anchor, and every block in the Stage 4.1 blueprint.
The central carved stone starts a short Wise Fly Agaric and Syusya story with
private chimes and particles. Three existing blueprint symbols offer distinct
interpretations: the single crest crystal, the paired side crystals, and the
small corner lights. The shared-light interpretation restores the Valley's
quiet memory voice. Wrong answers give a gentle hint and allow unlimited
retries; listening and completed replay are safe. Damaged or decorative sites
are inert and never repaired. No NPC is required, no block is changed, and no
chunk is loaded or forced by the resolver.

Changed files:

- `ValeMemoryQuest.java` and `ValeMusicProgress.java` — native site, private
  listening, choices, prerequisites and persistent completion.
- `ValeMemoryTests.java`; `ValeMusicTests.java` — focused chapter coverage and
  shared fixture dispatch.
- `en_us.json` and `ru_ru.json` — matching story, hint, prerequisite and replay
  feedback.
- `MUSHROOM-VALE.md`, `DEVELOPMENT-WORKFLOW.md`, `tools/test.sh`, and this report.

No structure registration, world-generation blueprint, album protocol, album
data, soundtrack, family travel, version or dependency changed for this stage.

## Automated evidence

The final focused music namespace run passed all **19 required tests**, including
the six Chapter 3 tests. Coverage includes explicit listening, Chapter 1 and 2
prerequisites, correct and incorrect interpretation, unlimited retry, anchor
and per-player isolation, compressed NBT persistence, idempotent replay,
private scene feedback, missing native starts, damaged pieces, changed symbol
blocks, offhand/spectator rejection and unloaded-position safety.

The full `./tools/test.sh all` run passed the Gradle build, all configured model
and resource verifiers, and **all 292 required GameTests**. This includes the
previous music chapters, Stage 4.1 native landmarks, Stage 4.0 family travel,
the Mystery/album tests and the new Chapter 3 cases. Both test runs used fresh
isolated worlds; no production save was used. Full report:
`build/reports/dudunka-tests/20261012T215219Z-3772934/`. After a final
nonbehavioral cleanup removed an unused result value and translation, the
full build/model/resource checks and focused GameTests were rerun successfully
on the final source at `20261012T215711Z-3775762` and
`20261012T215729Z-3776505`. The full 292-test suite predates only that cleanup.

Validation history: the first focused attempt found a test-only visibility
issue in the resolver, then a test assertion that expected an early native
choice to be unhandled. The resolver was exposed as read-only, and the test was
corrected to assert that the interaction is handled gently while completion
remains blocked. The final focused and full runs above passed.

## Commands

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh all
git diff --check
```

## Manual acceptance — NOT RUN

In a backed-up test world, locate an actual generated site with
`/locate structure dudunka:memory_stone`. Complete Chapters 1 and 2, approach
the native Memory Stone, and choose when to listen. Review the three chimes,
firefly-like visual clues, story pacing and choice descriptions in English and
Russian. Try both wrong symbols, wait between interactions, retry, complete
with no naturally spawned inhabitants, replay after completion, and save/reload.
In multiplayer, interleave two players' listening and choices and confirm that
private feedback and completion stay separate. Repeat presentation and save
compatibility checks in Better MC BMC4 v55.5. Manual graphical, audio, restart
and full-modpack acceptance remain distinct from automated PASS.

## Exposed listening target correction — validation pending

Manual runClient inspection found that the original root interaction was hidden
inside the monument. Listening now uses the existing bottom-center crying
obsidian at `anchor + (0,1,0)`, just above the mossy base, with exposed north and
south faces. No blueprint or saved anchor changes are needed, including at
already generated sites. RU/EN welcome, listen-first and replay guidance names
this block. The three choice targets remain separate: crest 0, paired side
crystals 1, corner lights 2. The corner lights now supply the required correct
interpretation; previously this implementation incorrectly accepted symbol 1.
Existing saved completion remains valid; no progress fields or protocol change.

GameTests now cover ordinary player standing/reach and actual ray hits on both
exposed faces, listening through the interaction event, all seven choice blocks,
matching decorative blocks, hidden/neighboring blocks, blueprint damage and
saved completion/replay. Later-chapter prerequisite fixtures use symbol 2.
The historical PASS results above predate this correction. Builds and GameTests
for this correction: **NOT RUN**. Manual acceptance of the new target: **NOT RUN**.
Validate with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest`.
