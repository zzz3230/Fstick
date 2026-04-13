package ru.fstick.registry_service.service;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import lombok.RequiredArgsConstructor;
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
import ru.fstick.registry_service.dto.service.FileDownloadData;
import ru.fstick.registry_service.dto.service.FileUploadData;
import ru.fstick.registry_service.dto.service.PaginationData;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.repository.PluginsRepositoryMock;
import ru.fstick.registry_service.util.Container;
import ru.fstick.registry_service.util.RuntimeParser;
import ru.fstick.registry_service.util.TypeParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PluginsService {

    private final PluginsRepositoryMock pluginsRepositoryMock;
    private final S3Service s3Service;


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


        commitPluginRequest.getKeys().forEach(key -> {
            String fileName = key.substring(key.lastIndexOf("/") + 1);
            byte[] file = s3Service.getObject(key);
            //VALIDATION TODO
        });

        PluginData pluginData = pluginsRepositoryMock.addPlugin(
                pluginId,
                commitPluginRequest.getName(),
                commitPluginRequest.getDescription(),
                commitPluginRequest.getCategory(),
                commitPluginRequest.getKeys(),
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
                    TypeParser.Result resultType = TypeParser.parse(file.getType());
                    String key = null;

                    if (resultType.container==Container.CODE) {
                        RuntimeParser.Result resultRuntime = RuntimeParser.parse(resultType.type);
                        key = "plugins/" + pluginId + "/versions/" + request.getVersion() + "/" + resultRuntime.getTarget() + "/" + resultRuntime.getLanguage() + "/" + resultRuntime.getVersion() + "/" + file.getFileName();
                    }
                    else if (resultType.container==Container.IMAGE) {
                        key = "plugins/" + pluginId + "/screenshots/" + file.getFileName();
                    }

                    if (key==null) {
                        throw new RuntimeException();
                    }

                    String url = s3Service.generateUploadUrl(key);

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

    public AddVersionResponse initPluginVersionUpload(UUID pluginId, AddPluginVersionRequest request) {
        UUID versionId = UUID.randomUUID();

        List<FileUploadData> uploads = request.getFiles().stream()
                .map(file -> {
                    TypeParser.Result resultType = TypeParser.parse(file.getType());
                    String key = null;

                    if (resultType.container==Container.CODE) {
                        RuntimeParser.Result resultRuntime = RuntimeParser.parse(resultType.type);
                        key = "plugins/" + pluginId + "/versions/" + request.getVersion() + "/" + resultRuntime.getTarget() + "/" + resultRuntime.getLanguage() + "/" + resultRuntime.getVersion() + "/" + file.getFileName();
                    }

                    if (key==null) {
                        throw new RuntimeException();
                    }

                    String url = s3Service.generateUploadUrl(key);

                    return FileUploadData.builder()
                            .fileName(file.getFileName())
                            .key(key)
                            .uploadUrl(url)
                            .build();
                })
                .toList();

        return AddVersionResponse.builder()
                .pluginId(pluginId)
                .versionId(versionId)
                .uploads(uploads)
                .build();
    }

    public PluginViewExtend commitVersion(UUID pluginId, CommitVersionRequest commitVersionRequest) {

        commitVersionRequest.getKeys().forEach(key -> {
            String fileName = key.substring(key.lastIndexOf("/") + 1);
            byte[] file = s3Service.getObject(key);
            //VALIDATION TODO
        });
        //TODO

        return null;
    }

    public UpdateAssetsResponse updateAssets(UUID pluginId, UpdateAssetsRequest request) {
        List<FileUploadData> uploads = request.getFiles().stream()
                .map(file -> {
                    TypeParser.Result resultType = TypeParser.parse(file.getType());
                    String key = null;

                    if (resultType.container==Container.IMAGE) {
                        key = "plugins/" + pluginId + "/screenshots/" + file.getFileName();
                    }

                    if (key==null) {
                        throw new RuntimeException();
                    }

                    String url = s3Service.generateUploadUrl(key);

                    return FileUploadData.builder()
                            .fileName(file.getFileName())
                            .key(key)
                            .uploadUrl(url)
                            .build();
                })
                .toList();

        return UpdateAssetsResponse.builder()
                .pluginId(pluginId)
                .uploads(uploads)
                .build();
    }

    public PluginViewExtend commitAssets(UUID pluginId, CommitAssetsRequest commitAssetsRequest) {

        commitAssetsRequest.getKeys().forEach(key -> {
            String fileName = key.substring(key.lastIndexOf("/") + 1);
            byte[] file = s3Service.getObject(key);
            //VALIDATION TODO
        });

        return null;
    }

    public void deleteAsset(UUID pluginId, UUID assetId) {
        String key = pluginsRepositoryMock.getAssetKey(assetId);

        s3Service.deleteAsset(key);
    }

    public ChangeStatusResponse changeStatus(UUID pluginId, Status status) {
        //TODO
        return null;
    }

    public CodeLinksResponse getPluginCodeClient(UUID pluginId, String version, String runtime) {
        List<String> keys = pluginsRepositoryMock.getCodeClient();

        List<FileDownloadData> downloads = keys.stream().map(key -> FileDownloadData.builder()
                .downloadUrl(s3Service.generateDownloadUrl(key))
                .build()).toList();


        return CodeLinksResponse.builder()
                .files(downloads)
                .build();
    }

    public CodeLinksResponse getPluginCodeServer(UUID pluginId, String version, String runtime) {
        List<String> keys = pluginsRepositoryMock.getServerClient();

        List<FileDownloadData> downloads = keys.stream().map(key -> FileDownloadData.builder()
                .downloadUrl(s3Service.generateDownloadUrl(key))
                .build()).toList();


        return CodeLinksResponse.builder()
                .files(downloads)
                .build();
    }
}
