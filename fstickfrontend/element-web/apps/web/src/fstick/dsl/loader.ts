/*
Copyright 2025 New Vector Ltd.

SPDX-License-Identifier: AGPL-3.0-only OR GPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE files in the repository root for full details.
*/

const LOG = "[FStick:loader]";

export interface PluginEntry {
    installationId: string;
    pluginId: string;
    versionId: string;
    version: string;
    runtime: string;
    appCode: string;
    initialState: Record<string, unknown>;
}

interface Installation {
    installationId: string; // installation-service: camelCase
    pluginId: string;
    versionId: string;
}

interface PluginVersion {
    version_id: string; // registry-service: SNAKE_CASE
    version: string;
    runtime: string;
}

interface PluginDetails {
    id: string;
    versions: PluginVersion[];
}

interface CodeFile {
    download_url: string; // registry-service: SNAKE_CASE
}

interface CodeResponse {
    files: CodeFile[];
}

export async function loadPluginsForChat(
    apiBase: string,
    roomId: string,
    accessToken: string,
): Promise<PluginEntry[]> {
    console.log(`${LOG} loading plugins for room=${roomId} api=${apiBase}`);

    const headers = { Authorization: `Bearer ${accessToken}` };

    const installRes = await fetch(`${apiBase}/installations?chat_id=${encodeURIComponent(roomId)}`, { headers });
    if (!installRes.ok) {
        console.warn(`${LOG} failed to fetch installations (${installRes.status})`);
        return [];
    }
    const installBody: unknown = await installRes.json();
    // API may return a plain array OR { data: [...] } wrapper
    const installations: Installation[] = Array.isArray(installBody)
        ? (installBody as Installation[])
        : Array.isArray((installBody as Record<string, unknown>)?.data)
          ? ((installBody as Record<string, unknown>).data as Installation[])
          : [];
    if (installations.length === 0) {
        console.log(`${LOG} no installations found for room=${roomId}`);
        return [];
    }
    console.log(`${LOG} found ${installations.length} installation(s):`, installations.map((i) => i.pluginId));

    const entries: PluginEntry[] = [];

    for (const inst of installations) {
        console.log(`${LOG} loading plugin pluginId=${inst.pluginId} installationId=${inst.installationId}`);
        try {
            const pluginRes = await fetch(`${apiBase}/registry/plugins/${inst.pluginId}`, { headers });
            if (!pluginRes.ok) {
                console.warn(`${LOG} plugin details not found for pluginId=${inst.pluginId} (${pluginRes.status})`);
                continue;
            }
            const plugin: PluginDetails = await pluginRes.json();

            const versionObj =
                plugin.versions?.find((v) => v.version_id === inst.versionId) ?? plugin.versions?.[0];
            if (!versionObj) {
                console.warn(`${LOG} no version found for pluginId=${inst.pluginId}`);
                continue;
            }
            console.log(`${LOG} resolved version=${versionObj.version} runtime=${versionObj.runtime}`);

            const runtimeParam =
                versionObj.runtime != null && versionObj.runtime !== "undefined" && versionObj.runtime !== ""
                    ? `&runtime=${encodeURIComponent(versionObj.runtime)}`
                    : "";
            const codeRes = await fetch(
                `${apiBase}/registry/plugins/${inst.pluginId}/code/client?version=${encodeURIComponent(versionObj.version)}${runtimeParam}`,
                { headers },
            );
            if (!codeRes.ok) {
                console.warn(`${LOG} client code links not found for pluginId=${inst.pluginId} (${codeRes.status})`);
                continue;
            }
            const codeJson: CodeResponse = await codeRes.json();
            const downloadUrl = codeJson.files?.[0]?.download_url;
            if (!downloadUrl) {
                console.warn(`${LOG} no downloadUrl in code response for pluginId=${inst.pluginId}`);
                continue;
            }
            console.log(`${LOG} downloading client code from ${downloadUrl}`);

            // Download URL points to S3/MinIO — no auth header needed
            const srcRes = await fetch(downloadUrl);
            if (!srcRes.ok) {
                console.warn(`${LOG} failed to download client code for pluginId=${inst.pluginId} (${srcRes.status})`);
                continue;
            }
            const appCode = await srcRes.text();
            console.log(`${LOG} client code loaded (${appCode.length} chars) for pluginId=${inst.pluginId}`);

            // Fetch initial state from runtime
            let initialState: Record<string, unknown> = {};
            try {
                const stateRes = await fetch(
                    `${apiBase}/plugins/${inst.pluginId}/state?chat_id=${encodeURIComponent(roomId)}`,
                    { headers },
                );
                if (stateRes.ok) {
                    const stateJson = await stateRes.json();
                    if (stateJson && typeof stateJson === "object") {
                        initialState = stateJson as Record<string, unknown>;
                        console.log(`${LOG} initial state loaded for pluginId=${inst.pluginId}:`, initialState);
                    }
                } else {
                    console.log(`${LOG} no initial state available for pluginId=${inst.pluginId}`);
                }
            } catch {
                console.log(`${LOG} initial state fetch failed for pluginId=${inst.pluginId}, using empty state`);
            }

            entries.push({
                installationId: inst.installationId,
                pluginId: inst.pluginId,
                versionId: versionObj.version_id,
                version: versionObj.version,
                runtime: versionObj.runtime,
                appCode,
                initialState,
            });
            console.log(`${LOG} ✓ plugin ready: pluginId=${inst.pluginId}`);
        } catch (err) {
            console.error(`${LOG} unexpected error loading pluginId=${inst.pluginId}:`, err);
        }
    }

    console.log(`${LOG} finished: ${entries.length}/${installations.length} plugin(s) loaded`);
    return entries;
}
