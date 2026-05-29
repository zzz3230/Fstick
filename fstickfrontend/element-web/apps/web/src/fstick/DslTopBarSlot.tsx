import React, { useCallback, useEffect, useRef, useState } from "react";

import SdkConfig from "../SdkConfig";
import { MatrixClientPeg } from "../MatrixClientPeg";
import { loadPluginsForChat, type PluginEntry } from "./dsl/loader";
import { runDsl, type DslRunResult } from "./dsl/runner";
import { makeRef, type StateRef } from "./dsl/state";
import { DslRenderer } from "./dsl/Renderer";
import DSL_LIB_CODE from "./dsl/libCode";
import { FSTICK_SYNC_EVENT, type FstickSyncEventDetail } from "./syncInterceptor";

const LOG = "[FStick:DslTopBarSlot]";

interface Props {
    roomId: string;
    userId: string;
}

interface RunningPlugin {
    entry: PluginEntry;
    result: DslRunResult;
}

function resolveApiBase(): string {
    const raw =
        (SdkConfig.get("fstick_marketplace_api_url" as Parameters<typeof SdkConfig.get>[0]) as string | undefined) ??
        "";
    return raw.replace(/\/(plugins|registry).*$/, "");
}

export function DslTopBarSlot({ roomId, userId }: Props): React.ReactElement | null {
    const [plugins, setPlugins] = useState<RunningPlugin[]>([]);

    // 👇 отдельные высоты по pluginId
    const [heights, setHeights] = useState<Record<string, number>>({});
    const [resizingId, setResizingId] = useState<string | null>(null);

    const loadedRoom = useRef<string | null>(null);

    const startY = useRef<number>(0);
    const startHeight = useRef<number>(80);

    const onResizeStart = useCallback(
        (installationId: string, currentHeight: number) => (e: React.MouseEvent) => {
            e.preventDefault();

            setResizingId(installationId);
            startY.current = e.clientY;
            startHeight.current = currentHeight;
        },
        [],
    );

    useEffect(() => {
        if (!resizingId) return;

        const onMouseMove = (e: MouseEvent) => {
            const delta = e.clientY - startY.current;
            const newHeight = Math.max(40, Math.min(500, startHeight.current + delta));

            setHeights((prev) => ({
                ...prev,
                [resizingId]: newHeight,
            }));
        };

        const onMouseUp = () => {
            setResizingId(null);
        };

        window.addEventListener("mousemove", onMouseMove);
        window.addEventListener("mouseup", onMouseUp);

        return () => {
            window.removeEventListener("mousemove", onMouseMove);
            window.removeEventListener("mouseup", onMouseUp);
        };
    }, [resizingId]);

    const loadPlugins = useCallback(
        (force = false) => {
            if (!force && loadedRoom.current === roomId) return;
            loadedRoom.current = roomId;
            setPlugins([]);

            const apiBase = resolveApiBase();
            if (!apiBase) return;

            const accessToken = MatrixClientPeg.get()?.getAccessToken() ?? "";

            loadPluginsForChat(apiBase, roomId, accessToken)
                .then((entries: PluginEntry[]) => {
                    const running: RunningPlugin[] = [];

                    for (const entry of entries) {
                        try {
                            const result = runDsl(DSL_LIB_CODE, entry.appCode, {
                                initialState: entry.initialState,
                                backendContext: {
                                    apiBase,
                                    accessToken,
                                    userId,
                                    roomId,
                                    pluginId: entry.pluginId,
                                },
                            });

                            running.push({ entry, result });
                        } catch (e) {
                            console.warn(`${LOG} failed plugin ${entry.pluginId}`, e);
                        }
                    }

                    setPlugins(running);
                })
                .catch((e) => {
                    console.warn(`${LOG} load error`, e);
                });
        },
        [roomId, userId],
    );

    useEffect(() => {
        loadPlugins();
    }, [loadPlugins]);

    useEffect(() => {
        const handler = (e: Event): void => {
            const detail = (e as CustomEvent<{ roomId: string; pluginId: string }>).detail;
            if (detail?.roomId !== roomId) return;
            loadPlugins(true);
        };

        window.addEventListener("fstick:plugin-installed", handler);
        return () => window.removeEventListener("fstick:plugin-installed", handler);
    }, [roomId, loadPlugins]);

    useEffect(() => {
        if (!plugins.length) return;

        const handler = (e: Event): void => {
            const detail = (e as CustomEvent<FstickSyncEventDetail>).detail;

            if (detail.type !== "fstick.plugin.state") return;

            const content = detail.content as {
                plugin_id?: string;
                chat_id?: string;
                state?: Record<string, unknown>;
            };

            if (content.chat_id !== roomId || !content.state) return;

            for (const { entry, result } of plugins) {
                if (entry.pluginId === content.plugin_id) {
                    result.store.applyFull(content.state);
                }
            }
        };

        window.addEventListener(FSTICK_SYNC_EVENT, handler);
        return () => window.removeEventListener(FSTICK_SYNC_EVENT, handler);
    }, [plugins, roomId]);

    if (!plugins.length) return null;

    return (
        <>
            {plugins.map(({ entry, result }) => {
                const height = heights[entry.installationId] ?? 80;
                const isResizing = resizingId === entry.installationId;

                return (
                    <div
                        key={entry.installationId}
                        className="fstick-dsl-slot"
                        style={{
                            display: "flex",
                            flexDirection: "column",
                            alignItems: "stretch",
                            justifyContent: "center",
                            backgroundColor: "#ffffff",
                            height: `${height}px`,
                            padding: "8px 16px 0",
                            position: "relative",
                            cursor: isResizing ? "row-resize" : "default",
                            borderBottom: "2px solid #d1d5db",
                        }}
                    >
                        <div
                            style={{
                                flex: 1,
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                overflow: "hidden",
                                minHeight: 0,
                            }}
                        >
                            <div
                                style={{
                                    maxWidth: "100%",
                                    maxHeight: "100%",
                                    overflow: "auto",
                                }}
                            >
                                <DslRenderer node={result.tree} state={makeRef(result.store, "")} />
                            </div>
                        </div>

                        <div
                            onMouseDown={onResizeStart(entry.installationId, height)}
                            style={{
                                height: "12px",
                                cursor: "ns-resize",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                flexShrink: 0,
                            }}
                        >
                            <div
                                style={{
                                    width: "40px",
                                    height: "4px",
                                    borderRadius: "2px",
                                    backgroundColor: "#ccc",
                                }}
                            />
                        </div>
                    </div>
                );
            })}
        </>
    );
}
