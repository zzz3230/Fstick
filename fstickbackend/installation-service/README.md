# Plugin Installation Service

Микросервис управления установками плагинов в чатах (Matrix Rooms).
Является частью системы плагинов и взаимодействует с Registry Service и Integration Service.

***

## Содержание

- [Назначение](#назначение)
- [Архитектура и место в системе](#архитектура-и-место-в-системе)
- [Технологии](#технологии)
- [Запуск](#запуск)
- [Конфигурация](#конфигурация)
- [Логика работы](#логика-работы)
- [События Integration Service](#события-integration-service)
- [Коды ошибок](#коды-ошибок)
- [Ограничения](#ограничения)
- [База данных](#база-данных)

***

## Назначение

Сервис отвечает за:

- Установку плагинов в чаты (Matrix Rooms)
- Удаление плагинов из чатов
- Хранение записей об установленных плагинах
- Проверку совместимости версий при установке
- Двухшаговое подтверждение установки при наличии предупреждений
- Уведомление Integration Service о событиях установки/удаления

Сервис **не управляет самими плагинами** — это зона ответственности Registry Service.
Сервис **не активирует плагины в Dendrite** напрямую — это делает Integration Service по событию.

***

## Архитектура и место в системе

```
Gateway Service
      │
      ▼ X-User-Id header
Installation Service
      │
      ├──► Registry Service       GET /api/v1/plugins/{pluginId}
      │                           Проверка плагина, версии, актуальности
      │
      └──► Integration Service    GET /api/v1/chats/{chatId}/members/{userId}
                                  Проверка членства пользователя в чате
                                  POST /api/v1/events/push
                                  Уведомление о событиях установки/удаления
```

Все запросы поступают только через **Gateway Service**. Прямые вызовы от других сервисов не предусмотрены.

Matrix User ID пользователя передаётся в заголовке `X-User-Id` — Gateway проставляет его после аутентификации.

***

## Технологии

| Компонент | Технология |
|-----------|-----------|
| Язык | Java 21 |
| Фреймворк | Spring Boot |
| База данных | PostgreSQL 16 |
| Доступ к БД | Spring JDBC (JdbcTemplate) |
| Кэш / pending-токены | Redis 7 |
| HTTP-клиент | Spring RestClient |
| Сборка | Maven |

***

## Запуск

### Локально (только инфраструктура)

Поднять только PostgreSQL и Redis без самого сервиса:

```bash
docker compose up installation-db installation-redis -d
```

Запустить сервис через IDE или:

```bash
mvn spring-boot:run
```

### Полностью в Docker

```bash
docker compose up -d
```

Сервис будет доступен на `http://localhost:8081`.

### Переменные окружения

При запуске в Docker переменные передаются через `docker-compose.yml`. При локальном запуске используется `application.properties`.

| Переменная | Описание | По умолчанию |
|------------|----------|-------------|
| `SPRING_DATASOURCE_URL` | JDBC URL PostgreSQL | `jdbc:postgresql://localhost:5433/installation_db` |
| `SPRING_DATA_REDIS_HOST` | Хост Redis | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Порт Redis | `6379` |
| `REGISTRY_SERVICE_URL` | Base URL Registry Service | `http://localhost:8082` |
| `INTEGRATION_SERVICE_URL` | Base URL Integration Service | `http://localhost:8080` |
| `PENDING_INSTALL_TTL_MINUTES` | TTL токена подтверждения (минуты) | `10` |

***

## Логика работы

### Установка плагина

```
1. Проверить членство userId в chatId (Integration Service)
   └─ если не участник → 403 FORBIDDEN

2. Запросить плагин из Registry (GET /plugins/{pluginId})
   └─ если не найден или статус != ACTIVE → 404 PLUGIN_NOT_FOUND
   └─ если список versions пустой → 404 PLUGIN_NOT_FOUND

3. Проверить что versionId есть в списке versions плагина
   └─ если нет → 404 VERSION_NOT_FOUND

4. Проверить что плагин ещё не установлен в этом чате
   └─ если установлен → 409 ALREADY_INSTALLED

5. Проверить актуальность версии (versions[0] = последняя)
   └─ если не последняя → сохранить в Redis + вернуть confirmation_token + warnings
      установку НЕ делать, токен действует 10 минут

6. Сохранить запись в БД

7. Отправить событие PLUGIN_INSTALLED в Integration Service
```

### Подтверждение установки

```
1. Найти pending-установку по токену в Redis
   └─ если не найдена или истёк TTL → 400 INVALID_TOKEN

2. Проверить что userId совпадает с тем кто инициировал установку
   └─ если не совпадает → 403 FORBIDDEN

3. Удалить токен из Redis

4. Сохранить запись в БД

5. Отправить событие PLUGIN_INSTALLED в Integration Service
```

### Удаление плагина

```
1. Найти установку по installationId
   └─ если не найдена → 404 INSTALLATION_NOT_FOUND

2. Проверить членство userId в chatId (Integration Service)
   └─ если не участник → 403 FORBIDDEN

3. Удалить запись из БД

4. Отправить событие PLUGIN_UNINSTALLED в Integration Service
```

***

## События Integration Service

Сервис отправляет события через `POST /api/v1/events/push`. Integration Service интерпретирует события и уведомляет Dendrite.

### PLUGIN_INSTALLED

```json
{
  "userId": "@user:homeserver.org",
  "pluginId": "uuid",
  "chatId": "!roomid:homeserver.org",
  "eventName": "PLUGIN_INSTALLED",
  "eventData": {
    "versionId": "uuid",
    "installationId": "uuid"
  }
}
```

### PLUGIN_UNINSTALLED

```json
{
  "userId": "@user:homeserver.org",
  "pluginId": "uuid",
  "chatId": "!roomid:homeserver.org",
  "eventName": "PLUGIN_UNINSTALLED",
  "eventData": {
    "installationId": "uuid"
  }
}
```

***

## Коды ошибок

Все ошибки возвращаются в формате:

```json
{
  "error": "КОД_ОШИБКИ",
  "message": "Описание ошибки"
}
```

| HTTP | Код | Причина |
|------|-----|---------|
| 400 | `INVALID_TOKEN` | Токен подтверждения невалиден или истёк (TTL 10 минут) |
| 403 | `FORBIDDEN` | Пользователь не является участником чата |
| 404 | `PLUGIN_NOT_FOUND` | Плагин не найден в Registry, статус не ACTIVE, или нет версий |
| 404 | `VERSION_NOT_FOUND` | Версия не найдена среди версий плагина |
| 404 | `INSTALLATION_NOT_FOUND` | Запись установки не найдена в БД |
| 409 | `ALREADY_INSTALLED` | Плагин уже установлен в этом чате |
| 500 | `INTERNAL_ERROR` | Внутренняя ошибка сервиса или недоступность зависимостей |

***

## Ограничения

### Функциональные

- Один плагин может быть установлен в один чат только один раз.
- Проверка членства в чате делегируется Integration Service — если тот недоступен, сервис вернёт `500`.
- Порядок версий определяется тем в каком порядке Registry возвращает массив `versions[]`. Предполагается что `versions[0]` — последняя версия.
- Токен подтверждения действует **10 минут** (настраивается через `pending.install.ttl-minutes`).

***

## База данных

### Таблица `installations`

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE installations (
    installation_id UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    plugin_id       UUID         NOT NULL,
    version_id      UUID         NOT NULL,
    chat_id         VARCHAR(100) NOT NULL,
    installed_by    VARCHAR(100) NOT NULL,
    installed_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_plugin_chat UNIQUE (plugin_id, chat_id)
);

CREATE INDEX idx_installations_chat_id   ON installations (chat_id);
CREATE INDEX idx_installations_plugin_id ON installations (plugin_id);
```

Уникальный constraint `uq_plugin_chat` гарантирует что один плагин не может быть установлен в один чат дважды на уровне БД — дополнительная защита помимо проверки в коде.

### Redis

Хранит pending-токены подтверждения установки в формате:

```
Ключ:    pending_install:<uuid-токена>
Значение: {"request":{...},"chatId":"...","userId":"..."}
TTL:     600 секунд (10 минут)
```