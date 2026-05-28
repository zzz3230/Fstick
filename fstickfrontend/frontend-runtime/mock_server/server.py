"""
Mock HTTP server — имитирует реальный бэкенд DSL-приложения.

Endpoints:
  POST /api/v1/command   — выполнение команды (НЕ возвращает состояние)
  GET  /sync             — long-poll: ждёт изменения состояния, возвращает { state, track_id }
  GET  /state            — debug: текущее состояние без ожидания

Маппинг полей:
  CommandRequest.track_id  ←→  frontend traceId
  SyncResponse.track_id    ←→  frontend resolveTrace(traceId, ...)

Запуск:
  pip install -r requirements.txt
  python server.py
"""

from __future__ import annotations

import asyncio
import copy
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Any

import uvicorn
from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel


# ─── Authoritative server state ──────────────────────────────────────────────

# Состояние — произвольная вложенная структура.
# Клиент получает его полностью при каждом изменении.
_state: dict[str, Any] = {
    "now": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC"),
    "field1": "",
    "user": {
        "name": "Anonymous",
        "status": "idle",
    },
    "stats": {
        "commands_executed": 0,
        "last_command": None,
    },
}


# Long-poll: список Future ожидающих клиентов
_waiters: list[asyncio.Future[dict[str, Any]]] = []


def _push_state(track_id: str | None = None) -> None:
    """
    Оповещает всех ожидающих /sync клиентов о новом состоянии.
    track_id — ссылка на команду, вызвавшую изменение (None = серверный push).
    """
    payload: dict[str, Any] = {
        "state": copy.deepcopy(_state),
        "track_id": track_id,
    }
    for f in _waiters:
        if not f.done():
            f.set_result(payload)
    _waiters.clear()


# ─── Background: periodic server-initiated pushes ────────────────────────────

async def _server_push_loop() -> None:
    """
    Каждые 5 секунд обновляет state.now и пушит состояние клиентам.
    """
    while True:
        await asyncio.sleep(5)
        _state["now"] = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
        print(f"[server] tick → now={_state['now']}")
        _push_state()  # track_id=None — не связан с командой


@asynccontextmanager
async def lifespan(_app: FastAPI):
    task = asyncio.create_task(_server_push_loop())
    yield
    task.cancel()


# ─── App + CORS ───────────────────────────────────────────────────────────────

app = FastAPI(title="DSL Mock Server", version="1.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ─── OpenAPI schemas ──────────────────────────────────────────────────────────

class CommandRequest(BaseModel):
    name: str
    plugin_id: str | None = None
    user_id: str | None = None
    chat_id: str | None = None
    track_id: str | None = None
    args: dict[str, Any] = {}


class CommandError(BaseModel):
    message: str
    code: int
    details: str | None = None


class CommandResponse(BaseModel):
    status: str   # OK | UNKNOWN_ERROR | VALIDATION_ERROR | PLUGIN_ERROR
    response: dict[str, Any] | None = None
    error: CommandError | None = None


# ─── Command handlers ─────────────────────────────────────────────────────────

async def _cmd_test_command(args: dict[str, Any], track_id: str) -> CommandResponse:
    """Базовая команда из примера. Обновляет field1 и stats."""
    await asyncio.sleep(0.6)  # имитация обработки на сервере

    _state["field1"] = "Success"
    _state["stats"]["commands_executed"] = _state["stats"]["commands_executed"] + 1
    _state["stats"]["last_command"] = "test_command"
    _state["user"]["status"] = "active"

    _push_state(track_id=track_id)
    return CommandResponse(status="OK")


async def _cmd_reset(args: dict[str, Any], track_id: str) -> CommandResponse:
    """Сбрасывает состояние в начальное."""
    await asyncio.sleep(0.2)

    _state["field1"] = ""
    _state["user"]["status"] = "idle"
    _state["stats"]["last_command"] = "reset"

    _push_state(track_id=track_id)
    return CommandResponse(status="OK")


async def _cmd_update_user(args: dict[str, Any], track_id: str) -> CommandResponse:
    """
    Обновляет вложенные данные пользователя.
    args: { "name": "...", "status": "..." }
    """
    await asyncio.sleep(0.3)
    d = args
    if "name" in d:
        _state["user"]["name"] = str(d["name"])
    if "status" in d:
        _state["user"]["status"] = str(d["status"])
    _state["stats"]["last_command"] = "update_user"

    _push_state(track_id=track_id)
    return CommandResponse(status="OK")


async def _cmd_fail(args: dict[str, Any], track_id: str) -> CommandResponse:
    """
    Намеренно завершается ошибкой.
    Используется для тестирования отката оптимистичных изменений на клиенте.
    Состояние НЕ изменяется, /sync НЕ вызывается.
    """
    await asyncio.sleep(0.4)
    return CommandResponse(
        status="PLUGIN_ERROR",
        error=CommandError(
            message="Intentional failure",
            code=500,
            details="This command always fails — for rollback testing",
        ),
    )


async def _cmd_slow(args: dict[str, Any], track_id: str) -> CommandResponse:
    """Медленная команда (3 сек) — для проверки длинного optimistic-состояния."""
    await asyncio.sleep(3.0)
    _state["field1"] = "Slow done"
    _state["stats"]["last_command"] = "slow"
    _push_state(track_id=track_id)
    return CommandResponse(status="OK")


_HANDLERS: dict[str, Any] = {
    "test_command":  _cmd_test_command,
    "reset":         _cmd_reset,
    "update_user":   _cmd_update_user,
    "fail_command":  _cmd_fail,
    "slow_command":  _cmd_slow,
}


# ─── Endpoints ────────────────────────────────────────────────────────────────

@app.post("/api/v1/command", response_model=CommandResponse)
async def handle_command(req: CommandRequest) -> CommandResponse:
    """
    Выполняет команду. Не возвращает новое состояние.
    Состояние приходит клиенту через /sync с тем же track_id.
    """
    track_id = req.track_id or str(uuid.uuid4())

    print(f"[command] {req.name} track_id={track_id} args={req.args}")

    handler = _HANDLERS.get(req.name)
    if handler is None:
        return CommandResponse(
            status="UNKNOWN_ERROR",
            error=CommandError(
                message=f"Unknown command: '{req.name}'",
                code=404,
                details=f"Available commands: {', '.join(_HANDLERS)}",
            ),
        )

    result: CommandResponse = await handler(req.args, track_id)
    print(f"[command] {req.name} → {result.status}")
    return result


@app.get("/sync")
async def sync(timeout: float = Query(default=30.0, le=60.0)) -> JSONResponse:
    """
    Long-poll endpoint.

    Держит соединение открытым до изменения состояния или до таймаута.

    Ответ при изменении:
      { "state": { ...полное состояние... }, "track_id": "uuid" | null }
        track_id — ссылка на команду (null = серверный push, не связан с командой)

    Ответ при таймауте (heartbeat):
      { "state": null, "track_id": null }
      Клиент должен немедленно переподключиться.
    """
    loop = asyncio.get_event_loop()
    future: asyncio.Future[dict[str, Any]] = loop.create_future()
    _waiters.append(future)

    try:
        payload = await asyncio.wait_for(asyncio.shield(future), timeout=timeout)
        print(f"[sync] push to client  track_id={payload.get('track_id')}")
        return JSONResponse(payload)
    except asyncio.TimeoutError:
        if future in _waiters:
            _waiters.remove(future)
        # heartbeat — клиент переподключается
        return JSONResponse({"state": None, "track_id": None})


@app.get("/state")
async def get_state() -> JSONResponse:
    """Debug: возвращает текущее состояние немедленно."""
    return JSONResponse({"state": copy.deepcopy(_state)})


# ─── Entry point ──────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("DSL Mock Server")
    print("  POST /api/v1/command")
    print("  GET  /sync")
    print("  GET  /state  (debug)")
    print()
    uvicorn.run(app, host="0.0.0.0", port=8000, log_level="info")

