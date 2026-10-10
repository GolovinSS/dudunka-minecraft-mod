# Development guidelines

- Target Minecraft 1.20.1, Forge 47.4.13, and Java 17; the target modpack is Better MC BMC4 v55.5.
- Git and source code are the source of truth. Read the relevant documentation before changing a subsystem.
- Preserve registry IDs, UUIDs, the `dudunka` namespace, save compatibility, and the network protocol unless explicitly authorized to change them.
- Preserve agreed gameplay mechanics and each character's personality.
- Avoid unnecessary dependencies, especially GeckoLib and mixins. Follow existing code patterns and architecture.
- For code changes, run appropriate Gradle checks and report the results. Distinguish automated test results from manual Minecraft/BMC4 acceptance.
- Never commit, push, or create a PR without explicit approval.
