# Проверка 0.26.0-alpha — 2026-10-05

Дополнение от 2026-10-10: [Mushroom Vale, этап 1](VALIDATION-MUSHROOM-VALE-STAGE-1.md) —
реализация прототипа, итоговые 150/150 GameTests, замечания о нестабильных прежних
тестах и еще не выполненная ручная приемка. Ниже сохранен отчет 0.26.0.

Дополнение от 2026-10-10: [Mushroom Vale, этап 2.1](VALIDATION-MUSHROOM-VALE-STAGE-2.1.md) —
естественный рельеф, расширяемая конфигурация worldgen и ограничения старых миров.
Ручная визуальная приемка и BMC4 проверяются отдельно.

Дополнение от 2026-10-10: [Mushroom Vale, этап 2.2](VALIDATION-MUSHROOM-VALE-STAGE-2.2.md) —
три отдельных биома, распределение по нескольким seed и проверка новых чанков.
Ручная визуальная приемка и полный BMC4 остаются отдельными проверками.

Дополнение от 2026-10-10: пользователь подтвердил успешные тесты и визуальную
приемку локальных этапов 2.2 и 2.3; прежние отчеты сохранены как история.
[Mushroom Vale, этап 2.4](VALIDATION-MUSHROOM-VALE-STAGE-2.4.md) — уютный декор,
редкие экстерьеры домиков Пуфика и Мудрого Мухомора. Ручная приемка новых
построек, плотности генерации, маршрутов Сюси и BMC4 проверяется отдельно.

Дополнение от 2026-10-10: [Mushroom Vale, этап 2.4a](VALIDATION-MUSHROOM-VALE-STAGE-2.4a.md) —
оригинальные модели/текстуры декора, грибные домики и ограниченная команда
предпросмотра. Результаты автоматических проверок и нерешенный прежний тест
приветствий описаны отдельно от еще не выполненной визуальной приемки/BMC4.

## Подтверждено

Дополнение от 2026-10-10: [Mushroom Vale, этап 2.3](VALIDATION-MUSHROOM-VALE-STAGE-2.3.md) —
фирменная растительность, светящиеся грибы и редкие грибные ориентиры.
Ручная визуальная приемка, навигация Сюси и полный BMC4 проверяются отдельно.

- Minecraft 1.20.1 / Forge 47.4.13 / JDK 17.0.20. build, verifyModels и полный GameTest успешны: **142/142 обязательных теста**, exit 0, phase26-test3.log. 134 прежних и 8 новых сценариев. Для headless-прогона использовано `build verifyModels runGameTestServer -x downloadAssets`.
- Воспоминания: приватность владельцев, сохранение первых дат, предел 18 событий, исключение самознакомства и отрицательных дат. Настоящие тики роста записывают подростка и взрослого; загрузка NBT и восстановление сущности не выдумывают вылупление и не дублируют историю.
- Прежний тест реального вылупления Сюси дополнен проверкой записи после успешного появления. Встреча за стеной или с чужим владельцем не создает знакомства; видимая встреча своей семьи записывает две личные подписи. Подтвержденная общая сценка оставляет две личные записи без бонуса дружбы или повторов.
- Сводка семьи: актуальное следование/ожидание; настоящее домашнее присутствие отдельно от режима вдали от дома; UNKNOWN для старого индекса; сохранение последнего режима. Настоящий тест выгрузки проверяет последний режим и доступность поиска без заявления о живом наблюдении.
- Сюся в переноске: восстановление отключено при действующей переноске в инвентаре и доступно при ее отсутствии; просмотр сохраняет текущий билет. Старые проверки освобождения билетов, восстановления, replay и приватности сохранены.
- Новый codec передает времена воспоминаний и поля сводки; отклоняет дубликаты, отрицательные даты и превышение предела. Прежние поврежденные пакеты адаптированы к новому хвосту, сохранив проверки битов историй.
- Девять возрастных моделей и 1620 комбинаций поз/вариантов/усиков проходят verifyModels. Геометрия, PNG и Activity сохранены. Новый SavedData воспоминаний и необязательное поле Mode в индексе; персонажный NBT сохранен. **Протокол альбома 9: обновлять сервер и все клиенты вместе.**
- JSON ресурсов корректен, ru/en содержат одинаковые наборы ключей. Встроенная помощь сохраняет пять страниц. Упакованные локализации сверены с исходниками, JAR проверен на целостность и версию.

## Исправления тестовых условий

- В новом тесте режима хозяин размещен возле друзей до команды ожидания и открытия альбома.
- Надземный дом атмосферы теперь очищает оставшиеся живые сущности прошлых сцен: обычная очистка подземного GameTest-шаблона их не охватывает. Это устраняет влияние порядка тестовых партий на доступность отдельных мест.
- Прежняя проверка пешего движения Сюси после 100 тиков ждет достижения того же минимального расстояния в пределах прежнего тайм-аута 150. Верхняя граница движения и расстояние до хозяина сохранены; проверка не зависит от фазы первого запуска навигации. Производственная скорость и перенос не изменены.

## Еще требуется игровая приемка

- Полный Better MC BMC4 v55.5, настоящий клиент–сервер, GUI Scale, длинные имена и визуальная прокрутка новых вкладок здесь не проверены в графическом клиенте.
- Воспоминания первых событий учитываются по виду и владельцу, а не для каждого дополнительного персонажа того же вида. История старого мира не реконструируется; встречи и возрастные переходы после установки могут добавлять новые страницы.
- Сводка — снимок при открытии и ответе сервера; вне видимости показан последний известный режим. До 12 ближайших карточек и 32 записей всей семьи. Предварительная доступность возврата не гарантирует успех по устаревшему индексу; сервер повторно проверяет условия.

Механика и ручная приемка: [ALBUM-MEMORIES.md](ALBUM-MEMORIES.md). Предыдущая проверка: [0.25.0-alpha](VALIDATION-0.25.0.md).

Артефакт: `dudunka-0.26.0-alpha.jar`, SHA-256 `8ddb64254615a856de35a5da409684f1636ad973d59d2c711148d7e56b7110cb`. Сборка, модели и полный GameTest: phase26-test3.log, BUILD SUCCESSFUL, exit 0.


## Mushroom Vale Stage 2.4b — 2026-10-10

Complete cottage walls/doors, distinct rounded Pufik and timber library Wise
blueprints, and functional transient bench seating. Final `build verifyModels`
passes; final full suite **162/162 required tests PASS**, including **14/14 Vale**.
Preview fixture chunk-readiness race corrected; no remaining intermittent
failures in the final run. Minecraft/BMC4 visual and real-client multiplayer
acceptance remain pending. [Detailed results and preservation evidence](VALIDATION-MUSHROOM-VALE-STAGE-2.4b.md).

## Mushroom Vale Stage 2.5 — atmosphere, music and environmental polish

Stage 2.2–2.4b local work remains preserved and uncommitted. Stage 2.5 adds rare
voxel mushroom landmarks, reduced mushroom attempts, porous level-14 lantern
lighting, standalone bench support/preview improvements and native streamed
biome music. Build, resource/JAR, focused and full GameTest evidence plus manual
Minecraft/BMC4 status are in
[VALIDATION-MUSHROOM-VALE-STAGE-2.5.md](VALIDATION-MUSHROOM-VALE-STAGE-2.5.md).
Exact new-world client commands are in
[TESTING.md](TESTING.md#stage-25-magical-atmosphere-music-and-environmental-polish).


## Mushroom Vale Stage 2.5a — final environment fixes

Preserved the uncommitted Stage 2.2–2.5 tree, all IDs/save formats and album
protocol 9. Fixed a confirmed bed-edge arrival deadlock and isolated random
idle roaming in the live homecoming fixture without changing assertions or
its time budget. More ordinary/medium detailed mushrooms and natural benches;
visible lantern/bulb block light 15; automatic native-slot music in Creative
and Survival with dimension exit cleanup.

Final focused **16/16**, including six live greeting repetitions; **build**,
**verifyModels** and **verifyValeMusic PASS**; one final full suite **166/166**.
Real generation census: **2304 FULL chunks**, seeds **0/42/-71021**, all intended
medium colors in every biome sample, **91 standalone bench groups** and only
**21 large landmarks**. All **363/363** packaged resources match source.
Minecraft/BMC4 graphical, audible and old-save acceptance remains manual.

[Root cause, exact distributions, intermediate failures and final evidence](VALIDATION-MUSHROOM-VALE-STAGE-2.5a.md).
[Developer commands and acceptance](TESTING.md#stage-25a-final-environment-fixes-and-homecoming-regression).

## Mushroom Vale Stage 3.1 — Pufik

Independent Pufik entity, detailed model and texture, gentle behavior and
forest-only natural spawning. [Stage 3.1 validation report](VALIDATION-MUSHROOM-VALE-STAGE-3.1.md)
records automated results and pending manual Minecraft/BMC4 acceptance.

## Mushroom Vale Stage 3.2a — mixed-color fireflies

Independent ambient fireflies, three original color atlases, emissive rendering,
bounded safe server flight and Vale-only night spawning. Build/model/resource
checks PASS; focused **4/4**, final full suite **173/173** required tests PASS.
Results and pending Minecraft/BMC4 acceptance are recorded in
[Stage 3.2a validation](VALIDATION-MUSHROOM-VALE-STAGE-3.2A.md).

## Mushroom Vale Stage 3.2c — sustained flight and natural visibility

Fixed floor-intersecting takeoff checks and vanilla's clearing of tiny steering
velocities. Safe terrain-relative hovering, local population turnover and
same-tick pack coordination retain the approved visuals, palettes, requested
groups of 3–5, spawn weights, ambient cap and album protocol 9.

Build, verifyModels and verifyValeFirefly PASS; focused **7/7**, generated-world
population checks **9/9** across seeds **0/42/2026** and all three Vale biomes;
one final complete suite **179/179**, exit 0. Comparable seed-0/42 routes improved
mean visible fireflies **4.86 → 9.16**; final nearby population peaks **17–19**,
mean visible groups **2.78** across nine routes. These are server visibility
proxies, with graphical Minecraft/BMC4 acceptance still pending. Historical
companion intermittency did not occur in this final suite and remains documented
separately. Existing uncommitted Stage 3.2–3.2b work is preserved.

[Root causes, exact measurements and validation logs](VALIDATION-MUSHROOM-VALE-STAGE-3.2C.md).
[Natural-world client acceptance commands](TESTING.md#stage-32c-natural-flight-and-density-acceptance).
