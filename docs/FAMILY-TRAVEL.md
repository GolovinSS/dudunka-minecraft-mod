# Безопасное догоняние — 0.15.0 alpha

Персонажи в режиме следования теперь рассматривают хозяина в пределах 64 блоков в том же измерении, вместо прежнего предела 24 блока. Сначала используется обычная навигация. Сюся сохраняет прежнюю скорость и бонус доверия: если она идет по нормальному пути, перемещения нет.

Во время активного следования раз в секунду проверяется продвижение. Если персонаж не может идти дальше и отстает минимум на 12 блоков, нужны шесть последовательных секунд неудачного следования. Тогда он может переместиться на безопасное свободное место в 2–3 блоках по горизонтали от хозяина, на его высоте либо на один блок выше/ниже. После успешного перемещения действует пауза 20 секунд. Прерванные задачи, выгрузка и время вне игры не добавляют пропущенные секунды.

Проверяются только загруженные позиции. Нужны безопасный твердый пол, свободное пространство для текущего возрастного размера, отсутствие жидкости и опасностей в ближайшем окружении. Огонь, оба костра, магма, кактусы, ягодные кусты, роза иссушения, рыхлый снег и порталы исключаются; листья не подходят как пол. Учитываются сообщаемые модами неблагоприятные типы блоков для навигации. Живые существа и транспорт не должны занимать место появления. Если места нет, персонаж остается на месте и позже проверяет снова.

Перемещение запрещено для команды «ждать», поводка, пассажира/перевозящего пассажиров персонажа, чужого игрока, другого измерения, спящего/наблюдающего/летящего хозяина и хозяина в воде/лаве. Хозяин должен стоять на земле. Самостоятельные интервалы Маруси сохраняются: догоняние происходит только когда она готова следовать.

Следование остается ниже срочных реакций, ожидания своей Сюси, поглаживания, костра, сундука и домашних сцен. Эти задачи не принуждают к догонянию. Возраст, доверие, дом, хозяин, UUID, режим и дружба не меняются; скорость/модели/текстуры сохранены. При перемещении очищаются движение и накопленное падение.

Настройка мира `familyCatchUp = true` в `dudunka-server.toml`; установите `false`, чтобы запретить перемещение. Обычное следование сохраняется. Чанки не загружаются принудительно, билетов нет.

Это локальное догоняние уже загруженного персонажа. Оно не переносит через измерения. Отдельный явный семейный переход Stage 4.0 описан ниже; возврат через альбом описан в FAMILY-RECOVERY.md. Сюсю по-прежнему можно перевезти переноской. В полном BMC4 нужны проверки длительного путешествия, лестниц, леса, воды, границ чанков и сторонних опасных блоков.

## Stage 4.0: explicit family journeys

`FamilyJourney` coordinates awakened-gateway entry, physical return and
`/dudunka return_family`. The ritual is unchanged. Nearby means within 8 blocks
of the gateway centre (command: player block centre). Select exactly one owned,
live, loaded, adult FOLLOW companion of each Kind; reject duplicate candidates,
noncanonical UUIDs, conflicting registry owner/Kind, carried state, leashes,
mounts/passengers, fire, fluids, unsafe ground and active portal poses. A pending
album recovery blocks group travel. Temporary Marusya follow pauses do not change
her permanent FOLLOW mode or prevent an explicitly requested journey.

Shift + right-click explicitly selects solo travel. With no owned candidates
nearby the gate retains implicit solo behavior; a partial/ineligible family
blocks ordinary clicks. `/dudunka return` and developer entry/return stay solo.
The family command requires all three even while sneaking.

The existing MushroomVale destination/bookmark code is shared with solo travel.
Explicit travel can normally load its single target chunk, as before; companion
planning never scans or loads adjacent chunks. A loaded `FULL` chunk is enough for
block inspection, but native companion transfer requires `ENTITY_TICKING`. On a
cold target, the family stays at the source while a bounded temporary region
ticket prepares the selected arrival chunk; the request times out after 200 ticks
or cancels if the player moves or changes dimension.
The ticket is removed on completion, cancellation or timeout. No ticket is saved
as a persistent forced chunk. All four separate boxes
must fit within that chunk, with loaded hazard halos, border, collision, solid
floor and native/mod-reported hazards checked. Companion placement searches at
most radius 4 and Y offsets 0, ±1, ±2 around the player landing. For each
companion column, placement checks the loaded `MOTION_BLOCKING_NO_LEAVES`
surface and a fixed five-height neighborhood, still within radius 4 and the same
arrival chunk. This handles uneven terrain without scanning or loading more chunks.
Non-colliding vegetation and leaves in the safety halo are allowed; tagged hazards,
fluids, unsafe floors, intersecting mod path hazards, collision, and occupied space
remain rejected. Candidate failures log one bounded summary per rejected member,
with reason counts and at most four position samples. No terrain edits, offline NBT
recreation or automatic cross-dimensional following.

Plan in server thread → revalidate → cancel transient goals/scenes/navigation →
transfer Dudunka, Marusya, Syusya using native Entity.teleportTo → verify UUID and
registry after each → revalidate all arrivals → transfer player last. Reentrant
journeys for the same owner are denied. Native Forge travel vetoes remain active
for forward and rollback transfers. Home/furniture assignments and permanent modes
are retained; normal transient cleanup/cooldowns are not byte-for-byte NBT invariants.

On a normal failure, inspect the three planned UUIDs and attempt rollback in reverse
order, at most once each, to their exact recorded source positions/rotations.
No retry loop and no forced rollback through a veto or unsafe/occupied source.
A player redirected by a listener gets one safe rollback attempt too. Successful
rollback yields DENIED; incomplete rollback yields ROLLBACK_FAILED, logs owner/UUID
and sends private recovery feedback, observing surviving live instances immediately
in FamilyRegistry for existing album recovery. Never spawn a replacement. The
previous bookmark is untouched on all failed group operations; success writes entry
bookmark or consumes return bookmark only after the entire move is confirmed.

No persistent transition journal is introduced. JVM death between native entity
transfers, player save and SavedData writes can leave split dimensions or stale
bookmarks/index. Vanilla dimension-transfer save ordering is not proven crash atomic;
use backups and existing live-entity recovery after restart. Registry observation is
marked dirty, not synchronously flushed. Failure feedback/logs cover live-server
rollback failure, not guaranteed delivery through a crash. Real restart and BMC4
acceptance remain separate from in-memory NBT round trips.
