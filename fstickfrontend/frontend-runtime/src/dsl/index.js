/**
 * DSL Library — исполняется изолированно через new Function.
 * Никаких импортов: всё нужное передаётся через DSL_CONTEXT.
 *
 * DSL_CONTEXT shape:
 *   exports: {}               — сюда пишет пользовательский код
 *   registerHandler(fn) => id — регистрирует функцию, возвращает id
 */

/* global DSL_CONTEXT */

const VERSION = '1';

// ─── Internal ─────────────────────────────────────────────────────────────────

function makeNode(type, props, children, extra) {
  return {
    __dsl_node: true,
    version: VERSION,
    type,
    props: props ?? {},
    children: children ?? [],
    ...extra,
  };
}

// ─── Layout ───────────────────────────────────────────────────────────────────

function Column(props, children) {
  return makeNode('Column', props, children);
}

function Row(props, children) {
  return makeNode('Row', props, children);
}

// ─── Primitives ───────────────────────────────────────────────────────────────

function Text(content, props) {
  return makeNode('Text', props, [], { content });
}

function Button(label, onPress, props) {
  return makeNode('Button', props, [], { label, handler: onPress ?? null });
}

function Input(props) {
  return makeNode('Input', props, []);
}

// ─── Binding (заглушка, будет расширена) ─────────────────────────────────────

function Binding(source, transform) {
  return {
    __dsl_binding: true,
    source,
    transform,
  };
}

// ─── Экспортируем в контекст ──────────────────────────────────────────────────

DSL_CONTEXT.exports.__lib__ = { Column, Row, Text, Button, Input, Binding };



