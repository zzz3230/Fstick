/*
Copyright 2026 New Vector Ltd.
SPDX-License-Identifier: AGPL-3.0-only OR GPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE files in the repository root for full details.
*/

import React, { type JSX, useMemo, useState } from "react";
import { Button, Form, Search, Separator, Text } from "@vector-im/compound-web";
import ExtensionsIcon from "@vector-im/compound-design-tokens/assets/web/icons/extensions";
import { type Room } from "matrix-js-sdk/src/matrix";

import BaseCard from "./BaseCard";
import EmptyState from "./EmptyState";
import { useMatrixClientContext } from "../../../contexts/MatrixClientContext";
import {
    useMarketplaceCardViewModel,
    type CodeFileEntry,
    type MarketplacePlugin,
} from "../../viewmodels/right_panel/MarketplaceCardViewModel";

interface Props {
    room: Room;
    onClose(this: void): void;
}

type TabId = "marketplace" | "installed" | "upload";

const MarketplaceCard: React.FC<Props> = ({ room, onClose }) => {
    const cli = useMatrixClientContext();
    const userId = cli.getSafeUserId();
    const vm = useMarketplaceCardViewModel(room.roomId, userId);

    const isAdmin = (room.getMember(userId)?.powerLevel ?? 0) >= 50;

    const [activeTab, setActiveTab] = useState<TabId>("marketplace");

    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [version, setVersion] = useState("1.0.0");
    const [category, setCategory] = useState("Analytics");
    const [tags, setTags] = useState("");
    const [serverRuntime, setServerRuntime] = useState("sv.lua@1.0.0");
    const [clientRuntime, setClientRuntime] = useState("cl.js@1.0.0");
    const [serverFiles, setServerFiles] = useState<File[]>([]);
    const [clientFiles, setClientFiles] = useState<File[]>([]);
    const [iconFile, setIconFile] = useState<File | null>(null);
    const [uploadHint, setUploadHint] = useState<string | null>(null);

    const allCodeFiles = useMemo<CodeFileEntry[]>(
        () => [
            ...serverFiles.map((f) => ({ file: f, runtime: serverRuntime })),
            ...clientFiles.map((f) => ({ file: f, runtime: clientRuntime })),
        ],
        [serverFiles, clientFiles, serverRuntime, clientRuntime],
    );

    const canUpload = useMemo(
        () => Boolean(name.trim() && description.trim() && version.trim() && iconFile && serverFiles.length),
        [name, description, version, iconFile, serverFiles.length],
    );

    const onUploadClick = async (): Promise<void> => {
        if (!iconFile) return;
        setUploadHint(null);
        try {
            await vm.uploadPlugin({
                name: name.trim(),
                description: description.trim(),
                runtime: serverRuntime,
                version: version.trim(),
                category: category.trim(),
                tags,
                codeFiles: allCodeFiles,
                iconFile,
            });
            setUploadHint("Plugin uploaded successfully.");
            setName(""); setDescription(""); setVersion("1.0.0");
            setCategory(""); setTags("");
            setServerFiles([]); setClientFiles([]); setIconFile(null);
        } catch (err) {
            setUploadHint(err instanceof Error ? err.message : "Upload failed");
        }
    };

    const renderPluginCard = (
        plugin: MarketplacePlugin,
        showInstall: boolean,
    ): JSX.Element => {
        const busy = vm.isActionInProgress(plugin.id);
        const installed = vm.isInstalled(plugin.id);
        return (
            <article className="mx_MarketplaceCard_item" key={plugin.id}>
                <div className="mx_MarketplaceCard_itemHeader">
                    <div className="mx_MarketplaceCard_iconWrapper">
                        {plugin.iconUrl ? (
                            <img
                                className="mx_MarketplaceCard_icon"
                                src={plugin.iconUrl}
                                alt={plugin.name}
                                loading="lazy"
                            />
                        ) : (
                            <ExtensionsIcon className="mx_MarketplaceCard_iconFallback" />
                        )}
                    </div>
                    <div className="mx_MarketplaceCard_itemInfo">
                        <Text size="md" weight="semibold">{plugin.name}</Text>
                        <div className="mx_MarketplaceCard_itemMeta">
                            {plugin.author && <Text size="sm">By {plugin.author}</Text>}
                            {plugin.version && <Text size="sm">v{plugin.version}</Text>}
                        </div>
                    </div>
                </div>
                {plugin.description && (
                    <Text size="sm" className="mx_MarketplaceCard_description">{plugin.description}</Text>
                )}
                {plugin.tags?.length ? <Text size="sm">Tags: {plugin.tags.join(", ")}</Text> : null}
                {isAdmin && (
                    <div className="mx_MarketplaceCard_itemActions">
                        {showInstall && !installed ? (
                            <Button disabled={busy} onClick={() => void vm.installPlugin(plugin.id)}>
                                {busy ? "Installing..." : "Install in chat"}
                            </Button>
                        ) : (
                            <Button kind="secondary" disabled={busy} onClick={() => void vm.uninstallPlugin(plugin.id)}>
                                {busy ? "Removing..." : "Remove from chat"}
                            </Button>
                        )}
                    </div>
                )}
            </article>
        );
    };

    let marketplaceBody: JSX.Element;
    if (!vm.hasEndpoint) {
        marketplaceBody = (
            <EmptyState
                Icon={ExtensionsIcon}
                title="Plugins not connected"
                description="Configure the Fstick marketplace API endpoint to show plugins here."
            />
        );
    } else if (vm.isLoading && vm.plugins.length === 0) {
        marketplaceBody = <Text size="sm">Loading plugins...</Text>;
    } else if (vm.error && vm.plugins.length === 0) {
        marketplaceBody = (
            <div className="mx_MarketplaceCard_errorState">
                <Text size="sm">{vm.error}</Text>
                <Button kind="secondary" onClick={vm.retry}>Retry</Button>
            </div>
        );
    } else if (vm.plugins.length === 0) {
        marketplaceBody = <Text size="sm">No plugins matched your search.</Text>;
    } else {
        marketplaceBody = (
            <div className="mx_MarketplaceCard_list">
                {vm.plugins.map((p) => renderPluginCard(p, true))}
            </div>
        );
    }

    let installedBody: JSX.Element;
    if (!vm.hasEndpoint) {
        installedBody = (
            <EmptyState
                Icon={ExtensionsIcon}
                title="Plugins not connected"
                description="Configure the Fstick marketplace API endpoint to manage installed plugins."
            />
        );
    } else if (vm.isLoading && vm.installedPlugins.length === 0) {
        installedBody = <Text size="sm">Loading installed plugins...</Text>;
    } else if (vm.installedPlugins.length === 0) {
        installedBody = <Text size="sm">No plugins installed in this chat yet.</Text>;
    } else {
        installedBody = (
            <div className="mx_MarketplaceCard_list">
                {vm.installedPlugins.map((p) => renderPluginCard(p, false))}
            </div>
        );
    }

    return (
        <BaseCard header="Plugins" className="mx_MarketplaceCard" onClose={onClose}>
            <div className="mx_MarketplaceCard_tabs">
                {(["marketplace", "installed", "upload"] as TabId[]).map((tab) => (
                    <button
                        key={tab}
                        className={`mx_MarketplaceCard_tab${activeTab === tab ? " mx_MarketplaceCard_tab_active" : ""}`}
                        onClick={() => setActiveTab(tab)}
                    >
                        {tab === "marketplace" ? "Marketplace" : tab === "installed" ? "Installed" : "Upload"}
                    </button>
                ))}
            </div>

            <Separator />

            {activeTab === "marketplace" && (
                <>
                    <Form.Root className="mx_MarketplaceCard_search" onSubmit={(e) => e.preventDefault()}>
                        <Search
                            placeholder="Search plugins"
                            name="marketplace_search"
                            value={vm.query}
                            onChange={(e) => vm.onQueryChange(e.currentTarget.value)}
                            disabled={!vm.hasEndpoint}
                        />
                    </Form.Root>
                    {vm.error && vm.hasEndpoint && vm.plugins.length > 0 && (
                        <Text size="sm" className="mx_MarketplaceCard_warning">{vm.error}</Text>
                    )}
                    {marketplaceBody}
                </>
            )}

            {activeTab === "installed" && installedBody}

            {activeTab === "upload" && (
                <div className="mx_MarketplaceCard_upload">
                    <Text size="sm" className="mx_MarketplaceCard_uploadDescription">
                        Upload a new plugin to the system registry. Fields marked * are required.
                    </Text>
                    <input value={name} onChange={(e) => setName(e.currentTarget.value)} placeholder="Plugin name *" />
                    <input value={description} onChange={(e) => setDescription(e.currentTarget.value)} placeholder="Description *" />
                    <input value={tags} onChange={(e) => setTags(e.currentTarget.value)} placeholder="Tags (comma separated)" />

                    <label className="mx_MarketplaceCard_selectLabel">
                        <Text size="sm">Category *</Text>
                        <select value={category} onChange={(e) => setCategory(e.currentTarget.value)} className="mx_MarketplaceCard_select">
                            <option value="Analytics">Analytics</option>
                            <option value="Productivity">Productivity</option>
                            <option value="Security">Security</option>
                        </select>
                    </label>
                    <input value={tags} onChange={(e) => setTags(e.currentTarget.value)} placeholder="Tags (comma separated)" />

                    <div className="mx_MarketplaceCard_fileGroup">
                        <Text size="sm" weight="semibold">Server-side code *</Text>
                        <input
                            value={serverRuntime}
                            onChange={(e) => setServerRuntime(e.currentTarget.value)}
                            placeholder="Runtime (e.g. sv.lua@1.0.0)"
                        />
                        <label className="mx_MarketplaceCard_fileLabel">
                            <Text size="sm">Code files *</Text>
                            <input type="file" multiple onChange={(e) => setServerFiles(Array.from(e.currentTarget.files ?? []))} />
                        </label>
                    </div>

                    <div className="mx_MarketplaceCard_fileGroup">
                        <Text size="sm" weight="semibold">Client-side code (optional)</Text>
                        <input
                            value={clientRuntime}
                            onChange={(e) => setClientRuntime(e.currentTarget.value)}
                            placeholder="Runtime (e.g. cl.js@1.0.0)"
                        />
                        <label className="mx_MarketplaceCard_fileLabel">
                            <Text size="sm">Code files</Text>
                            <input type="file" multiple onChange={(e) => setClientFiles(Array.from(e.currentTarget.files ?? []))} />
                        </label>
                    </div>

                    <label className="mx_MarketplaceCard_fileLabel">
                        <Text size="sm">Icon *</Text>
                        <input type="file" accept="image/*" onChange={(e) => setIconFile(e.currentTarget.files?.[0] ?? null)} />
                    </label>

                    <Button onClick={() => void onUploadClick()} disabled={!canUpload || vm.isUploading}>
                        {vm.isUploading ? "Uploading..." : "Upload plugin"}
                    </Button>
                    {vm.uploadError && <Text size="sm" className="mx_MarketplaceCard_uploadError">{vm.uploadError}</Text>}
                    {uploadHint && <Text size="sm" className="mx_MarketplaceCard_uploadSuccess">{uploadHint}</Text>}
                </div>
            )}
        </BaseCard>
    );
};

export default MarketplaceCard;







