# Проверки 0.3.1 alpha — 2026-10-03

Forge 47.4.13 / Minecraft 1.20.1 / Java 17. Цель: BMC4 v55.5.

- build и reobfJar: успешны.
- verifyModels: успешен.
- Forge GameTestServer: все 11 обязательных тестов прошли.
- Новый тест appleGoalToleratesTicksAfterConsumption запускает цель возле стека из двух яблок, съедает одно, затем вызывает еще три tick до canContinueToUse. Цель не падает, второе яблоко остается, продолжение отказывает. Проверен также tick после stop.
- Исправление общей FamilyBehaviorGoal одинаково в 0.2.1-alpha и 0.3.1-alpha.

Полный BMC4 и повторение с настоящим клиентом/Neruina еще требуют проверки. Предыдущий отчет: VALIDATION-0.3.0.md. В среде ограничена загрузка ассетов; серверный тест выполнялся без downloadAssets, для отдельной 0.2.1 использованы те же локальные метаданные Minecraft без повторного downloadMCMeta.
