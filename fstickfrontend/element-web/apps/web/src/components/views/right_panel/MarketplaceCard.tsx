/*
Copyright 2026 New Vector Ltd.
SPDX-License-Identifier: AGPL-3.0-only OR GPL-3.0-only OR LicenseRef-Element-Commercial
Please see LICENSE files in the repository root for full details.
*/

import React, { type JSX, useEffect, useMemo, useState } from "react";
import { Badge, Button, Form, Search, Separator, Text } from "@vector-im/compound-web";
import ExtensionsIcon from "@vector-im/compound-design-tokens/assets/web/icons/extensions";
import CheckIcon from "@vector-im/compound-design-tokens/assets/web/icons/check";
import DeleteIcon from "@vector-im/compound-design-tokens/assets/web/icons/delete";
import CloudIcon from "@vector-im/compound-design-tokens/assets/web/icons/cloud";
import FilesIcon from "@vector-im/compound-design-tokens/assets/web/icons/files";
import ImageIcon from "@vector-im/compound-design-tokens/assets/web/icons/image";
import CloseIcon from "@vector-im/compound-design-tokens/assets/web/icons/close";
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

function onDropFiles(setter: (files: File[]) => void): (e: React.DragEvent<HTMLLabelElement>) => void {
    return (e) => {
        e.preventDefault();
        setter(Array.from(e.dataTransfer.files ?? []));
    };
}

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

    const iconPreviewUrl = useMemo(() => (iconFile ? URL.createObjectURL(iconFile) : null), [iconFile]);
    useEffect(() => {
        return () => {
            if (iconPreviewUrl) URL.revokeObjectURL(iconPreviewUrl);
        };
    }, [iconPreviewUrl]);

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
                    <Text size="lg" weight="semibold" className="mx_MarketplaceCard_itemName">{plugin.name}</Text>
                    <div className="mx_MarketplaceCard_tags">
                        {plugin.tags?.map((tag) => (
                            <span className="mx_MarketplaceCard_tag" key={tag}>{tag}</span>
                        ))}
                    </div>
                </div>
                <div className="mx_MarketplaceCard_itemBody">
                    {plugin.description && (
                        <Text size="sm" className="mx_MarketplaceCard_description">{plugin.description}</Text>
                    )}
                    {isAdmin && (
                        <div className="mx_MarketplaceCard_itemActions">
                            {showInstall && !installed ? (
                                <Button size="lg" disabled={busy} onClick={() => void vm.installPlugin(plugin.id)}>
                                    {busy ? "Installing..." : "Install"}
                                </Button>
                            ) : (
                                <Button
                                    size="sm"
                                    kind="tertiary"
                                    destructive={!showInstall}
                                    Icon={showInstall ? CheckIcon : DeleteIcon}
                                    disabled={busy}
                                    onClick={() => void vm.uninstallPlugin(plugin.id)}
                                >
                                    {busy ? "Removing..." : showInstall ? "Installed" : "Remove"}
                                </Button>
                            )}
                        </div>
                    )}
                </div>
                <div className="mx_MarketplaceCard_itemMeta">
                    {plugin.author && (
                        <Text size="xs" className="mx_MarketplaceCard_itemAuthor">By {plugin.author}</Text>
                    )}
                    {plugin.version && <Text size="xs">v{plugin.version}</Text>}
                </div>
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
        marketplaceBody = (
            <EmptyState
                Icon={ExtensionsIcon}
                title="No plugins found"
                description="Try a different search term."
            />
        );
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
        installedBody = (
            <div className="mx_MarketplaceCard_empty">
                <EmptyState
                    Icon={ExtensionsIcon}
                    title="No plugins installed"
                    description="Plugins you install for this chat will appear here."
                />
                <Button kind="secondary" size="sm" onClick={() => setActiveTab("marketplace")}>
                    Browse marketplace
                </Button>
            </div>
        );
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
                <div className="mx_MarketplaceCard_tabsTrack">
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

                    <Form.Root className="mx_MarketplaceCard_uploadForm" onSubmit={(e) => e.preventDefault()}>
                        <div className="mx_MarketplaceCard_section">
                            <Text size="sm" weight="semibold">Basic details</Text>

                            <Form.Field name="pluginName">
                                <Form.Label>Name *</Form.Label>
                                <Form.TextControl
                                    value={name}
                                    onChange={(e) => setName(e.currentTarget.value)}
                                    placeholder="e.g. Meeting Notes"
                                />
                            </Form.Field>

                            <Form.Field name="pluginDescription">
                                <Form.Label>Description *</Form.Label>
                                <textarea
                                    className="mx_MarketplaceCard_textarea"
                                    value={description}
                                    onChange={(e) => setDescription(e.currentTarget.value)}
                                    placeholder="What does this plugin do?"
                                />
                            </Form.Field>

                            <label className="mx_MarketplaceCard_selectLabel">
                                <Text size="sm">Category *</Text>
                                <select
                                    value={category}
                                    onChange={(e) => setCategory(e.currentTarget.value)}
                                    className="mx_MarketplaceCard_select"
                                >
                                    <option value="Analytics">Analytics</option>
                                    <option value="Productivity">Productivity</option>
                                    <option value="Security">Security</option>
                                </select>
                            </label>

                            <Form.Field name="pluginTags">
                                <Form.Label>Tags</Form.Label>
                                <Form.TextControl
                                    value={tags}
                                    onChange={(e) => setTags(e.currentTarget.value)}
                                    placeholder="e.g. ai, summary, notes"
                                />
                            </Form.Field>
                            <Text size="sm" className="mx_MarketplaceCard_hint">Separate with commas</Text>
                        </div>

                        <div className="mx_MarketplaceCard_section">
                            <div className="mx_MarketplaceCard_sectionHeader">
                                <Text size="sm" weight="semibold">Server-side runtime</Text>
                                <Badge kind="grey">Required</Badge>
                            </div>

                            <Form.Field name="serverRuntime">
                                <Form.Label>Runtime</Form.Label>
                                <Form.TextControl
                                    value={serverRuntime}
                                    onChange={(e) => setServerRuntime(e.currentTarget.value)}
                                    placeholder="sv.lua@1.0.0"
                                />
                            </Form.Field>

                            {serverFiles.length > 0 && (
                                <div className="mx_MarketplaceCard_fileList">
                                    {serverFiles.map((file, index) => (
                                        <div className="mx_MarketplaceCard_fileChip" key={`${file.name}-${index}`}>
                                            <FilesIcon width="14px" height="14px" />
                                            <span className="mx_MarketplaceCard_fileChipName">{file.name}</span>
                                            <button
                                                type="button"
                                                className="mx_MarketplaceCard_fileChipRemove"
                                                onClick={() => setServerFiles((files) => files.filter((_, i) => i !== index))}
                                            >
                                                <CloseIcon width="12px" height="12px" />
                                            </button>
                                        </div>
                                    ))}
                                </div>
                            )}

                            <label
                                className="mx_MarketplaceCard_dropzone"
                                onDragOver={(e) => e.preventDefault()}
                                onDrop={onDropFiles(setServerFiles)}
                            >
                                <CloudIcon />
                                <Text size="sm" className="mx_MarketplaceCard_dropzoneText">Drop files or click to browse</Text>
                                <Text size="sm" className="mx_MarketplaceCard_dropzoneHint">Code files *</Text>
                                <input
                                    type="file"
                                    multiple
                                    onChange={(e) => setServerFiles(Array.from(e.currentTarget.files ?? []))}
                                />
                            </label>
                        </div>

                        <div className="mx_MarketplaceCard_section">
                            <div className="mx_MarketplaceCard_sectionHeader">
                                <Text size="sm" weight="semibold">Client-side runtime</Text>
                                <Badge kind="grey">Optional</Badge>
                            </div>

                            <Form.Field name="clientRuntime">
                                <Form.Label>Runtime</Form.Label>
                                <Form.TextControl
                                    value={clientRuntime}
                                    onChange={(e) => setClientRuntime(e.currentTarget.value)}
                                    placeholder="cl.js@1.0.0"
                                />
                            </Form.Field>

                            {clientFiles.length > 0 && (
                                <div className="mx_MarketplaceCard_fileList">
                                    {clientFiles.map((file, index) => (
                                        <div className="mx_MarketplaceCard_fileChip" key={`${file.name}-${index}`}>
                                            <FilesIcon width="14px" height="14px" />
                                            <span className="mx_MarketplaceCard_fileChipName">{file.name}</span>
                                            <button
                                                type="button"
                                                className="mx_MarketplaceCard_fileChipRemove"
                                                onClick={() => setClientFiles((files) => files.filter((_, i) => i !== index))}
                                            >
                                                <CloseIcon width="12px" height="12px" />
                                            </button>
                                        </div>
                                    ))}
                                </div>
                            )}

                            <label
                                className="mx_MarketplaceCard_dropzone"
                                onDragOver={(e) => e.preventDefault()}
                                onDrop={onDropFiles(setClientFiles)}
                            >
                                <CloudIcon />
                                <Text size="sm" className="mx_MarketplaceCard_dropzoneText">Drop files or click to browse</Text>
                                <Text size="sm" className="mx_MarketplaceCard_dropzoneHint">Code files</Text>
                                <input
                                    type="file"
                                    multiple
                                    onChange={(e) => setClientFiles(Array.from(e.currentTarget.files ?? []))}
                                />
                            </label>
                        </div>

                        <div className="mx_MarketplaceCard_section">
                            <Text size="sm" weight="semibold">Icon *</Text>
                            <div className="mx_MarketplaceCard_iconRow">
                                <label
                                    className="mx_MarketplaceCard_dropzone mx_MarketplaceCard_iconDropzone"
                                    onDragOver={(e) => e.preventDefault()}
                                    onDrop={(e) => {
                                        e.preventDefault();
                                        const file = e.dataTransfer.files?.[0];
                                        if (file) setIconFile(file);
                                    }}
                                >
                                    {iconPreviewUrl ? (
                                        <img className="mx_MarketplaceCard_iconPreview" src={iconPreviewUrl} alt="Icon preview" />
                                    ) : (
                                        <ImageIcon />
                                    )}
                                    <input
                                        type="file"
                                        accept="image/*"
                                        onChange={(e) => setIconFile(e.currentTarget.files?.[0] ?? null)}
                                    />
                                </label>
                                <Text size="sm" className="mx_MarketplaceCard_dropzoneHint">
                                    PNG or JPG, at least 128×128px, up to 2MB
                                </Text>
                            </div>
                        </div>
                    </Form.Root>

                    <div className="mx_MarketplaceCard_uploadFooter">
                        {vm.uploadError && <Text size="sm" className="mx_MarketplaceCard_uploadError">{vm.uploadError}</Text>}
                        {uploadHint && <Text size="sm" className="mx_MarketplaceCard_uploadSuccess">{uploadHint}</Text>}
                        <Button onClick={() => void onUploadClick()} disabled={!canUpload || vm.isUploading}>
                            {vm.isUploading ? "Uploading..." : "Upload plugin"}
                        </Button>
                    </div>
                </div>
            )}
        </BaseCard>
    );
};

export default MarketplaceCard;
