function getDeep(obj: unknown, path: string): unknown {
    if (!path) return obj;
    return path.split(".").reduce<unknown>(
        (cur, key) => (cur != null && typeof cur === "object" ? (cur as Record<string, unknown>)[key] : undefined),
        obj,
    );
}

function setDeep(root: Record<string, unknown>, path: string, value: unknown): void {
    const keys = path.split(".");
    let cur: Record<string, unknown> = root;
    for (let i = 0; i < keys.length - 1; i++) {
        const key = keys[i];
        if (cur[key] == null || typeof cur[key] !== "object") cur[key] = {};
        cur = cur[key] as Record<string, unknown>;
    }
    cur[keys[keys.length - 1]] = value;
}

export type OptimisticRecord = { path: string; prev: unknown };

let batch: OptimisticRecord[] | null = null;

export function runOptimistic(fn: () => void): OptimisticRecord[] {
    batch = [];
    fn();
    const records = batch;
    batch = null;
    return records;
}

export class StateStore {
    private state: Record<string, unknown>;
    private readonly subs = new Map<string, Set<() => void>>();

    public constructor(initial: Record<string, unknown> = {}) {
        this.state = initial;
    }

    public getPath(path: string): unknown {
        return getDeep(this.state, path);
    }

    public setPath(path: string, value: unknown): void {
        if (batch !== null && !batch.find((record) => record.path === path)) {
            batch.push({ path, prev: this.getPath(path) });
        }

        if (path) {
            setDeep(this.state, path, value);
        } else {
            this.state = value as Record<string, unknown>;
        }

        this.notifyAncestors(path);
    }

    public rollback(records: OptimisticRecord[]): void {
        for (const { path, prev } of records) {
            if (path) {
                setDeep(this.state, path, prev);
            } else {
                this.state = prev as Record<string, unknown>;
            }
            this.notifyAncestors(path);
        }
    }

    public applyFull(incoming: Record<string, unknown>): void {
        const prev = this.state;
        this.state = incoming;
        for (const path of this.subs.keys()) {
            if (getDeep(prev, path) !== getDeep(incoming, path)) {
                this.subs.get(path)?.forEach((fn) => fn());
            }
        }
    }

    public subscribe(path: string, fn: () => void): () => void {
        if (!this.subs.has(path)) this.subs.set(path, new Set());
        this.subs.get(path)!.add(fn);
        return () => this.subs.get(path)?.delete(fn);
    }

    private notifyAncestors(path: string): void {
        const parts = path ? path.split(".") : [];
        const paths = ["", ...parts.map((_, i) => parts.slice(0, i + 1).join("."))];
        for (const p of paths) {
            this.subs.get(p)?.forEach((fn) => fn());
        }
    }
}

export const REACTIVE = Symbol("dsl.reactive");

export interface StateRef {
    readonly [REACTIVE]: true;
    get value(): unknown;
    set(v: unknown): void;
    subscribe(fn: () => void): () => void;
}

export function makeRef(store: StateStore, path: string): StateRef {
    return new Proxy({} as StateRef, {
        get(_target, key: string | symbol): unknown {
            if (key === REACTIVE) return true;
            if (key === "value") return store.getPath(path);
            if (key === "set") return (v: unknown) => store.setPath(path, v);
            if (key === "subscribe") return (fn: () => void) => store.subscribe(path, fn);
            if (typeof key === "symbol") return undefined;
            const child = path ? `${path}.${key}` : String(key);
            return makeRef(store, child);
        },
    });
}

export function isReactive(v: unknown): v is StateRef {
    return v != null && typeof v === "object" && (v as Record<symbol, unknown>)[REACTIVE] === true;
}

