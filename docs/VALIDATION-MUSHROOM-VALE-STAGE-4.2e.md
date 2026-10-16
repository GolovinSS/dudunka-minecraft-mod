# Mushroom Vale Stage 4.2e — A Song for All Friends

Target: Minecraft **1.20.1**, Forge **47.4.13**, Java **17**, Better MC **BMC4 v55.5**.
Mod version remains **0.27.1-alpha**; album protocol remains **10**.

## Status

| Area | Status |
| --- | --- |
| Chapter 5 implementation / RU and EN narrative | Implemented |
| Build / model and resource checks after final source edits | **PASS** |
| Focused music GameTests | **40/40 PASS**, including 11 new finale tests |
| Full GameTest suite | **312/313 PASS; 1 FAIL** — existing live flower-walk test; unresolved |
| Manual runClient / BMC4 / real multiplayer / graphical and audible acceptance | **NOT RUN — intentionally deferred for this milestone** |
| Commit / push / publication | **NOT DONE** |

## Implementation

`ValeConcertQuest.java` implements an explicitly activated, private 440-tick
concert at the original central carved stone of a verified native
`dudunka:singing_mushroom`. Chapters 1–4 gate activation. Discovery gives an
invitation without beginning playback. The entire loaded blueprint is checked
at activation and every concert tick; damaged/absent pieces, decorative copies,
wrong dimensions, offhand, spectators and remote interactions fail safely.

The score introduces Dudunka and Pufik’s ascending bells at ticks 0/12/24,
Marusya’s paired six-step jumper rhythm at 80–140, Syusya’s shared-light memory
with Wise Fly Agaric/fireflies at 180/200/220, and the friendly spiders’
low/high/middle harp chord at 280. The combined reprise starts at 360, interleaves
voices at 396/408, and closes on a chord at 420. Tick 440 announces restoration
and saves completion. RU/EN text gives each family member and inhabitant a role
without requiring actual NPC presence or precise positioning. Gentle vanilla
harp/amethyst audio and private note/light particles leave ambient music alone.

Nearby real owned companions and friendly inhabitants optionally receive private
applause particles. This reads existing entities without changing AI, emotes,
owner UUIDs, homes, friendship or saved state. Effects are capped at 24 friends.
No entities are created and no terrain/biome blocks are written by the quest.
No new assets, dependencies, registry IDs or album UI are introduced.

Playback belongs to one UUID in transient server state. Repeated clicks do not
restart, stack or skip it. Leaving beyond 16 blocks, death, spectator mode,
logout, dimension change, reload or site damage cancels safely; an explicit
new click restarts freely. Completion and replay do not depend on unpredictable
wild spawns or companions’ pathfinding. Multiple players can play independently.

`ValeMusicProgress.java` adds
`Chapters/dudunka:singing_mushroom/Completed` to existing `dudunka_vale_music`
SavedData. Completion is gated and idempotent; full replay changes no saved
progress and gives no repeat rewards. Earlier flags, unknown envelope/owner/
chapter fields and duplicate-row monotonic completion survive loading/saving.

Other changes: 16 matching RU/EN strings, fixture dispatch/cleanup in
`ValeMusicTests`, real generated anchor resolution in `ValeAdventureTests`,
focused-scope descriptions, storyline and manual acceptance documentation.
All pre-existing uncommitted changes were retained.

## Automated coverage and commands

Eleven new `ValeConcertTests` cover all four prerequisites, explicit activation,
full-sequence completion timing, disk codec/reload, malformed/duplicate rows,
unknown metadata, replay with identical sound pitches and no save mutation,
interleaved UUIDs/private packets, repeated interaction, departure/logout/
dimension/spectator/death cancellation, every missing blueprint piece,
last-tick damage and repair/restart, invalid native starts, decorative anchors,
offhand, distance, wrong dimension/unloaded positions, unchanged full landmark
volume/entities/forced chunks/Mystery, and read-only applause on the real three
family types and all five inhabitant types. Tests create disposable fixtures;
production quest code never creates companions. Existing music tests provide
Chapters 1–4 progression regression; full tests include naturally generated
Singing Mushroom anchor resolution and earlier worldgen/travel/Mystery behavior.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh build
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./tools/test.sh gametest
git diff --check
```

Recorded evidence (reports retained locally):

- Final build/model/resource checks: **PASS**,
  `build/reports/dudunka-tests/20261012T224330Z-3798870/`.
- Focused music GameTests: **40/40 PASS**,
  `build/reports/dudunka-tests/20261012T224405Z-3799700/`.
- Full GameTests: **312/313 PASS, 1 FAIL**,
  `build/reports/dudunka-tests/20261012T224506Z-3800661/`.
  The server registered 313 tests and reported exactly one required failure:
  `liveflowerwalkstartsandreturnstofollowing`. All music tests and the native
  landmark-locate test (including Singing Mushroom anchor resolution) passed.
- `git diff --check`: **PASS**; localization JSON parsing and 16-key RU/EN parity:
  **PASS**.

Each GameTest run uses a fresh isolated world. No broad survey or runClient is
included. All final build and test runs above followed the final production/test
source edit; only documentation changed afterward. An earlier build also passed
(`20261012T224300Z-3798062`).

### Unresolved full-suite failure

The existing `FamilyTests.liveFlowerWalkStartsAndReturnsToFollowing` failed its
80-tick assertion that a stationary owner permits all three family members to
start a flower stop. The log reports `scene=null`, Dudunka `(552.5, -58, 2.5)`,
Marusya `(554.5, -58, 2.5)`, Syusya `(554.5, -58, 4.5)`, and owner/current initial
position `(553.5, -58, 2.5)`, with `rain=false`, `day=1080`, and no nearby monsters.
The cause is **not confirmed**. Source review inspected the existing fixture,
stationarity/scene throttling, safety and path checks. No family production code,
flower-walk assertions or timeouts were changed, and no blind rerun was used to
replace the failure. The original failing log/world is retained. This is an
unresolved regression risk; the full suite is **not green**, despite the passing
focused music suite. Resolve and repeat full validation before accepting the
complete regression baseline.

## Manual acceptance — NOT RUN

The five chapters are implemented for later manual review. Manual runClient,
real multiplayer, old-save restart and full BMC4 acceptance remain intentionally
deferred in this milestone, as requested. Use the complete-chain checklist in
[TESTING.md](TESTING.md#stages-42ae-complete-music-chain). Automated results cannot
establish musical balance, particle appearance, narrative readability in actual
play, real multiplayer privacy or compatibility with the entire modpack.
