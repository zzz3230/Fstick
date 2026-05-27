/**
 * BackendService — клиентская обёртка над серверным API.
 *
 * Каждый вызов команды получает уникальный trace_id.
 * По нему сервер и клиент отслеживают соответствие ответа конкретному запросу.
 *
 * Жизненный цикл:
 *   1. CommandInvocation создаётся → генерируется trace_id
 *   2. .before(fn) → оптимистичные записи регистрируются под trace_id
 *   3. Запрос уходит с trace_id
 *   4. Ответ содержит { ok, traceId, state } (полное серверное состояние)
 *      → ok:    applyFull(state) + pending удаляется
 *      → error: rollback(records) + pending удаляется
 *   5. Тот же resolveTrace вызывается из WS/SSE sync-обработчика
 */

import { runOptimistic, type OptimisticRecord, type StateStore } from './state.ts';

export type CommandResult = {
  ok: boolean;
  traceId?: string;
  error?: string;
  /** Полное серверное состояние после выполнения команды */
  state?: Record<string, unknown>;
};

type SendFn = (name: string, args: Record<string, unknown>, traceId: string) => Promise<CommandResult>;

// ─── trace_id ─────────────────────────────────────────────────────────────────

function newTraceId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
  return `tr_${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`;
}

// ─── CommandInvocation ────────────────────────────────────────────────────────

class CommandInvocation implements PromiseLike<CommandResult> {
  readonly traceId: string = newTraceId();
  #name: string;
  #args: Record<string, unknown>;
  #service: BackendService;

  constructor(name: string, args: Record<string, unknown>, service: BackendService) {
    this.#name = name;
    this.#args = args;
    this.#service = service;
  }

  /** Запускает команду с оптимистичным хуком.
   *  fn() выполняется сразу; при ошибке команды — автоматический откат через trace_id. */
  before(fn: () => void): Promise<CommandResult> {
    const records = runOptimistic(fn);
    this.#service._registerPending(this.traceId, records);
    return this.#execute();
  }

  /** PromiseLike — позволяет await без .before() */
  then<TResult1 = CommandResult, TResult2 = never>(
    onFulfilled?: ((value: CommandResult) => TResult1 | PromiseLike<TResult1>) | null,
    onRejected?: ((reason: unknown) => TResult2 | PromiseLike<TResult2>) | null,
  ): PromiseLike<TResult1 | TResult2> {
    return this.#execute().then(onFulfilled, onRejected);
  }

  #execute(): Promise<CommandResult> {
    const { traceId } = this;
    return this.#service._send(this.#name, this.#args, traceId)
      .then(res => {
        this.#service.resolveTrace(traceId, res.ok, res.state);
        return res;
      })
      .catch(err => {
        this.#service.resolveTrace(traceId, false);
        return { ok: false, traceId, error: String(err) } satisfies CommandResult;
      });
  }
}

// ─── BackendService ───────────────────────────────────────────────────────────

export class BackendService {
  #store: StateStore;
  #send: SendFn;
  #pending = new Map<string, OptimisticRecord[]>();

  constructor(store: StateStore, send?: SendFn) {
    this.#store = store;
    this.#send = send ?? httpSend;
  }

  makeCommand(name: string): (args?: Record<string, unknown>) => CommandInvocation {
    return (args = {}) => new CommandInvocation(name, args, this);
  }

  /**
   * Разрешает pending оптимистичные изменения по trace_id.
   *
   * Вызывается автоматически при HTTP-ответе.
   * Также можно вызвать вручную из WS/SSE-обработчика:
   *   ws.onmessage = ({ data }) => {
   *     const { traceId, ok, state } = JSON.parse(data);
   *     backend.resolveTrace(traceId, ok, state);
   *   };
   */
  resolveTrace(traceId: string, ok: boolean, serverState?: Record<string, unknown>): void {
    const records = this.#pending.get(traceId);
    this.#pending.delete(traceId);

    if (!ok) {
      if (records?.length) this.#store.rollback(records);
      return;
    }

    if (serverState) {
      this.#store.applyFull(serverState);
    }
  }

  /** @internal */
  _registerPending(traceId: string, records: OptimisticRecord[]): void {
    this.#pending.set(traceId, records);
  }

  /** @internal */
  _send(name: string, args: unknown[], traceId: string): Promise<CommandResult> {
    return this.#send(name, args, traceId);
  }
}

// ─── HTTP transport (mock_server/server.py) ───────────────────────────────────

const API_BASE = 'http://localhost:8000';

async function httpSend(name: string, args: Record<string, unknown>, traceId: string): Promise<CommandResult> {
  const res = await fetch(`${API_BASE}/api/v1/command`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, args, track_id: traceId }),
  });

  if (!res.ok) {
    return { ok: false, traceId, error: `HTTP ${res.status}` };
  }

  const data = await res.json();
  // data: { status, response, error }
  return {
    ok: data.status === 'OK',
    traceId,
    error: data.error?.message,
    // Состояние НЕ приходит с командой — оно придёт через /sync
  };
}

// ─── Long-poll sync client ────────────────────────────────────────────────────

export type SyncMessage = {
  state: Record<string, unknown> | null;
  track_id: string | null;
};

/**
 * Запускает long-poll цикл против GET /sync.
 * При получении состояния вызывает onMessage.
 * Возвращает функцию остановки.
 */
export function startSyncLoop(
  onMessage: (msg: SyncMessage) => void,
  baseUrl = API_BASE,
): () => void {
  let running = true;

  async function poll() {
    while (running) {
      try {
        const res = await fetch(`${baseUrl}/sync?timeout=30`);
        if (!res.ok) { await sleep(2000); continue; }
        const msg: SyncMessage = await res.json();
        onMessage(msg);
      } catch {
        // сеть недоступна — подождём и переподключимся
        await sleep(3000);
      }
    }
  }

  poll();
  return () => { running = false; };
}

function sleep(ms: number) {
  return new Promise(r => setTimeout(r, ms));
}
