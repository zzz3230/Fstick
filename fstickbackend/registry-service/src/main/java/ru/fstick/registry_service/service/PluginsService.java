package ru.fstick.registry_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fstick.registry_service.dto.Status;
import ru.fstick.registry_service.dto.api.request.*;
import ru.fstick.registry_service.dto.api.request.commit.CommitAssetsRequest;
import ru.fstick.registry_service.dto.api.response.*;
import ru.fstick.registry_service.dto.api.view.*;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.dto.model.Screenshot;
import ru.fstick.registry_service.dto.service.FileUploadData;
import ru.fstick.registry_service.dto.service.IconUploadData;
import ru.fstick.registry_service.dto.service.PaginationData;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.repository.BranchRepository;
import ru.fstick.registry_service.repository.PluginsRepository;
import ru.fstick.registry_service.util.Container;
import ru.fstick.registry_service.util.KeyType;
import ru.fstick.registry_service.util.MinioKeyParser;
import ru.fstick.registry_service.util.TypeParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PluginsService {

    private final PluginsRepository pluginsRepository;
    private final BranchRepository branchRepository;
    private final S3Service s3Service;
    private final BlobStore blobStore;
    private final TemplateProvider templateProvider;
    private final AccessGuard accessGuard;

    //получить список плагинов и их пагинацию по критериям

    @Transactional
    public PluginsView<PluginViewShrink> getPlugins(Integer page, Integer limit, String category, String search, String sort, String order) {
        List<PluginData> pluginsData = pluginsRepository.getPlugins(page * limit, limit, category, search, sort, order, null);

        List<PluginViewShrink> items = pluginsData.stream().map(pluginData -> PluginViewShrink.builder()
                .id(pluginData.getId())
                .authorId(pluginData.getAuthorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(iconUrl(pluginData))
                .releasedSemver(pluginData.getReleasedSemver())
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .build()).toList();

        int pluginsTotal = pluginsRepository.getPluginsTotal(category, search, null);

        return PluginsView.<PluginViewShrink>builder()
                .items(items)
                .pagination(pagination(page, limit, pluginsTotal))
                .build();
    }

    @Transactional
    public PluginsView<PluginViewOwned> getOwnedPlugins(UUID caller, Integer page, Integer limit, String category, String search, String sort, String order) {
        List<PluginData> pluginsData = pluginsRepository.getPlugins(page * limit, limit, category, search, sort, order, caller);

        List<PluginViewOwned> items = pluginsData.stream().map(pluginData -> PluginViewOwned.builder()
                .id(pluginData.getId())
                .authorId(pluginData.getAuthorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(iconUrl(pluginData))
                .devBranchId(pluginData.getDevBranchId())
                .releasedSemver(pluginData.getReleasedSemver())
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .build()).toList();

        int pluginsTotal = pluginsRepository.getPluginsTotal(category, search, caller);

        return PluginsView.<PluginViewOwned>builder()
                .items(items)
                .pagination(pagination(page, limit, pluginsTotal))
                .build();
    }

    //получить плагин по id
    @Transactional
    public PluginViewExtend getPlugin(UUID pluginId, UUID caller, String chatId) {
        PluginData pluginData = pluginsRepository.findPlugin(pluginId)
                .filter(plugin -> !Status.DELETED.getValue().equals(plugin.getStatus()))
                .orElseThrow(() -> pluginNotFound(pluginId));

        List<Branch> branches = accessGuard.visibleBranches(caller, pluginData, branchRepository.findByPlugin(pluginId), chatId);
        if (branches.isEmpty()) {
            throw pluginNotFound(pluginId);
        }

        return extendView(pluginData, branches);
    }


    //обновить метаданные плагина
    @Transactional
    public PluginViewExtend updatePlugin(UUID pluginId, UUID caller, PluginRequest pluginRequest) {
        accessGuard.requireAuthor(pluginId, caller);

        PluginData pluginData = pluginsRepository.updatePlugin(
                pluginId,
                pluginRequest.getName(),
                pluginRequest.getDescription(),
                pluginRequest.getCategory(),
                pluginRequest.getTags());

        return extendView(pluginData, branchRepository.findByPlugin(pluginId));
    }


    //Удалить плагин
    @Transactional
    public ChangeStatusResponse deletePlugin(UUID pluginId, UUID caller) {
        // Получаем текущие данные плагина до изменения статуса
        PluginData before = accessGuard.requireAuthor(pluginId, caller);

        // Помечаем как удалённый
        PluginData after = pluginsRepository.deletePlugin(pluginId);

        return ChangeStatusResponse.builder()
                .pluginId(pluginId)
                .newStatus(after.getStatus())
                .oldStatus(before.getStatus())
                .build();
    }

    @Transactional
    public AddPluginResponse createPlugin(AddPluginRequest request, UUID authorId) {
        UUID pluginId = UUID.randomUUID();

        String iconKey = null;
        if (request.getIcon() != null) {
            iconKey = imageKey(request.getIcon().getType(), "plugins/" + pluginId + "/icon");
        }

        pluginsRepository.addPlugin(pluginId, request.getName(), request.getDescription(), request.getCategory(), request.getTags(), authorId);

        String clientSha = blobStore.put(pluginId.toString(), templateProvider.getClientCode());
        String serverSha = blobStore.put(pluginId.toString(), templateProvider.getServerCode());
        UUID devBranchId = branchRepository.createDevBranch(pluginId, clientSha, serverSha);

        IconUploadData iconUpload = iconKey == null ? null : IconUploadData.builder()
                .key(iconKey)
                .uploadUrl(s3Service.generateUploadUrl(iconKey))
                .build();

        return AddPluginResponse.builder()
                .pluginId(pluginId)
                .devBranchId(devBranchId)
                .iconUpload(iconUpload)
                .build();
    }

    @Transactional
    public UpdateScreenshotsResponse updateScreenshots(UUID pluginId, UUID caller, UpdateScreenshotsRequest request) {
        accessGuard.requireAuthor(pluginId, caller);

        List<FileUploadData> uploads = request.getFiles().stream()
                .map(file -> {
                    String key = imageKey(file.getType(), "plugins/" + pluginId + "/screenshots/" + file.getFileName());

                    return FileUploadData.builder()
                            .fileName(file.getFileName())
                            .key(key)
                            .uploadUrl(s3Service.generateUploadUrl(key))
                            .build();
                })
                .toList();

        return UpdateScreenshotsResponse.builder()
                .pluginId(pluginId)
                .uploads(uploads)
                .build();
    }

    @Transactional
    public PluginViewExtend commitAssets(UUID pluginId, UUID caller, CommitAssetsRequest request) {
        accessGuard.requireAuthor(pluginId, caller);

        String prefix = "plugins/" + pluginId + "/";
        String iconKey = null;
        List<String> screenshotKeys = new ArrayList<>();

        for (String key : request.getKeys()) {
            KeyType type = MinioKeyParser.resolveType(key);
            boolean valid = key.startsWith(prefix)
                    && (type == KeyType.SCREENSHOT || (type == KeyType.ICON && key.equals(prefix + "icon")));
            if (!valid) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_key", "Key is not an icon or screenshot of this plugin: " + key);
            }
            if (!s3Service.exists(key)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "object_not_found", "Object was not uploaded: " + key);
            }
            if (type == KeyType.ICON) {
                iconKey = key;
            } else {
                screenshotKeys.add(key);
            }
        }

        if (iconKey != null) {
            pluginsRepository.setIcon(pluginId, iconKey);
        }
        PluginData pluginData = pluginsRepository.addScreenshots(pluginId, screenshotKeys);

        return extendView(pluginData, branchRepository.findByPlugin(pluginId));
    }

    @Transactional
    public void deleteScreenshot(UUID pluginId, UUID caller, UUID screenshotId) {
        accessGuard.requireAuthor(pluginId, caller);

        String key;
        try {
            key = pluginsRepository.getScreenshotKey(pluginId, screenshotId);
        } catch (EmptyResultDataAccessException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "screenshot_not_found", "Screenshot not found: " + screenshotId);
        }

        s3Service.deleteScreenshot(key);
        pluginsRepository.deleteScreenshot(screenshotId);
    }

    @Transactional
    public ChangeStatusResponse changeStatus(UUID pluginId, UUID caller, Status status) {
        PluginData before = accessGuard.requireAuthor(pluginId, caller);
        if (status == null || status == Status.ARCHIVED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_status", "Status must be ACTIVE, HIDDEN or DELETED");
        }

        pluginsRepository.changeStatus(pluginId, status);
        PluginData after = pluginsRepository.getPlugin(pluginId);
        return ChangeStatusResponse.builder()
                .pluginId(pluginId)
                .oldStatus(before.getStatus())
                .newStatus(after.getStatus())
                .build();
    }

    private PluginViewExtend extendView(PluginData pluginData, List<Branch> branches) {
        List<ScreenshotView> screenshotViews = pluginsRepository.getScreenshots(pluginData.getId()).stream()
                .map(this::screenshotView)
                .toList();

        return PluginViewExtend.builder()
                .id(pluginData.getId())
                .authorId(pluginData.getAuthorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(iconUrl(pluginData))
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .branches(branches.stream().map(PluginsService::branchView).toList())
                .screenshots(screenshotViews)
                .build();
    }

    private static BranchView branchView(Branch branch) {
        return BranchView.builder()
                .id(branch.getId())
                .status(branch.getStatus().name())
                .semver(branch.getSemver())
                .runtime(BranchView.RuntimeView.builder()
                        .client(branch.getRuntimeClient())
                        .server(branch.getRuntimeServer())
                        .build())
                .createdAt(branch.getCreatedAt())
                .build();
    }

    private ScreenshotView screenshotView(Screenshot screenshot) {
        return ScreenshotView.builder()
                .screenshotId(screenshot.getScreenshotId())
                .screenshotUrl(s3Service.generateDownloadUrl(screenshot.getS3ScreenshotKey(), true))
                .build();
    }

    private String iconUrl(PluginData pluginData) {
        String key = pluginData.getIconUrlKey() != null ? pluginData.getIconUrlKey() : TemplateProvider.ICON_KEY;
        return s3Service.generateDownloadUrl(key, true);
    }

    private static String imageKey(String type, String key) {
        try {
            if (TypeParser.parse(type).container == Container.IMAGE) {
                return key;
            }
        } catch (IllegalArgumentException e) {
            throw invalidType(type);
        }
        throw invalidType(type);
    }

    private static ApiException invalidType(String type) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_type", "Expected an image type, got: " + type);
    }

    private static PaginationData pagination(int page, int limit, int total) {
        int totalPages = (int) Math.ceil((double) total / limit);

        return PaginationData.builder()
                .page(page)
                .limit(limit)
                .total(total)
                .totalPages(totalPages)
                .hasNext(page < totalPages - 1)
                .hasPrev(page > 0)
                .build();
    }

    private static ApiException pluginNotFound(UUID pluginId) {
        return new ApiException(HttpStatus.NOT_FOUND, "plugin_not_found", "Plugin not found: " + pluginId);
    }
}
