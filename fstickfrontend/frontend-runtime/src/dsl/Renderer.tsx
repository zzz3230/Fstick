import React, { useSyncExternalStore } from 'react';
import type { DslNode } from './runner';
import { isReactive, type StateRef } from './state.ts';

type RendererProps = {
  node: DslNode;
};

export function DslRenderer({ node }: RendererProps) {
  return renderNode(node);
}

function renderNode(node: DslNode): React.ReactElement {
  switch (node.type) {
    case 'Column':
      return (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: (node.props.gap as number) ?? 0,
            alignSelf: (node.props.self_align as React.CSSProperties['alignSelf']) ?? 'stretch',
            flex: node.props.weight ? (node.props.weight as number) : undefined,
          }}
        >
          {(node.children as DslNode[]).map((child, i) => (
            <React.Fragment key={i}>{renderNode(child)}</React.Fragment>
          ))}
        </div>
      );

    case 'Row':
      return (
        <div
          style={{
            display: 'flex',
            flexDirection: 'row',
            gap: (node.props.gap as number) ?? 0,
            alignSelf: (node.props.self_align as React.CSSProperties['alignSelf']) ?? 'stretch',
            flex: node.props.weight ? (node.props.weight as number) : undefined,
          }}
        >
          {(node.children as DslNode[]).map((child, i) => (
            <React.Fragment key={i}>{renderNode(child)}</React.Fragment>
          ))}
        </div>
      );

    case 'Text': {
      const fontSizeMap: Record<string, string> = {
        title: '1.5rem',
        small: '0.75rem',
        normal: '1rem',
      };
      const fontSize = fontSizeMap[node.props.font_size as string] ?? '1rem';
      const style: React.CSSProperties = {
        textAlign: (node.props.content_align as React.CSSProperties['textAlign']) ?? 'left',
        fontSize,
        margin: 0,
      };

      // Реактивная ячейка — подписываемся через useSyncExternalStore
      if (isReactive(node.content)) {
        return <ReactiveText ref_={node.content} style={style} />;
      }

      return <p style={style}>{node.content as string}</p>;
    }

    case 'Button':
      return (
        <button
          onClick={node.handler as (() => void) | undefined}
          style={{
            flex: node.props.weight ? (node.props.weight as number) : undefined,
            cursor: 'pointer',
          }}
        >
          {node.label as string}
        </button>
      );

    case 'Input':
      return (
        <input
          id={(node.props.id as string) ?? undefined}
          placeholder={(node.props.placeholder as string) ?? ''}
          style={{
            flex: node.props.weight ? (node.props.weight as number) : undefined,
          }}
        />
      );

    default:
      console.warn(`[DslRenderer] Unknown node type: "${node.type}"`);
      return <span style={{ color: 'red' }}>[unknown: {node.type}]</span>;
  }
}

// ─── Реактивный Text ──────────────────────────────────────────────────────────

function ReactiveText({ ref_, style }: { ref_: StateRef; style: React.CSSProperties }) {
  const value = useSyncExternalStore(
    cb => ref_.subscribe(cb),
    () => ref_.value,
  );
  return <p style={style}>{String(value ?? '')}</p>;
}
