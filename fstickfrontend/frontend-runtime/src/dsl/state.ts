/**
 * StateStore — реактивное зеркало серверного состояния.
 *
 * Состояние — произвольный вложенный объект, целиком приходящий с сервера.
 * Доступ к полям — через makeRef(store, path):
 *
 *   state.field1.set('loading')       // плоское поле
 *   state.user.profile.name.value     // вложенное чтение
 *   Text(state.stats.total, ...)      // реактивная привязка к рендереру
 *
 * Уведомления при изменении пути 'a.b.c' уходят подписчикам:
 *   'a.b.c', 'a.b', 'a', '' — чтобы подписка на родителя тоже срабатывала.
 *
 * При applyFull() сравниваются все подписанные пути — уведомляются только изменившиеся.
 */

// ─── Вспомогательные функции ──────────────────────────────────────────────────

function getDeep(obj: unknown, path: string): unknown {
  if (!path) return obj;
  return path.split('.').reduce<unknown>(
    (cur, key) => (cur != null && typeof cur === 'object' ? (cur as Record<string, unknown>)[key] : undefined),
    obj,
  );
}

function setDeep(root: Record<string, unknown>, path: string, value: unknown): void {
  const keys = path.split('.');
  let cur: Record<string, unknown> = root;
  for (let i = 0; i < keys.length - 1; i++) {
    const k = keys[i];
    if (cur[k] == null || typeof cur[k] !== 'object') cur[k] = {};
    cur = cur[k] as Record<string, unknown>;
  }
  cur[keys[keys.length - 1]] = value;
}

// ─── Optimistic batch ─────────────────────────────────────────────────────────

export type OptimisticRecord = { path: string; prev: unknown };

let _batch: OptimisticRecord[] | null = null;

export function runOptimistic(fn: () => void): OptimisticRecord[] {
  _batch = [];
  fn();
  const records = _batch;
  _batch = null;
  return records;
}

// ─── StateStore ───────────────────────────────────────────────────────────────

export class StateStore {
  #state: Record<string, unknown>;
  #subs = new Map<string, Set<() => void>>();

  constructor(initial: Record<string, unknown> = {}) {
    this.#state = initial;
  }

  getPath(path: string): unknown {
    return getDeep(this.#state, path);
  }

  /** Вызывается пользователем (из .set()). Если активен optimistic-batch — записывает prev. */
  setPath(path: string, value: unknown): void {
    if (_batch !== null && !_batch.find(r => r.path === path)) {
      _batch.push({ path, prev: this.getPath(path) });
    }
    if (path) {
      setDeep(this.#state, path, value);
    } else {
      this.#state = value as Record<string, unknown>;
    }
    this.#notifyAncestors(path);
  }

  /** Откат optimistic-изменений. Bypasses batch tracking. */
  rollback(records: OptimisticRecord[]): void {
    for (const { path, prev } of records) {
      if (path) {
        setDeep(this.#state, path, prev);
      } else {
        this.#state = prev as Record<string, unknown>;
      }
      this.#notifyAncestors(path);
    }
  }

  /**
   * Применяет полное состояние от сервера.
   * Уведомляет только подписчиков чьё значение изменилось.
   */
  applyFull(incoming: Record<string, unknown>): void {
    const prev = this.#state;
    this.#state = incoming;
    for (const path of this.#subs.keys()) {
      if (getDeep(prev, path) !== getDeep(incoming, path)) {
        this.#subs.get(path)?.forEach(fn => fn());
      }
    }
  }

  subscribe(path: string, fn: () => void): () => void {
    if (!this.#subs.has(path)) this.#subs.set(path, new Set());
    this.#subs.get(path)!.add(fn);
    return () => this.#subs.get(path)?.delete(fn);
  }

  /** Уведомляет сам путь и всех его предков ('' → 'a' → 'a.b' → 'a.b.c') */
  #notifyAncestors(path: string): void {
    const parts = path ? path.split('.') : [];
    const paths = ['', ...parts.map((_, i) => parts.slice(0, i + 1).join('.'))];
    for (const p of paths) {
      this.#subs.get(p)?.forEach(fn => fn());
    }
  }
}

// ─── Reactive ref (Proxy) ─────────────────────────────────────────────────────

export const REACTIVE = Symbol('dsl.reactive');

// Интерфейс для TypeScript — описывает то, что видит рендерер
export interface StateRef {
  readonly [REACTIVE]: true;
  get value(): unknown;
  set(v: unknown): void;
  subscribe(fn: () => void): () => void;
}

export function makeRef(store: StateStore, path: string): StateRef {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return new Proxy({} as any, {
    get(_t, key: string | symbol): unknown {
      if (key === REACTIVE)    return true;
      if (key === 'value')     return store.getPath(path);
      if (key === 'set')       return (v: unknown) => store.setPath(path, v);
      if (key === 'subscribe') return (fn: () => void) => store.subscribe(path, fn);
      if (typeof key === 'symbol') return undefined;
      const child = path ? `${path}.${key}` : String(key);
      return makeRef(store, child);
    },
  });
}

export function isReactive(v: unknown): v is StateRef {
  return v != null && typeof v === 'object' && (v as Record<symbol, unknown>)[REACTIVE] === true;
}
