/*
Copyright 2025 New Vector Ltd.

SPDX-License-Identifier: AGPL-3.0-only OR GPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE files in the repository root for full details.
*/

import React, { useSyncExternalStore } from "react";

import type { DslNode } from "./runner.ts";
import { isReactive, type StateRef } from "./state.ts";

type RendererProps = {
    node: DslNode;
    state?: StateRef;
    inputValues?: Record<string, string>;
};

export function DslRenderer({ node, state, inputValues }: RendererProps): React.ReactElement {
    return renderNode(node, state, inputValues);
}

function renderNode(node: DslNode, state?: StateRef, inputValues?: Record<string, string>): React.ReactElement {
    switch (node.type) {
        case "Column":
            return (
                <div
                    style={{
                        display: "flex",
                        flexDirection: "column",
                        gap: (node.props.gap as number) ?? 0,
                        alignSelf: (node.props.self_align as React.CSSProperties["alignSelf"]) ?? "stretch",
                        flex: node.props.weight ? (node.props.weight as number) : undefined,
                    }}
                >
                    {(node.children as DslNode[]).map((child, i) => (
                        <React.Fragment key={i}>{renderNode(child, state, inputValues)}</React.Fragment>
                    ))}
                </div>
            );

        case "Row":
            return (
                <div
                    style={{
                        display: "flex",
                        flexDirection: "row",
                        gap: (node.props.gap as number) ?? 0,
                        alignSelf: (node.props.self_align as React.CSSProperties["alignSelf"]) ?? "stretch",
                        flex: node.props.weight ? (node.props.weight as number) : undefined,
                    }}
                >
                    {(node.children as DslNode[]).map((child, i) => (
                        <React.Fragment key={i}>{renderNode(child, state, inputValues)}</React.Fragment>
                    ))}
                </div>
            );

        case "Text": {
            const fontSizeMap: Record<string, string> = {
                title: "1.5rem",
                small: "0.75rem",
                normal: "1rem",
            };
            const fontSize = fontSizeMap[node.props.font_size as string] ?? "1rem";
            const style: React.CSSProperties = {
                textAlign: (node.props.content_align as React.CSSProperties["textAlign"]) ?? "left",
                fontSize,
                margin: 0,
            };

            if (isReactive(node.content)) {
                return <ReactiveText ref_={node.content} style={style} />;
            }

            return <p style={style}>{node.content as string}</p>;
        }

        case "Button":
            return (
                <button
                    onClick={node.handler as (() => void) | undefined}
                    style={{
                        flex: node.props.weight ? (node.props.weight as number) : undefined,
                        cursor: "pointer",
                    }}
                >
                    {node.label as string}
                </button>
            );

        case "Input": {
            const inputId = (node.props.id as string) ?? "";
            const [localValue, setLocalValue] = React.useState(inputValues?.[inputId] ?? "");

            return (
                <input
                    id={inputId || undefined}
                    value={localValue}
                    placeholder={(node.props.placeholder as string) ?? ""}
                    onChange={(e) => {
                        const newValue = e.target.value;
                        setLocalValue(newValue);
                        if (inputValues) {
                            inputValues[inputId] = newValue;
                        }
                    }}
                    style={{
                        flex: node.props.weight ? (node.props.weight as number) : undefined,
                    }}
                />
            );
        }

        case "IfBlock": {
            const condition = node.condition as () => boolean;
            // Subscribe to root path to catch ALL state changes, forcing re-render
            if (!state) {
                console.warn("[DslRenderer] IfBlock requires state prop");
                return <></>;
            }
            // eslint-disable-next-line react-hooks/rules-of-hooks
            useSyncExternalStore(
                (cb) => state.subscribe(cb),
                () => condition(),
            );

            if (condition()) {
                return (
                    <>
                        {(node.children as DslNode[]).map((child, i) => (
                            <React.Fragment key={i}>{renderNode(child, state, inputValues)}</React.Fragment>
                        ))}
                    </>
                );
            }
            return <></>;
        }

        case "OnUpdate": {
            const field = node.watch as StateRef;
            useSyncExternalStore(
                (cb) => field.subscribe(cb),
                () => field.value,
            );
            return (
                <>
                    {(node.children as DslNode[]).map((child, i) => (
                        <React.Fragment key={i}>{renderNode(child, state, inputValues)}</React.Fragment>
                    ))}
                </>
            );
        }

        default:
            console.warn(`[DslRenderer] Unknown node type: "${node.type}"`);
            return <span style={{ color: "red" }}>[unknown: {node.type}]</span>;
    }
}

function ReactiveText({ ref_, style }: { ref_: StateRef; style: React.CSSProperties }): React.ReactElement {
    const value = useSyncExternalStore(
        (cb) => ref_.subscribe(cb),
        () => ref_.value,
    );
    return <p style={style}>{String(value ?? "")}</p>;
}
