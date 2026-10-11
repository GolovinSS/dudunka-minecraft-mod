# Development guidelines

- Target Minecraft 1.20.1, Forge 47.4.13, and Java 17; the target modpack is Better MC BMC4 v55.5.
- Git and source code are the source of truth. Read the relevant documentation before changing a subsystem.
- Preserve registry IDs, UUIDs, the `dudunka` namespace, save compatibility, and the network protocol unless explicitly authorized to change them.
- Preserve agreed gameplay mechanics and each character's personality.
- Avoid unnecessary dependencies, especially GeckoLib and mixins. Follow existing code patterns and architecture.
- Development/validation roles are defined in `docs/DEVELOPMENT-WORKFLOW.md`. Read and follow that document before implementation.
- Codex implements features and writes tests, but does NOT execute Gradle builds, GameTests, broad surveys or repeated diagnostic runs by default. These are run externally through `./tools/test.sh` by the developer and reviewed with ChatGPT; run them in Codex only when explicitly requested.
- Codex may run cheap static checks such as `git diff --check`, must provide validation commands and must clearly mark unexecuted tests as **NOT RUN**.
- Keep implementation, automated verification, manual Minecraft/BMC4 acceptance and publication as separate statuses; never claim success for unrun tests.
- Never commit, push, or create a PR without explicit approval.
