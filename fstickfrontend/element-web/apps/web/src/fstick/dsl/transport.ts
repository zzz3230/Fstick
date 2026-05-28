/*
Copyright 2025 New Vector Ltd.

SPDX-License-Identifier: AGPL-3.0-only OR GPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE files in the repository root for full details.
*/

import { runOptimistic, type OptimisticRecord, type StateStore } from "./state.ts";

export type CommandResult = {
    ok: boolean;
    traceId?: string;
    error?: string;
    state?: Record<string, unknown>;
};

export type BackendContext = {
    apiBase: string;
    accessToken: string;
    userId: string;
    roomId: string;
    pluginId: string;
};

type SendFn = (name: string, args: Record<string, unknown>, traceId: string) => Promise<CommandResult>;

function newTraceId(): string {
    if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
    return `tr_${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`;
}

class CommandInvocation implements PromiseLike<CommandResult> {
    public readonly traceId = newTraceId();
    private readonly name: string;
    private readonly args: Record<string, unknown>;
    private readonly service: BackendService;

    public constructor(name: string, args: Record<string, unknown>, service: BackendService) {
        this.name = name;
        this.args = args;
        this.service = service;
    }

    public before(fn: () => void): Promise<CommandResult> {
        const records = runOptimistic(fn);
        this.service.registerPending(this.traceId, records);
        return this.execute();
    }

    public then<TResult1 = CommandResult, TResult2 = never>(
        onFulfilled?: ((value: CommandResult) => TResult1 | PromiseLike<TResult1>) | null,
        onRejected?: ((reason: unknown) => TResult2 | PromiseLike<TResult2>) | null,
    ): PromiseLike<TResult1 | TResult2> {
        return this.execute().then(onFulfilled, onRejected);
    }

    private execute(): Promise<CommandResult> {
        const { traceId } = this;
        return this.service
            .send(this.name, this.args, traceId)
            .then((result) => {
                this.service.resolveTrace(traceId, result.ok, result.state);
                return result;
            })
            .catch((error: unknown) => {
                this.service.resolveTrace(traceId, false);
                return { ok: false, traceId, error: String(error) } satisfies CommandResult;
            });
    }
}

export class BackendService {
    private readonly store: StateStore;
    private readonly sendFn: SendFn;
    private readonly pending = new Map<string, OptimisticRecord[]>();

    public constructor(store: StateStore, context?: BackendContext, send?: SendFn) {
        this.store = store;
        this.sendFn =
            send ??
            (context
                ? makeHttpSend(context)
                : () => Promise.resolve({ ok: false, error: "Backend is not configured" }));
    }

    public makeCommand(name: string): (args?: Record<string, unknown>) => CommandInvocation {
        return (args = {}) => new CommandInvocation(name, args, this);
    }

    public resolveTrace(traceId: string, ok: boolean, serverState?: Record<string, unknown>): void {
        const records = this.pending.get(traceId);
        this.pending.delete(traceId);

        if (!ok) {
            if (records?.length) this.store.rollback(records);
            return;
        }

        if (serverState) {
            this.store.applyFull(serverState);
        }
    }

    public registerPending(traceId: string, records: OptimisticRecord[]): void {
        this.pending.set(traceId, records);
    }

    public send(name: string, args: Record<string, unknown>, traceId: string): Promise<CommandResult> {
        return this.sendFn(name, args, traceId);
    }
}

function makeHttpSend(context: BackendContext): SendFn {
    return async (name: string, args: Record<string, unknown>, traceId: string): Promise<CommandResult> => {
        const endpoint = `${context.apiBase}/plugins/${encodeURIComponent(context.pluginId)}/command?chat_id=${encodeURIComponent(context.roomId)}`;
        const response = await fetch(endpoint, {
            method: "POST",
            headers: {
                Accept: "application/json",
                "Content-Type": "application/json",
                Authorization: `Bearer ${context.accessToken}`,
            },
            body: JSON.stringify({ name, args, track_id: traceId }),
        });

        if (!response.ok) {
            return { ok: false, traceId, error: `HTTP ${response.status}` };
        }

        const payload = (await response.json()) as Record<string, unknown>;
        const status = payload.status as string | undefined;
        const ok = status === "SUCCESS" || status === "OK" || payload.ok === true;
        const state = (payload.state && typeof payload.state === "object" ? payload.state : undefined) as
            | Record<string, unknown>
            | undefined;

        return {
            ok,
            traceId,
            error: payload.error ? JSON.stringify(payload.error) : undefined,
            state,
        };
    };
}
