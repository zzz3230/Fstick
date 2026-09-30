# Fstick

[English](README.md) · **Русский**

Fstick — мессенджер на [Matrix](https://matrix.org), в котором любой чат можно расширить плагинами. Плагин — это маленькое приложение внутри чата: голосование, игра, доска задач. Его видят все участники, и все работают с одними и теми же данными. Разработчики публикуют плагины в маркетплейсе, а админы чатов устанавливают их в конкретную комнату.

## Чем Fstick отличается

**Плагин — это две небольшие программы.**
- **Клиентская часть** описывает интерфейс на компактном декларативном JavaScript DSL: `Column`, `Row`, `Text`, `Button`, `Input`, `IfBlock`, привязки к состоянию. Не нужно учить фреймворк и собирать проект — приложение само отрисовывает UI.
- **Серверная часть** пишется на Lua: объявляет типизированную схему состояния и регистрирует команды, которые вызывает интерфейс.

```lua
SetStateSchema({ counter = Types.int })

RegisterCommand({
    name = "counter.increment",
    in_schema = { by = Types.int },
    out_schema = { value = Types.int },
    handler = function(ctx, payload)
        local v = ctx.state.counter:get() + payload.by
        ctx.state.counter:set(v)
        return { value = v }
    end,
})
```

```js
const increment = backend.makeCommand('counter.increment');

DSL_CONTEXT.exports.__app = Column({ gap: 8 }, [
    Text(state.counter, { font_size: 'title' }),
    Button('+1', () => increment({ by: 1 }).before(() => state.counter.set(state.counter.value + 1))),
]);
```

**Состояние синхронизируется само.**
- У каждого установленного плагина одно общее состояние на чат, источник истины — сервер. После каждой команды новое состояние рассылается всем участникам через поток синхронизации Matrix.
- Интерфейс обновляется оптимистично и откатывается, если сервер отклонил изменение.
- Поля с пометкой `user_scoped` приватные: каждый участник получает только свою часть.

**Исполнение в песочнице.** Серверный код никогда не выполняется на клиенте и не получает доступа к хосту. Сервис рантайма запускает его в изолированной песочнице:
- доступен только API плагина;
- нет доступа к файловой системе и сети;
- действуют лимиты ресурсов.

Сломанный или вредоносный плагин не может навредить платформе и другим плагинам. Рантайм сейчас переписывается, поэтому контрактом считается API плагина выше, а не текущий движок.

**Разработка прямо в чате.**
- Во встроенном редакторе оба файла открыты рядом, есть живая консоль отладки.
- Hot reload сохраняет состояние.
- Отлаживаешь в настоящем чате с настоящими людьми.
- Готовый релиз публикуется и проходит модерацию, прежде чем попасть в маркетплейс.

## Архитектура

```
Веб-клиент на основе Element
        │  Matrix client API + /fstick/api/v1/*  (Matrix access token)
        ▼
Dendrite (Matrix homeserver, доработанный)
        │  аутентифицирует пользователя, проксирует API плагинов
        ▼
Gateway ──► Registry      каталог плагинов, ветки, блобы кода, модерация     (PostgreSQL, MinIO)
        ├─► Installation  какой плагин и ветка установлены в каком чате      (PostgreSQL, Redis)
        └─► Runtime       выполняет команды плагинов, хранит их состояние    (Redis)

Integration ◄── сервисы     единственный сервис, который обращается к Matrix:
        │                   маппинг пользователей, участники чатов, сообщения, доставка событий
        ▼
Dendrite ──► /sync ──► клиент     (обновления состояния, вывод консоли, уведомления)
```

**Почему Matrix и Dendrite.** Matrix из коробки даёт комнаты, аккаунты, федерацию и надёжный канал синхронизации в реальном времени. Мы используем [Dendrite](https://github.com/element-hq/dendrite) — homeserver на Go — и дополняем его небольшим API `/fstick`:
- он проверяет Matrix-токен пользователя у запросов к API плагинов и передаёт их в Gateway;
- доставляет собственные события `fstick_events` (обновления состояния, строки консоли, уведомления) в обычном ответе `/sync`.

Клиент — форк [Element Web](https://github.com/element-hq/element-web) с маркетплейсом плагинов, слотами плагинов в шапке комнаты и редактором плагинов.

**Сервисы**

| Сервис | За что отвечает |
|---|---|
| **Gateway** | Единая точка входа за Dendrite. Переводит Matrix id во внутренние id пользователей, маршрутизирует запросы, добавляет отображаемые id в ответы |
| **Registry** | Маркетплейс: плагины, ветки debug/release, хранилище кода по хэшу содержимого, публикация и модерация |
| **Installation** | Установка ветки плагина в чат; debug-ветку может поставить только автор |
| **Runtime** | Выполняет команды плагинов в песочнице, хранит состояние по чатам, hot reload |
| **Integration** | Anti-corruption layer для Matrix: таблица идентичностей, участники чатов, сообщения, доставка событий |

Внутри бэкенда пользователь — это внутренний UUID. Matrix id (`@user:server`) живут только на границах системы: в Dendrite, Integration и Gateway.

## Технологии

- **Бэкенд:** Java 17/21, Spring Boot 4, Gradle / Maven
- **Хранилища:** PostgreSQL, Redis, MinIO (S3)
- **Matrix:** Dendrite (Go), Matrix client-server API
- **Плагины:** Lua на сервере, JavaScript DSL на клиенте
- **Фронтенд:** Element Web (TypeScript, React), pnpm + Nx
- **Инфраструктура:** Docker Compose

## Структура репозитория

```
fstickbackend/
  gateway-service/ registry-service/ installation-service/
  runtime-service/ integration-service/
  dendrite/              Matrix homeserver с расширением /fstick
  docker-compose.yml     все бэкенд-сервисы и базы данных
fstickfrontend/element-web/   веб-клиент
documentation/           проектные записки и инструкции по реализации
vote-plugin/ ttt-plugin/ примеры плагинов
```

## Локальный запуск

```bash
# Matrix homeserver
docker compose -f fstickbackend/dendrite/build/docker/docker-compose.yml up -d

# бэкенд-сервисы, базы данных, MinIO
cd fstickbackend && docker compose up --build

# веб-клиент
cd fstickfrontend/element-web && pnpm install && pnpm start
```

Схемы баз данных создаются init-скриптами при первом запуске. После изменения схем пересоздайте тома: `docker compose down -v`.

## Документация

- [Проект редактора плагинов](documentation/plugin-editor-integration.ru.html) ([EN](documentation/plugin-editor-integration.html)): контракты API, ветки, модерация, консоль
- [Инструкции по реализации](documentation/implementation/00-overview.md) (EN): соглашения и по файлу на каждый эпик
- [Спецификация сервисов](fstick_specification.md)
