# Целевая совместимость

Источник: официальный файл CurseForge 7443284:
https://www.curseforge.com/minecraft/modpacks/better-mc-forge-bmc4/files/7443284

Распакованы manifest.json и modlist.html именно Better MC [FORGE] 1.20.1 v55.5.zip.

- name: Better MC [FORGE] BMC4
- version: v55.5
- minecraft.version: 1.20.1
- minecraft.modLoaders[0].id: forge-47.4.13
- 393 записи модов в манифесте.
- SHA-256 исходного manifest.json: 7ddd1d97422368c85923cea3e698b72c69c987dd1398cf9b40b42b11de4db2bf.

В списке есть GeckoLib, Oculus, ModernFix, Alex's Mobs и несколько дополнений Sodium/Embeddium. Их присутствие не означает, что интеграция протестирована. Мод пока не вызывает их API.

Сборка проекта использует JDK 17, Gradle 8.8, ForgeGradle 6.0.54 и Forge 47.4.13; official mappings 1.20.1. Разрешенный Minecraft строго 1.20.1; Forge [47.4.13,48). Более поздний Forge этой ветки допустим метаданными, но не является целевым тестовым окружением. NeoForge и Fabric не поддерживаются.

Почему такой подход: отдельные entity/block/item IDs; клиентские классы изолированы Dist.CLIENT; логика на сервере; добавочный GLM лут; никаких mixin и глобальных переборов мира. Стадии и режим передаются через SynchedEntityData; альбом использует отдельный SimpleChannel с протоколом 7, авторизованными серверными командами и приватными снимками. Остальные реакции используют стандартные события Minecraft.

Мод не изменяет конфигурацию Better MC и не требует обновления профиля. Полная проверка совместимости возможна только после запуска клиента и сервера именно этой сборки. Итоговый чек-лист в TESTING.md.
