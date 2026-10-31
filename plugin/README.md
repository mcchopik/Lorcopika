# Lorcopika — Paper-плагин

Серверная реализация лора Чопика для Paper 26.2. Ветка `lore-plugin`; клиентский мод и Fabric-серверный мод лежат в отдельных ветках.

## Сборка

Нужен JDK 25.

```bash
./gradlew build
```

Готовый файл: `build/libs/lorcopika-plugin-1.0.0.jar`.

## Установка

1. Поставьте Paper 26.2 (проверено на build 135).
2. Положите jar в `plugins/`.
3. Перезапустите сервер.

При первом запуске создаются:

- `plugins/Lorcopika/config.yml` — фичи, параметры, персональные настройки;
- `plugins/Lorcopika/bindings.yml` — привязки UUID к профилям;
- `plugins/Lorcopika/characters/chopik.yml` — профиль по умолчанию.

Игрок `McChopik` при первом входе автоматически привязывается к профилю `chopik`; реальный UUID сохраняется. Остальных привязывает администратор.

## Команды

| Команда | Право | Назначение |
| --- | --- | --- |
| `/lore` или `/lore status` | `lore.status` | профиль и состояние |
| `/lore memory [игрок]` | `lore.status` / `lore.admin` | случайное воспоминание |
| `/lore trigger sound [игрок] [звук]` | `lore.status` / `lore.admin` | проиграть знакомый звук |
| `/lore feature [игрок] <фича> <on\|off>` | `lore.status` / `lore.admin` | включить или выключить фичу |
| `/lore set [игрок] <параметр> <значение>` | `lore.status` / `lore.admin` | изменить параметр |
| `/lore show [игрок]` | `lore.status` / `lore.admin` | все настройки |
| `/lore reset [игрок]` | `lore.status` / `lore.admin` | сбросить настройки |
| `/lore debug [игрок] [on\|off\|toggle]` | `lore.status` / `lore.admin` | отладочные сообщения |
| `/lore bind <игрок> <профиль>` | `lore.admin` | привязать профиль |
| `/lore unbind <игрок>` | `lore.admin` | убрать привязку |
| `/lore bindings` | `lore.admin` | список привязок |
| `/lore profile ...` | `lore.admin` | управление профилями |
| `/lore reload` | `lore.admin` | перечитать конфиг и профили |

`/lore profile` поддерживает `list`, `create`, `delete`, `info`, `name`, `description`, `memory` и `familiar`. Для `memory`/`familiar` доступны `add`, `remove` и `list`.

Привязка по нику работает и для офлайн-игрока, если его имя уже сохранено в `bindings.yml` (например, после входа на сервер).

## Права

- `lore.status` — по умолчанию у всех; свои команды.
- `lore.admin` — по умолчанию у операторов; чужие профили, привязки, reload.

## Механики

- `fragile_body` — максимальное здоровье из `fragile_body.max_health`.
- `warden_ignore` — Варден не выбирает игрока целью; `SONIC_BOOM` отменяется.
- `fire_weakness` — повышенный урон от огня и увеличенное время горения.
- `loud_sounds` — урон от взрывов, молнии, козьего рога и `SONIC_BOOM`.
- `random_darkness` — случайный эффект тьмы.
- `infected_zone` — повторный слабый урон при ударе или попадании projectile сверху.
- `nether_sickness` — тошнота и слабость после долгого пребывания в Аду.
- `sense` — частицы `SCULK_SOUL` у движущихся существ; при `sense.mode >= 1` вместо частиц свечение.
- `sculk_spread` — скалк расползается рядом с умершим существом.
- `memories` — случайные воспоминания в чате.
- `familiar_sound` — знакомый звук вызывает `...` в action bar и замедление.

## Параметры

| Параметр | По умолчанию |
| --- | --- |
| `fragile_body.max_health` | `14.0` |
| `fire_weakness.burn_multiplier` | `1.5` |
| `fire_weakness.damage_multiplier` | `1.25` |
| `loud_sounds.damage` | `4.0` |
| `loud_sounds.radius` | `16.0` |
| `loud_sounds.interval` | `40.0` |
| `random_darkness.min_interval` | `1200.0` |
| `random_darkness.max_interval` | `3600.0` |
| `random_darkness.duration` | `200.0` |
| `infected_zone.chance` | `0.5` |
| `infected_zone.secondary_multiplier` | `0.4` |
| `infected_zone.height_ratio` | `0.6` |
| `nether_sickness.start_seconds` | `60.0` |
| `nether_sickness.effect_interval` | `200.0` |
| `sense.radius` | `16.0` |
| `sense.mode` | `0.0` |
| `sculk_spread.radius` | `10.0` |
| `sculk_spread.max_blocks` | `12.0` |
| `memories.min_interval` | `2400.0` |
| `memories.max_interval` | `7200.0` |
| `familiar_sound.slow_duration` | `60.0` |
| `familiar_sound.slow_amplifier` | `1.0` |

Интервалы и длительности (`random_darkness.*`, `memories.*`, `loud_sounds.interval`, `familiar_sound.slow_duration`, `nether_sickness.effect_interval`) задаются в тиках; `nether_sickness.start_seconds` — в секундах. 20 тиков = 1 секунда.

## Ограничения

- Персональная подсветка сущности в Paper API недоступна; `sense` использует частицы, а режим свечения — `setGlowing`.
- В Paper API нет отдельного события `SculkShrieker`; звуки скалка обрабатываются через смерть Вардена и `SONIC_BOOM`.
- Визуалы правого глаза и заражённой руки есть только в клиентской ветке `lore-client`.
