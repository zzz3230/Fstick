/*
Copyright 2025 New Vector Ltd.

SPDX-License-Identifier: AGPL-3.0-only OR GPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE files in the repository root for full details.
*/

import { StateStore, makeRef } from "./state.ts";
import { BackendService, type BackendContext } from "./transport.ts";

export type DslNode = {
    __dsl_node: true;
    version: string;
    type: string;
    props: Record<string, unknown>;
    children: DslNode[];
    [key: string]: unknown;
};

export type DslRunOptions = {
    initialState?: Record<string, unknown>;
    backendContext?: BackendContext;
};

export type DslRunResult = {
    tree: DslNode;
    store: StateStore;
    backend: BackendService;
    inputValues: Record<string, string>;
};

export function runDsl(libCode: string, appCode: string, options: DslRunOptions = {}): DslRunResult {
    const store = new StateStore(options.initialState ?? {});
    const backend = new BackendService(store, options.backendContext);
    const state = makeRef(store, "");

    const DSL_CONTEXT = {
        exports: {} as Record<string, unknown>,
        __inputValues: {},
    };
    DSL_CONTEXT.__inputValues = {};

    const runLib = new Function("DSL_CONTEXT", libCode);
    runLib(DSL_CONTEXT);

    const lib = DSL_CONTEXT.exports.__lib__ as Record<string, unknown>;
    const runApp = new Function("DSL_CONTEXT", ...Object.keys(lib), "state", "backend", appCode);
    runApp(DSL_CONTEXT, ...Object.values(lib), state, backend);

    const tree = DSL_CONTEXT.exports.__app as DslNode;
    if (!tree || !tree.__dsl_node) {
        throw new Error("[DSL Runner] App must set DSL_CONTEXT.exports.__app to a valid node.");
    }

    return { tree, store, backend, inputValues: DSL_CONTEXT.__inputValues as Record<string, string> };
}
