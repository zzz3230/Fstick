/**
 * DSL Runner — загружает и исполняет DSL-код в изолированном scope.
 *
 * Порядок выполнения:
 *   1. DSL-lib (index.js)       → регистрирует компоненты в DSL_CONTEXT.exports.__lib__
 *   2. Пользовательский код     → получает компоненты + state + backend, пишет дерево в __app
 *
 * state  — реактивные ячейки, зеркало серверного состояния (StateField)
 * backend — клиент для вызова команд (BackendService)
 */

import { StateStore, makeRef } from './state.ts';
import { BackendService } from './backend.ts';

export type DslNode = {
  __dsl_node: true;
  version: string;
  type: string;
  props: Record<string, unknown>;
  children: DslNode[];
  [key: string]: unknown;
};

export type DslBinding = {
  __dsl_binding: true;
  source: unknown;
  transform: ((val: unknown) => string) | null;
};

export type DslRunOptions = {
  /** Начальное состояние. В реальности приходит с сервера вместе с appCode. */
  initialState?: Record<string, unknown>;
};

export type DslRunResult = {
  tree: DslNode;
  /** Хранилище состояния — нужно рендереру для подписки на обновления */
  store: StateStore;
  /** Сервис команд — нужен для WS/SSE sync-обработчика */
  backend: BackendService;
};

/**
 * Запускает DSL lib + пользовательский код.
 * @param libCode      — содержимое src/dsl/index.js
 * @param appCode      — пользовательский DSL-скрипт
 * @param options      — начальное состояние и прочие настройки
 */
export function runDsl(libCode: string, appCode: string, options: DslRunOptions = {}): DslRunResult {
  const store = new StateStore(options.initialState ?? {});
  const backend = new BackendService(store);

  // state — корневой Proxy: state.field1, state.user.profile.name и т.д.
  const state = makeRef(store, '');

  const DSL_CONTEXT = {
    exports: {} as Record<string, unknown>,
  };

  const runLib = new Function('DSL_CONTEXT', libCode);
  runLib(DSL_CONTEXT);

  const lib = DSL_CONTEXT.exports.__lib__ as Record<string, unknown>;

  const runApp = new Function('DSL_CONTEXT', ...Object.keys(lib), 'state', 'backend', appCode);
  runApp(DSL_CONTEXT, ...Object.values(lib), state, backend);

  const tree = DSL_CONTEXT.exports.__app as DslNode;

  if (!tree || !tree.__dsl_node) {
    throw new Error('[DSL Runner] App must set DSL_CONTEXT.exports.__app to a valid node.');
  }

  return { tree, store, backend };
}
