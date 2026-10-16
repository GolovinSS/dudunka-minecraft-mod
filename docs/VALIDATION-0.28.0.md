# 0.28.0-alpha — release validation, 2026-10-17 (Europe/Moscow)

Target: Minecraft 1.20.1 / Forge 47.4.13 / Java 17 / Better MC BMC4 v55.5.
The user explicitly authorized this release's builds, tests, commits and normal
push to `origin/main`. Baseline: `eff277bdefc78fe285d70794bbb9040607ebed41`
(0.27.1-alpha). Fresh fetch initially confirmed local and remote main were equal.

## Separate statuses

- Implementation: accumulated music landmarks/quests, styled Bells integration,
  family teleport improvements, block-built Dance Clearing preview and automatic
  native export are included. The new Dance Clearing NBT is an explicitly placed
  preview; it has no production worldgen integration.
- Automated validation: **PASS**, including build/model/resource checks, native
  export/resource round-trips, all required GameTest scopes and dedicated
  teleport runtime validation. The corrected initial full-suite failure is
  retained in the evidence below.
- Manual acceptance: full BMC4, graphical/audio client behavior, real networked
  multiplayer, copied old-world restart and the exported NBT's manual placement
  are **NOT RUN** for this release. User-reported approval of the styled Bells
  preview and Java Dance Clearing preview does not validate production terrain
  placement or native NBT placement. Earlier real `/tp` acceptance failed; that
  incident remains unresolved until an actual manual retest succeeds.
- Publication: distribution prepared for a normal `origin/main` push after
  validation. The final task report records GitHub remote confirmation; a
  distribution commit alone does not establish publication.

## Commands and current release evidence

All commands used `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`
(OpenJDK 17.0.20.1); default offline dependency mode. Each GameTest phase used a
fresh isolated `run/dudunka-test-<run-id>/` directory. No player world was reused.
Reports/logs remain locally under `build/reports/dudunka-tests/<run-id>/` and
are intentionally excluded from Git. Dates in run IDs are UTC; execution was
on October 17 in Moscow.

| Command / phase | Result | Run ID |
| --- | --- | --- |
| `python3 tools/export_dance_structure.py` — native generation | **PASS 2/2** | `20261016T215500Z-1648203` |
| `./tools/test.sh build` — exporter build phase | **PASS** | `20261016T215600Z-1649557` |
| Exporter — packaged resource native lookup/placement | **PASS 1/1** | `20261016T215617Z-1650379` |
| `DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest` | **PASS 63/63** | `20261016T215732Z-1651335` |
| `DUDUNKA_TEST_JOURNEY=1 ./tools/test.sh gametest` | **PASS 31/31** | `20261016T215840Z-1652222` |
| `DUDUNKA_TEST_LANDMARKS=1 ./tools/test.sh gametest` | **PASS 8/8** | `20261016T220004Z-1653167` |
| `./tools/test.sh gametest` — initial full scope | **FAIL 354/355**, fixture readiness | `20261016T220250Z-1654732` |
| `./tools/test.sh build` — after fixture correction | **PASS** | `20261016T220842Z-1657589` |
| `DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest` — after fixture correction | **PASS 63/63** | `20261016T220906Z-1658487` |
| `./tools/test.sh gametest` — after fixture correction | **PASS 355/355**, exit 0 | `20261016T221018Z-1659410` |
| `DUDUNKA_TEST_ACCEPT_EULA=1 python3 tools/test-teleport-dedicated.py` | **PASS 13/13**, clean stop/save, exit 0 | `teleport-dedicated-20261016T221550Z-1663965` |

The build ran `build verifyModels verifyPufik`, including `reobfJar`, the
configured `check` verifiers for music, fantasy visuals, original and styled
Mushroom Bells, fireflies, spider, Wise Fly Agaric and Fluffy Jumper. All nine
family age-model bakes passed. This build phase from the documented exporter
is the required `./tools/test.sh build`; no redundant build was substituted.

Additional Python regressions: export installation safety **3/3**, original
Bells converter **5/5**, styled Bells **10/10**, dedicated teleport evidence
parser **11/11**. Tracked source/doc `git diff --check`: **PASS**. Complete
staged review reports only a final empty line in each of the two generated
`palette.mtl` assets; their original validated bytes are preserved. Staged
whitespace checking with only `blank-at-eof` disabled passes. Changed/new JSON resources parse;
credential-pattern and accidental-cache/log/world path scans found no issues.

The first sandboxed exporter invocation (`20261016T215400Z-3`) failed before
Minecraft started because the Gradle wrapper lock under `~/.gradle` was read-only.
The approved retry completed native generation and resource validation. This
was an execution-permission failure; no test assertion was bypassed or weakened.
Historical development reports retain earlier failures and NOT RUN handoffs;
the current release results above supersede those automated statuses only.

## Failure investigation and release correction

Initial full run `20261016T220250Z-1654732` finished **354/355**, with one readiness failure in
`danceAssistanceRespectsGatesVetoesReloadAndDeparture`: root chunk advertised
ENTITY_TICKING ticket status and loaded entity data, but
`ServerLevel.isPositionEntityTicking` remained false before the test deadline.
Focused music had passed the same assertions.

Pinned Minecraft 1.20.1 source confirms that
`ChunkMap.prepareEntityTickingChunk` requires a radius-two FULL neighbourhood;
the fixture loaded only the centre before requesting promotion. Full-suite
layout can put this fixture beyond terrain already prepared by earlier tests.
The release correction explicitly prepares those 25 bounded fixture chunks
before the accelerated readiness wait. Production firefly behavior, assertions,
180-tick deadlines and global view/simulation distances are unchanged. Post-fix
build and music **63/63**, then full **355/355**, passed. The previously failing
site at `(456,160,8)` now reported actual entity ticking and completed all of
its original assertions. The initial failure remains recorded above.

## Native resource and artifact verification

Resource: `src/main/resources/data/dudunka/structures/previews/jumper_dance_43a5a.nbt`.
Native ID: `dudunka:previews/jumper_dance_43a5a`.
Size: **25 × 6 × 25**, compressed file **2,501 bytes**.
SHA-256: `2437a9df4f61e1a62c3658cc7ab62c6768eee1eb8d72f44d8e814cc5ac02b4fd`.

Native runtime checks compared **477 authored blocks** and **3,273 untouched
cells**: all 3,750 volume cells, zero mismatches, zero entities, zero authored
air. Independently repeated generation produced identical NBT and compressed
bytes. A separate named chest retained its contents/name through native capture
and placement. The second fresh world resolved the resource through Minecraft's
StructureTemplateManager without a generated-world shadow and placed it exactly.
Export report: `build/reports/dance-structure-export/run-4r2qtc3d/validated.json`.

Production build: `build/libs/dudunka-0.28.0-alpha.jar`, reobfuscated by ForgeGradle.
Both `META-INF/mods.toml` and `META-INF/MANIFEST.MF` declare **0.28.0-alpha**.
The final rebuild after the test-fixture correction was inspected again.
Archive integrity, mod entry class, styled OBJ/layout resources and access
transformer entries were inspected. All 1,336 non-expanded source resource files
match their JAR entries byte-for-byte; the two expanded metadata files were
checked separately. No source Java files are packaged. Exactly
one NBT entry at `data/dudunka/structures/previews/jumper_dance_43a5a.nbt` matches
the validated resource bytes.

Distribution: `dist/dudunka-0.28.0-alpha.jar`, **9,525,911 bytes**.
SHA-256: `c6ef266ef6ba1c919941af5c819f01befb07952f49b2bd31b54a63481444a9ac`.
Build and distribution bytes/hashes are identical. All 30 previous published
JARs remain byte-identical. The four existing root/dist `SHA256SUMS[.txt]`
manifests now cover all **31** JARs in the existing lexical order/path convention,
including the two previously omitted 0.27.x artifacts. No GitHub Release or
CurseForge publication is part of this operation.

The dedicated runtime run warmed fresh cold regions asynchronously, completed
all 13 native 500-tick scenarios sequentially, and stopped/saved normally. It
covers registered vanilla command execution, source simulation loss, dimension
changes, console/command blocks, eligibility exclusions, unsafe arrivals,
Forge veto/insertion failure and superseding commands. Its packet adapter is
not a network login/Forge handshake or a graphical/BMC4 client. This run uses
the developer's existing accepted EULA for the same disposable-server workflow.

## Compatibility and remaining acceptance

Album protocol **10** is preserved. The optional `dudunka:vale_music_hints`
channel is protocol **1**, as documented in MUSHROOM-VALE-AUDIO.md. Existing
registry IDs, UUIDs and saved visual revisions remain compatible. Update clients
and server together. Quest progress is per player; the new preview template
contains block geometry, not native structure starts or quest bindings.

Manual follow-up: hear all five chapters and soundtrack fades with actual audio
settings; inspect new production Bells in natural terrain; test vanilla command
accompaniment with real clients and full BMC4; reload copied saves; explicitly
place the native Dance Clearing template in a disposable Creative world. Existing
production `jumper_dance` continues to use its prior Java generator. Automated
PASS does not establish any of these manual acceptance results.
