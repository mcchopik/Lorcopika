# Lorcopika Server

Серверный Fabric-мод для Minecraft 26.2. Добавляет лорные механики персонажей и админ-команды. Игроки заходят с ванильным клиентом.

## Сборка

Требования: JDK 25.

```bash
cd server-mod
./gradlew build
```

Готовый файл: `server-mod/build/libs/lorcopika-server-1.0.0.jar`.

## Установка

1. Установите Fabric Loader 0.19.5+ для Minecraft 26.2 на сервер.
2. Положите Fabric API 0.161.0+26.2 и `lorcopika-server-1.0.0.jar` в папку `mods`.
3. Перезапустите сервер.

## Конфиги

- `config/lorcopika/config.json` — механики, параметры и персональные настройки.
- `config/lorcopika/bindings.json` — привязки UUID к профилям.
- `config/lorcopika/characters/*.json` — профили персонажей.

По умолчанию игрок `McChopik` привязывается к профилю `chopik` при первом входе. Остальные игроки обычные.

## Команды

`/lore status` — свои особенности и воспоминания.

Админские команды требуют уровень оператора 2+:

- `/lore bind <ник> <профиль>`
- `/lore unbind <ник>`
- `/lore bindings`
- `/lore profile list|show <профиль>|reload`
- `/lore memory add|remove|list|clear <профиль> ...`
- `/lore trigger sound add|remove|list <профиль> ...`
- `/lore feature <механика> on|off [игрок]`
- `/lore set <параметр> <значение> [игрок]`
- `/lore show [игрок]`
- `/lore reload`
- `/lore reset [игрок]`
- `/lore debug on|off`

## Механики

`fragile_body`, `warden_ignore`, `fire_weakness`, `loud_sounds`, `random_darkness`, `infected_zone`, `nether_sickness`, `sense`, `sculk_spread`, `memories`, `familiar_sound`.

## Основные параметры

- `fragile_body.max_health` — максимум HP, по умолчанию 14.
- `fire_weakness.burn_multiplier` — длительность горения, по умолчанию 1.5.
- `fire_weakness.damage_multiplier` — множитель урона от огня, по умолчанию 1.25.
- `loud_sounds.damage|radius|interval` — урон, радиус и интервал громких звуков.
- `random_darkness.min_interval|max_interval|duration` — интервалы и длительность тьмы.
- `infected_zone.chance|secondary_multiplier|height_ratio` — шанс и сила повторного удара.
- `nether_sickness.start_seconds|effect_interval` — время до болезни и интервал эффектов.
- `sense.radius|mode` — радиус чувства и режим (0 частицы, 1 свечение).
- `sculk_spread.radius|max_blocks` — радиус и лимит расползания скалка.
- `memories.min_interval|max_interval` — интервалы воспоминаний.
- `familiar_sound.slow_duration|slow_amplifier` — замедление от знакомого звука.

## Ограничения

Громкие звуки и знакомые звуки перехватываются на уровне сервера. Варден не призывает отдельных существ, поэтому `warden_ignore` очищает его цель и блокирует урон `sonic_boom`.
