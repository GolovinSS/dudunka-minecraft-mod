# Dudunka Family — development and validation workflow

## Roles and token economy

- **ChatGPT (design/review):** define staged requirements, inspect code and validation evidence, interpret logs, prepare narrow bug reports and acceptance criteria.
- **Codex (implementation):** inspect the local source, implement the agreed change, write focused tests, maintain relevant documentation, run cheap static checks (for example `git diff --check`), and hand off. **Do not run Gradle builds, GameTests, large world-generation surveys or repeated diagnostics by default.** Run these only if explicitly requested or when the user authorizes a narrowly scoped exception.
- **Developer's terminal / IntelliJ:** execute builds, resource and model verification, and GameTests independently of Codex. Share concise outcomes and relevant log excerpts back with ChatGPT.
- **Minecraft/BMC4:** the developer performs graphical, gameplay, multiplayer and saved-world acceptance. Automated PASS never means BMC4 visual or compatibility acceptance.

## Standard cycle

1. Agree on a small, reviewable stage and preserve current uncommitted work.
2. Codex writes production code, test coverage, updated docs, and exact external verification instructions. Codex states **NOT RUN** for checks it has not executed.
3. Developer runs `./tools/test.sh build`; review the report. If this fails, share the failure log to identify a focused correction.
4. Once build is green, developer runs `./tools/test.sh gametest` (or `./tools/test.sh all` for the complete pipeline). Each GameTest run uses a **fresh isolated world**.
5. ChatGPT analyzes failed assertions and logs; a narrow Codex prompt addresses confirmed causes. Do not weaken assertions or increase timeouts without evidence.
6. Perform manual in-game review when relevant. Only then approve the stage, commit, push or release. Codex never commits/pushes without explicit approval.

## Commands

From the repository root, with **JDK 17**:

```bash
./tools/test.sh build       # Gradle build/check and existing model verifiers
./tools/test.sh gametest    # Full mod GameTest suite in an unused world
./tools/test.sh all         # Build, then GameTests
DUDUNKA_TEST_LANDMARKS=1 ./tools/test.sh gametest # Stage 4.1 focused suite
DUDUNKA_TEST_MUSIC=1 ./tools/test.sh gametest # Stage 4.2a–e focused suite
```

`./tools/test.sh` defaults to `all` and uses `--offline`. If a dependency has not been cached, set `DUDUNKA_TEST_ONLINE=1` for a deliberate online run. `JAVA_HOME` may point to the JDK 17 installation.

The script retains reports under `build/reports/dudunka-tests/<run-id>/` (`summary.txt`, `build.log`, `gametest.log` and the generated Gradle init script). Fresh GameTest working directories are kept under `run/dudunka-test-<run-id>/` for investigation. They are ignored by Git; remove **only a specifically identified test directory** when no longer needed. The script does **not** delete, reset or reuse a player's existing world. Avoid launching `runClient` or other Gradle commands simultaneously with the validation wrapper.

Existing specialized survey and focused-test tools remain available when a stage needs them, but are **not** executed by the default wrapper. A passing exit code is not a substitute for checking whether the GameTest summary reports the expected total. Preserve logs of intermittent or unresolved failures honestly.

## Boundaries

- Git/source is the source of truth. Preserve registry IDs, UUIDs, world save compatibility, namespace `dudunka`, and the current album protocol unless explicitly authorized. Stage 3.8 uses protocol 10 for its extended private snapshot/locator request; published Stage 3.7 used 9.
- Preserve approved character designs and behavior, including manually accepted models and interactions.
- Prioritize the smallest maintainable solution. No new dependencies, mixins, broad web research or unrelated refactors without a reason.
- A completed implementation is not a verified implementation. Keep **implemented**, **automatically validated**, **manually accepted**, and **published** separate.
- Never commit, push or create a PR without explicit user approval.
