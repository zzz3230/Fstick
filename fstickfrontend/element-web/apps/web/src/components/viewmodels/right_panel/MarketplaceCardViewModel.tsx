/*
Copyright 2026 New Vector Ltd.
SPDX-License-Identifier: AGPL-3.0-only OR GPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE files in the repository root for full details.
*/

import { useEffect, useMemo, useState } from "react";

import SdkConfig from "../../../SdkConfig";

export interface MarketplacePlugin {
    id: string;
    name: string;
    description?: string;
    author?: string;
    version?: string;
    homepageUrl?: string;
    iconUrl?: string;
    tags?: string[];
}

interface PluginVersionView {
    version_id: string;
    version: string;
}

interface InstallationShort {
    installationId: string;
    pluginId: string;
}

interface InstallationListResponse {
    data?: InstallationShort[];
}

interface UploadFileData {
    file_name: string;
    key: string;
    upload_url: string;
}

interface AddPluginInitResponse {
    plugin_id: string;
    version_id: string;
    uploads: UploadFileData[];
    icon_upload: UploadFileData;
}

export interface CodeFileEntry {
    file: File;
    /** e.g. "sv.lua@1.0.0" or "cl.js@1.0.0" – determines the S3 key path */
    runtime: string;
}

export interface UploadPluginInput {
    name: string;
    description: string;
    /** Primary runtime stored in version record, e.g. "sv.lua@1.0.0" */
    runtime: string;
    version: string;
    category?: string;
    tags?: string;
    /** Each entry carries its own runtime so type = "code/<entry.runtime>" */
    codeFiles: CodeFileEntry[];
    iconFile: File;
}

export interface MarketplaceCardViewModelState {
    query: string;
    isLoading: boolean;
    error: string | null;
    hasEndpoint: boolean;
    plugins: MarketplacePlugin[];
    installedPlugins: MarketplacePlugin[];
    isUploading: boolean;
    uploadError: string | null;
    onQueryChange: (query: string) => void;
    retry: () => void;
    installPlugin: (pluginId: string) => Promise<void>;
    uninstallPlugin: (pluginId: string) => Promise<void>;
    uploadPlugin: (payload: UploadPluginInput) => Promise<void>;
    isInstalled: (pluginId: string) => boolean;
    isActionInProgress: (pluginId: string) => boolean;
}

function normalizePlugins(payload: unknown): MarketplacePlugin[] {
    const shape = payload as Record<string, unknown>;
    let candidates: unknown[] = [];
    if (Array.isArray(payload)) {
        candidates = payload;
    } else if (Array.isArray(shape?.items)) {
        candidates = shape.items as unknown[];
    } else if (Array.isArray(shape?.data)) {
        candidates = shape.data as unknown[];
    } else if (Array.isArray(shape?.plugins)) {
        candidates = shape.plugins as unknown[];
    }

    const readString = (value: unknown): string | undefined => (typeof value === "string" ? value : undefined);

    return candidates
        .filter((entry): entry is Record<string, unknown> => Boolean(entry && typeof entry === "object"))
        .map((raw, index) => ({
            id: readString(raw.id) ?? readString(raw.pluginId) ?? readString(raw.uuid) ?? `${index}`,
            name: readString(raw.name) ?? readString(raw.title) ?? `plugin-${index}`,
            description: readString(raw.description),
            author: readString(raw.author) ?? readString(raw.authorId) ?? readString(raw.author_id),
            version: readString(raw.version),
            homepageUrl: readString(raw.url),
            iconUrl: readString(raw.icon_url) ?? readString(raw.iconUrl),
            tags: Array.isArray(raw.tags) ? (raw.tags as unknown[]).filter((t): t is string => typeof t === "string") : undefined,
        }));
}

function semverWeight(version: string): number {
    const parts = version.split(".").map((n) => Number.parseInt(n, 10));
    if (parts.length !== 3 || parts.some((n) => Number.isNaN(n))) return -1;
    return parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2];
}

function resolveApiBase(endpoint: string): string {
    return endpoint.replace(/\/plugins(?:\/.*)?$/, "");
}

function parseImageSubtype(file: File): string {
    if (file.type.startsWith("image/")) return file.type.slice("image/".length);
    const dot = file.name.lastIndexOf(".");
    if (dot > 0 && dot < file.name.length - 1) return file.name.slice(dot + 1).toLowerCase();
    return "png";
}

function ensureOk(response: Response, message: string): void {
    if (!response.ok) throw new Error(`${message} (${response.status})`);
}

function buildHeaders(userId?: string, extra?: Record<string, string>): Record<string, string> {
    const headers: Record<string, string> = { Accept: "application/json", ...(extra ?? {}) };
    if (userId) headers["X-User-Id"] = userId;
    return headers;
}

export function useMarketplaceCardViewModel(roomId?: string, userId?: string): MarketplaceCardViewModelState {
    const endpoint = SdkConfig.get("fstick_marketplace_api_url" as Parameters<typeof SdkConfig.get>[0]);
    const hasEndpoint = typeof endpoint === "string" && endpoint.length > 0;
    const apiBase = hasEndpoint ? resolveApiBase(endpoint as string) : "";

    const [query, setQuery] = useState("");
    const [allPlugins, setAllPlugins] = useState<MarketplacePlugin[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [reloadToken, setReloadToken] = useState(0);
    const [installationByPlugin, setInstallationByPlugin] = useState<Record<string, string>>({});
    const [actionByPlugin, setActionByPlugin] = useState<Record<string, boolean>>({});
    const [isUploading, setIsUploading] = useState(false);
    const [uploadError, setUploadError] = useState<string | null>(null);

    useEffect(() => {
        if (!hasEndpoint) {
            setAllPlugins([]);
            setError("Marketplace endpoint is not configured.");
            return;
        }

        const controller = new AbortController();
        const timeout = window.setTimeout(async () => {
            try {
                setIsLoading(true);
                setError(null);

                const pluginsUrl = new URL(`${apiBase}/registry/plugins`);
                if (query.trim()) pluginsUrl.searchParams.set("search", query.trim());

                const pluginsResponse = await fetch(pluginsUrl.toString(), {
                    signal: controller.signal,
                    headers: buildHeaders(userId),
                });
                ensureOk(pluginsResponse, "Marketplace request failed");
                setAllPlugins(normalizePlugins(await pluginsResponse.json()));

                if (roomId && userId) {
                    const installsUrl = new URL(`${apiBase}/installations`);
                    installsUrl.searchParams.set("chat_id", roomId);
                    const installsResponse = await fetch(installsUrl.toString(), {
                        signal: controller.signal,
                        headers: buildHeaders(userId),
                    });
                    if (installsResponse.ok) {
                        const payload = (await installsResponse.json()) as InstallationListResponse;
                        const mapping: Record<string, string> = {};
                        for (const item of payload.data ?? []) {
                            if (item?.pluginId && item?.installationId) {
                                mapping[item.pluginId] = item.installationId;
                            }
                        }
                        setInstallationByPlugin(mapping);
                    }
                }
            } catch (err) {
                if (controller.signal.aborted) return;
                setAllPlugins([]);
                setError(err instanceof Error ? err.message : "Unable to load plugins");
            } finally {
                if (!controller.signal.aborted) setIsLoading(false);
            }
        }, 200);

        return () => {
            window.clearTimeout(timeout);
            controller.abort();
        };
    }, [apiBase, hasEndpoint, query, reloadToken, roomId, userId]);

    const visiblePlugins = useMemo(() => {
        if (!query.trim()) return allPlugins;
        const needle = query.trim().toLowerCase();
        return allPlugins.filter((p) => {
            const haystack = [p.name, p.description, p.author, p.version, ...(p.tags ?? [])]
                .filter(Boolean)
                .join(" ")
                .toLowerCase();
            return haystack.includes(needle);
        });
    }, [allPlugins, query]);

    const installedPlugins = useMemo(
        () => allPlugins.filter((p) => Boolean(installationByPlugin[p.id])),
        [allPlugins, installationByPlugin],
    );

    const installPlugin = async (pluginId: string): Promise<void> => {
        if (!roomId || !userId) throw new Error("Missing room or user context");
        setActionByPlugin((prev) => ({ ...prev, [pluginId]: true }));
        try {
            const detailsResponse = await fetch(`${apiBase}/registry/plugins/${encodeURIComponent(pluginId)}`, {
                headers: buildHeaders(userId),
            });
            ensureOk(detailsResponse, "Failed to load plugin details");
            const detailsPayload = (await detailsResponse.json()) as { versions?: PluginVersionView[] };
            const versions = detailsPayload.versions ?? [];
            if (!versions.length) throw new Error("Plugin has no available versions");

            let selected = versions[0];
            for (const v of versions) {
                if (semverWeight(v.version) > semverWeight(selected.version)) selected = v;
            }

            const installResponse = await fetch(`${apiBase}/installations?chat_id=${encodeURIComponent(roomId)}`, {
                method: "POST",
                headers: buildHeaders(userId, { "Content-Type": "application/json" }),
                body: JSON.stringify({ pluginId, versionId: selected.version_id }),
            });
            const installPayload = installResponse.headers.get("content-type")?.includes("application/json")
                ? await installResponse.json()
                : null;
            ensureOk(installResponse, "Install failed");

            const token = (installPayload as Record<string, unknown>)?.confirmationToken as string | undefined;
            if (token) {
                const confirmResponse = await fetch(`${apiBase}/installations/confirm`, {
                    method: "POST",
                    headers: buildHeaders(userId, { "Content-Type": "application/json" }),
                    body: JSON.stringify({ confirmationToken: token }),
                });
                ensureOk(confirmResponse, "Install confirm failed");
            }
            setReloadToken((v) => v + 1);
        } finally {
            setActionByPlugin((prev) => ({ ...prev, [pluginId]: false }));
        }
    };

    const uninstallPlugin = async (pluginId: string): Promise<void> => {
        if (!userId) throw new Error("Missing user context");
        const installationId = installationByPlugin[pluginId];
        if (!installationId) return;
        setActionByPlugin((prev) => ({ ...prev, [pluginId]: true }));
        try {
            const response = await fetch(`${apiBase}/installations/${encodeURIComponent(installationId)}`, {
                method: "DELETE",
                headers: buildHeaders(userId),
            });
            if (!response.ok) throw new Error(`Uninstall failed (${response.status})`);
            setReloadToken((v) => v + 1);
        } finally {
            setActionByPlugin((prev) => ({ ...prev, [pluginId]: false }));
        }
    };

    const uploadPlugin = async (payload: UploadPluginInput): Promise<void> => {
        if (!hasEndpoint) throw new Error("Marketplace endpoint is not configured");
        setIsUploading(true);
        setUploadError(null);
        try {
            const initResponse = await fetch(`${apiBase}/registry/plugins`, {
                method: "POST",
                headers: buildHeaders(userId, { "Content-Type": "application/json" }),
                body: JSON.stringify({
                    name: payload.name,
                    description: payload.description,
                    version: payload.version,
                    runtime: payload.runtime,
                    category: payload.category ?? "",
                    tags: (payload.tags ?? "")
                        .split(",")
                        .map((t) => t.trim())
                        .filter(Boolean),
                    files: payload.codeFiles.map((e) => ({ file_name: e.file.name, type: `code/${e.runtime}` })),
                    icon: {
                        file_name: payload.iconFile.name,
                        type: `image/${parseImageSubtype(payload.iconFile)}`,
                    },
                }),
            });
            ensureOk(initResponse, "Upload init failed");

            const initPayload = (await initResponse.json()) as AddPluginInitResponse;
            const filesByName = new Map(payload.codeFiles.map((e) => [e.file.name, e.file]));

            for (const upload of initPayload.uploads ?? []) {
                const file = filesByName.get(upload.file_name);
                ensureOk(
                    await fetch(upload.upload_url, {
                        method: "PUT",
                        headers: { "Content-Type": file?.type || "application/octet-stream" },
                        body: file,
                    }),
                    `File upload failed: ${upload.file_name}`,
                );
            }

            ensureOk(
                await fetch(initPayload.icon_upload.upload_url, {
                    method: "PUT",
                    headers: { "Content-Type": payload.iconFile.type || "application/octet-stream" },
                    body: payload.iconFile,
                }),
                "Icon upload failed",
            );

            const commitKeys = [...(initPayload.uploads ?? []).map((u) => u.key), initPayload.icon_upload.key];
            ensureOk(
                await fetch(`${apiBase}/registry/plugins/${encodeURIComponent(initPayload.plugin_id)}/commit`, {
                    method: "POST",
                    headers: buildHeaders(userId, { "Content-Type": "application/json" }),
                    body: JSON.stringify({ version_id: initPayload.version_id, keys: commitKeys }),
                }),
                "Upload commit failed",
            );

            setReloadToken((v) => v + 1);
        } catch (err) {
            const message = err instanceof Error ? err.message : "Plugin upload failed";
            setUploadError(message);
            throw err;
        } finally {
            setIsUploading(false);
        }
    };

    return {
        query,
        isLoading,
        error,
        hasEndpoint,
        plugins: visiblePlugins,
        installedPlugins,
        isUploading,
        uploadError,
        onQueryChange: setQuery,
        retry: () => setReloadToken((v) => v + 1),
        installPlugin,
        uninstallPlugin,
        uploadPlugin,
        isInstalled: (pluginId) => Boolean(installationByPlugin[pluginId]),
        isActionInProgress: (pluginId) => Boolean(actionByPlugin[pluginId]),
    };
}

