package ru.fstick.registry_service.service;

import org.springframework.stereotype.Service;
import ru.fstick.registry_service.dto.Status;
import ru.fstick.registry_service.dto.api.request.*;
import ru.fstick.registry_service.dto.api.request.commit.CommitAssetsRequest;
import ru.fstick.registry_service.dto.api.request.commit.CommitPluginRequest;
import ru.fstick.registry_service.dto.api.request.commit.CommitVersionRequest;
import ru.fstick.registry_service.dto.api.response.*;
import ru.fstick.registry_service.dto.api.view.*;
import ru.fstick.registry_service.dto.model.Screenshot;
import ru.fstick.registry_service.dto.model.Version;
import ru.fstick.registry_service.dto.service.FileUploadData;
import ru.fstick.registry_service.dto.service.PaginationData;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.repository.PluginsRepositoryMock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PluginsService {

    private final PluginsRepositoryMock pluginsRepositoryMock;
    private final S3Service s3Service;

    public PluginsService(PluginsRepositoryMock pluginsRepositoryMock, S3Service s3Service) {
        this.pluginsRepositoryMock = pluginsRepositoryMock;
        this.s3Service = s3Service;
    }

    //получить список плагинов и их пагинацию по критериям
    public PluginsView getPlugins(Integer page, Integer limit, String category, String search, String sort, String order) {
        Integer offset = page * limit;
        List<PluginData> pluginsData = pluginsRepositoryMock.getPlugins(offset, limit, category, search, sort, order);

        List<PluginViewShrink> pluginViewShrinks = new ArrayList<>();

        pluginsData.forEach(pluginData -> pluginViewShrinks.add(PluginViewShrink.builder()
                .id(pluginData.getId())
                .creatorId(pluginData.getCreatorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(pluginData.getIconUrlKey())
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .build()));


        Integer pluginsTotal = pluginsRepositoryMock.countPlugins(category, search);

        int totalPages = (int) Math.ceil((double) pluginsTotal / limit);

        PaginationData paginationData = PaginationData.builder()
                .page(page)
                .limit(limit)
                .total(pluginsTotal)
                .totalPages(totalPages)
                .hasNext(page<totalPages-1)
                .hasPrev(page>0)
                .build();

        return PluginsView.builder()
                .items(pluginViewShrinks)
                .pagination(paginationData)
                .build();
    }

    //получить плагин по id
    public PluginViewExtend getPlugin(UUID pluginId) {
        PluginData pluginData = pluginsRepositoryMock.getPlugin(pluginId);

        List<Version> versions = pluginsRepositoryMock.getVersionOfPlugin(pluginData.getId());
        List<Screenshot> screenshots = pluginsRepositoryMock.getScreenshots(pluginData.getId());


        List<VersionView> versionViews = new ArrayList<>();
        versions.forEach(version -> {versionViews.add(VersionView.builder()
                .version(version.getVersion())
                .changelog(version.getChangelog())
                .build());});

        List<ScreenshotView> screenshotViews = new ArrayList<>();
        screenshots.forEach(screenshot -> {ScreenshotView.builder()
                .screenshotId(screenshot.getScreenshotId())
                .screenshotUrl(screenshot.getS3ScreenshotKey())
                .build();});

        return PluginViewExtend.builder()
                .id(pluginData.getId())
                .creatorId(pluginData.getCreatorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(pluginData.getIconUrlKey())
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .versions(versionViews)
                .screenshots(screenshotViews)
                .build();
    }


    //обновить метаданные плагина
    public PluginViewExtend updatePlugin(UUID pluginId, PluginRequest pluginRequest) {
        PluginData pluginData = pluginsRepositoryMock.updatePlugin(
                pluginId,
                pluginRequest.getName(),
                pluginRequest.getDescription(),
                pluginRequest.getCategory(),
                pluginRequest.getTags());

        List<Version> versions = pluginsRepositoryMock.getVersionOfPlugin(pluginData.getId());
        List<Screenshot> screenshots = pluginsRepositoryMock.getScreenshots(pluginData.getId());

        List<VersionView> versionViews = new ArrayList<>();
        versions.forEach(version -> {versionViews.add(VersionView.builder()
                .version(version.getVersion())
                .changelog(version.getChangelog())
                .build());});

        List<ScreenshotView> screenshotViews = new ArrayList<>();
        screenshots.forEach(screenshot -> {ScreenshotView.builder()
                .screenshotId(screenshot.getScreenshotId())
                .screenshotUrl(screenshot.getS3ScreenshotKey())
                .build();});

        return PluginViewExtend.builder()
                .id(pluginData.getId())
                .creatorId(pluginData.getCreatorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(pluginData.getIconUrlKey())
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .versions(versionViews)
                .screenshots(screenshotViews)
                .build();
    }


    //Удалить плагин
    public ChangeStatusResponse deletePlugin(UUID pluginId) {
        PluginData pluginData = pluginsRepositoryMock.deletePlugin(pluginId);

        return ChangeStatusResponse.builder()
                .pluginId(pluginData.getId())
                .newStatus(Status.DELETED.getValue())
                .oldStatus(pluginData.getStatus())
                .build();
    }

    public PluginViewExtend commitPlugin(UUID pluginId, CommitPluginRequest commitPluginRequest) {

        PluginData pluginData = pluginsRepositoryMock.addPlugin(
                pluginId,
                commitPluginRequest.getName(),
                commitPluginRequest.getDescription(),
                commitPluginRequest.getCategory(),
                commitPluginRequest.getTags());

        List<Version> versions = pluginsRepositoryMock.getVersionOfPlugin(pluginData.getId());
        List<Screenshot> screenshots = pluginsRepositoryMock.getScreenshots(pluginData.getId());

        List<VersionView> versionViews = new ArrayList<>();
        versions.forEach(version -> {versionViews.add(VersionView.builder()
                .version(version.getVersion())
                .changelog(version.getChangelog())
                .build());});

        List<ScreenshotView> screenshotViews = new ArrayList<>();
        screenshots.forEach(screenshot -> {ScreenshotView.builder()
                .screenshotId(screenshot.getScreenshotId())
                .screenshotUrl(screenshot.getS3ScreenshotKey())
                .build();});

        return PluginViewExtend.builder()
                .id(pluginData.getId())
                .creatorId(pluginData.getCreatorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(pluginData.getIconUrlKey())
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .versions(versionViews)
                .screenshots(screenshotViews)
                .build();
    }

    public AddPluginResponse initPluginUpload(AddPluginRequest request) {
        UUID pluginId = UUID.randomUUID();

        List<FileUploadData> uploads = request.getFiles().stream()
                .map(file -> {

                    String key = "plugins/" + pluginId + "/" + UUID.randomUUID() + "_" + file.getFileName();

                    String url = s3Service.generateUrl(key);

                    return FileUploadData.builder()
                            .fileName(file.getFileName())
                            .key(key)
                            .uploadUrl(url)
                            .build();
                })
                .toList();

        return AddPluginResponse.builder()
                .pluginId(pluginId)
                .uploads(uploads)
                .build();
    }

    public AddVersionResponse addPluginVersion(UUID pluginId, AddPluginVersionRequest request) {
        //TODO
        return null;
    }

    public PluginViewExtend commitVersion(UUID pluginId, CommitVersionRequest request) {
        //TODO
        return null;
    }

    public UpdateAssetsResponse updateAssets(UUID pluginId, UpdateAssetsRequest request) {
        //TODO
        return null;
    }

    public PluginViewExtend commitAssets(UUID pluginId, CommitAssetsRequest request) {
        //TODO
        return null;
    }

    public void deleteAsset(UUID pluginId, UUID assetId) {
        //TODO
    }

    public ChangeStatusResponse changeStatus(UUID pluginId, Status status) {
        //TODO
        return null;
    }

    public CodeLinksResponse getPluginCodeClient(UUID pluginId, String version, String runtime) {
        //TODO
        return null;
    }

    public CodeLinksResponse getPluginCodeServer(UUID pluginId, String version, String runtime) {
        //TODO
        return null;
    }
}
