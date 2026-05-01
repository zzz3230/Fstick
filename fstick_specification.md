# Fstick — Описание проекта
## Назначение
Fstick — мессенджер с архитектурой микросервисов, основанный на протоколе Matrix. Ключевая концепция - расширяемость через плагины, которые публикуются разработчиками в центральном реестре, устанавливаются в конкретный чат (Matrix Room), и исполняются сервисом рантайма по командам из чата (интерфейса в чате). Плагины позволяют расширять функциональность чатов: добавлять интерактивные UI-компоненты, хранить общее состояние, обрабатывать пользовательские действия через серверную логику. Каждый плагин состоит из двух частей: клиентской (frontend, UI) и серверной (backend, бизнес-логика на Lua).

## Состав сервисов/компонентов

1. Client - клиентское приложение на основе Element
----
2. Gateway	service - Точка входа для всех внешних запросов в систему
3. Registry service - Реестр плагинов (каталог, версии, ассеты)
4. Runtime	serive - Исполнение команд плагинов
5. Installation service - Управление установками плагинов в чатах
6. Integration service - Anti-Corruption Layer для matrix dendrite
----
6. MinIO / S3	- Хранилище бинарных файлов плагинов
7. PostgreSQL - Реляционная БД
8. Matrix Dendrite - Matrix homeserver
----

## Детальное описание каждого сервиса и его API
### Registry service
Ответственность: Каталог (marketplace) плагинов — CRUD для плагинов, управление версиями, скриншотами, статусами, и кодом.

#### HTTP API
| Метод  | Путь                                        | Описание                                                                    |
|--------|---------------------------------------------|-----------------------------------------------------------------------------|
| GET    | /api/v1/plugins                             | Список плагинов с пагинацией, фильтрами (category, search, sort, order)     |
| POST   | /api/v1/plugins                             | Инициировать загрузку нового плагина → возвращает presigned PUT URLs для S3 |
| POST   | /api/v1/plugins/{pluginId}/commit           | Подтвердить загрузку плагина (сохранить метаданные)                         |
| GET    | /api/v1/plugins/{pluginId}                  | Получить полные данные плагина (с версиями и скриншотами)                   |
| PUT    | /api/v1/plugins/{pluginId}                  | Обновить метаданные плагина                                                 |
| DELETE | /api/v1/plugins/{pluginId}                  | Удалить плагин (soft delete — меняет статус)                                |
| PUT    | /api/v1/plugins/{pluginId}/status           | Изменить статус: ACTIVE, DELETED, HIDDEN, ARCHIVED                          |
| POST   | /api/v1/plugins/{pluginId}/versions         | Инициировать загрузку новой версии                                          |
| POST   | /api/v1/plugins/{pluginId}/versions/commit  | Подтвердить загрузку версии                                                 |
| POST   | /api/v1/plugins/{pluginId}/assets           | Обновить ассеты (скриншоты)                                                 |
| POST   | /api/v1/plugins/{pluginId}/assets/commit    | Подтвердить ассеты                                                          |
| DELETE | /api/v1/plugins/{pluginId}/assets/{assetId} | Удалить ассет из S3                                                         |
| GET    | /api/v1/plugins/{pluginId}/code/server      | Получить presigned GET URLs кода сервер-части плагина                       |
| GET    | /api/v1/plugins/{pluginId}/code/client      | Получить presigned GET URLs кода клиент-части плагина                       |

#### Database

База данных (PostgreSQL, registry service database.sql):

- categories, statuses — справочники
- plugins — основная таблица (UUID, name, description, status_id, category_id, author_id, s3_icon_key)
- tags, plugins_tags — many-to-many теги
- versions — версии плагина (version_number, changelog, s3_archive_key)
- screenshots — скриншоты (s3_screenshot_key)
- files — файлы версий (s3_file_key, version_id)

#### Formats
**Формат runtime-строки** (RuntimeParser.java): (sv|cl).<language>@<semver>, например sv.lua@1.0.0 — серверный Lua-плагин версии 1.0.0; cl.js@2.1.0 — клиентский JS.

**Формат type-строки** (TypeParser.java): (code|image)/<subtype>, где code/sv.lua@1.0.0 или image/png.

----
### Runtime service

Ответственность: Исполнение плагинных команд — принимает команду с параметрами, выполняет плагин, возвращает результат.

#### HTTP API
| Метод  | Путь                                        | Описание                                                                    |
|--------|---------------------------------------------|-----------------------------------------------------------------------------|
| POST    | /api/v1/command                             | Выполнить команду плагина     |


----
### Installation service
Ответственность: Управление фактом установки плагина в конкретный Matrix Room (чат).  Принимает запросы только от Core Gateway.

Аутентификация: Matrix User ID в заголовке X-User-Id (проставляет Gateway). Членство в чате проверяется через вызов к Integration Service.

#### HTTP API
| Метод  | Путь                             | Описание                                                           |
|--------|----------------------------------|--------------------------------------------------------------------|
| POST   | /installations?chat_id=...       | Установить плагин в чат (проверяет членство, дубли, совместимость) |
| GET    | /installations?chat_id=...       | Список установленных плагинов чата (пагинация)                     |
| GET    | /installations/{installation_id} | Детали конкретной установки                                        |
| PATCH  | /installations/{installation_id} | Обновить версию плагина в чате                                     |
| DELETE | /installations/{installation_id} | Удалить плагин из чата                                             |

----
### Integration service
...


----
## Архитектурные нюансы

### Плагины
Плагины в Fstick имеют две части кода — серверную (`sv.*`) и клиентскую (`cl.*`). Язык определяется строкой формата `sv.lua@1.0.0` или `cl.js@2.1.0`. Это предполагает, что Plugin Runtime умеет запускать код на разных языках.

### Привязка плагинов к чатам, а не к пользователям
Принципиальное архитектурное решение: установки плагинов привязаны к Matrix Room (chat_id), а не к пользователю. Один плагин может быть установлен только один раз в одном чате (UNIQUE (plugin_id, chat_id)).

### Двухфазная загрузка файлов
Загрузка плагина происходит в два шага:

Клиент запрашивает POST `/plugins` → получает список presigned PUT URLs для каждого файла.
Клиент загружает файлы напрямую в MinIO.
Клиент вызывает POST `/plugins/{id}/commit` → бэкенд сохраняет метаданные в БД.

### Версионирование плагинов (SemVer)
Версии плагинов — строго SemVer. В БД version_number денормализован (varchar) рядом с version_id (UUID) для удобства чтения.


## Инструкции по запуску

### Dendrite
Запускать `Fstick\fstickbackend\dendrite\build\docker\docker-compose.yml`


## Fstick Plugin System - Спецификация

### Runtime Service

Жизненный цикл выполнения команды:

1. Контроллер получает запрос, извлекает userId из заголовка
2. PluginRuntimeService проверяет, загружен ли LuaPluginEngine для данного pluginId (кэш в памяти)
3. Если нет — загружает исходный код плагина из Registry Service, инициализирует движок
4. Загружает состояние плагина из Redis по ключу state:<pluginId>:<chatId>
5. Устанавливает контекст выполнения (chatId, userId, состояние)
6. Вызывает ExecuteCommandHandler(commandName, payload) в Lua-среде
7. Сохраняет обновлённое состояние в Redis
8. Возвращает результат клиенту

### Backend API плагина (Lua)

#### Типы данных
| Тип                   | Описание                                    |
|-----------------------|---------------------------------------------|
| Types.int             | Целое число                                 |
| Types.float           | Дробное число                               |
| Types.string          | Строка                                      |
| Types.datetime        | Дата/время                                  |
| Types.user_id         | Идентификатор пользователя                  |
| Types.enum(values)    | Перечисление; поддерживает флаги (.flags()) |
| Types.list(inner)     | Список элементов одного типа                |
| Types.map(key, value) | Словарь                                     |
| Types.object(schema)  | Вложенный объект                            |

#### `SetStateSchema(schema)` — описание схемы состояния
Определяет структуру общего состояния плагина для данного чата. Состояние инициализируется значениями по умолчанию (0, "", пустые таблицы) и валидируется при каждом выполнении команды.

```
SetStateSchema({
    x = Types.int,
    votes = Types.map(Types.string, Types.int),
    history = Types.list(Types.object({
        user = Types.user_id,
        value = Types.int
    }))
})
```

#### RegisterCommand(args) — регистрация команды
```
RegisterCommand({
    name = "user.clicked",          -- имя команды (соответствует вызову на фронте)
    in_schema = {                   -- схема входящего payload
        user_id = Types.user_id,
        clicked_times = Types.int,
    },
    out_schema = {                  -- схема возвращаемого результата
        status = Types.string
    },
    handler = function(ctx, payload)
        -- ctx.state — типизированный доступ к состоянию
        local val = ctx.state.x:get()
        ctx.state.x:set(val + payload.clicked_times)
        return { status = "ok,counter=" .. val }
    end
})
```

#### Объект контекста ctx
| Поле      | Описание                                             |
|-----------|------------------------------------------------------|
| ctx.state | Обёрнутый объект состояния с типизированным доступом |

##### API состояния
Каждое поле состояния оборачивается в узел с методами:

Примитив/enum: .get() / .set(value)
Список: .get(index) → дочерний узел, .set(index, value)
Словарь: .get(key) → дочерний узел, .set(key, value)
Объект: автоматически разворачивается в набор именованных узлов


----

### Frontend API плагина (JavaScript DSL)
Клиентская часть плагина описывает интерфейс декларативно.

#### UI-компоненты
| Компонент                     | Описание                    |
|-------------------------------|-----------------------------|
| Column(props, children)       | Вертикальная колонка        |
| Row(props, children)          | Горизонтальная строка       |
| Text(content, props)          | Текст; поддерживает Binding |
| Input(props)                  | Поле ввода                  |
| Button(label, onClick, props) | Кнопка с обработчиком       |

##### Props:
- `gap`, `weight`, `self_align` — расположение
- `content_align: 'left' | 'center' | 'right'`
- `font_size: 'title' | 'small' | ...`
- `id` — идентификатор для ссылки в коде
- `placeholder`

##### Реактивное состояние state
Состояние автоматически синхронизируется с сервером. Доступ через state.<field>.

`Binding(source, transform)` — связывает значение состояния с UI:

```
Text(Binding(state.public.test, (val) => "Clicked " + val + " times"))
```

##### Вызов сервера backend.sendCommand
```
var track_id = backend.sendCommand('user.clicked', {
    user_id: User.id,
    clicked_times: _index.get()
})
```
Возвращает `track_id` — идентификатор для отслеживания ответа.