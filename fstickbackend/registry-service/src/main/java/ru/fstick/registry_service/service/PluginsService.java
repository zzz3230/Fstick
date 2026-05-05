package ru.fstick.registry_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fstick.registry_service.dto.Status;
import ru.fstick.registry_service.dto.api.request.*;
import ru.fstick.registry_service.dto.api.request.commit.CommitScreenshotsRequest;
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
import ru.fstick.registry_service.repository.PluginsRepository;
import ru.fstick.registry_service.util.Container;
import ru.fstick.registry_service.util.MinioKeyParser;
import ru.fstick.registry_service.util.RuntimeParser;
import ru.fstick.registry_service.util.TypeParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PluginsService {

    private final PluginsRepository pluginsRepository;
    private final S3Service s3Service;


    //получить список плагинов и их пагинацию по критериям

    @Transactional
    public PluginsView getPlugins(Integer page, Integer limit, String category, String search, String sort, String order) {
        Integer offset = page * limit;
        List<PluginData> pluginsData = pluginsRepository.getPlugins(offset, limit, category, search, sort, order);

        System.out.println(pluginsData);

        List<PluginViewShrink> pluginViewShrinks = new ArrayList<>();

        pluginsData.forEach(pluginData -> pluginViewShrinks.add(PluginViewShrink.builder()
                .id(pluginData.getId())
                .authorId(pluginData.getAuthorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(s3Service.generateDownloadUrl(pluginData.getIconUrlKey()))
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .build()));


        int pluginsTotal = pluginsRepository.getPluginsTotal(category, search);

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
    @Transactional
    public PluginViewExtend getPlugin(UUID pluginId) {
        PluginData pluginData = pluginsRepository.getPlugin(pluginId);

        List<Version> versions = pluginsRepository.getVersionsOfPlugin(pluginData.getId());
        List<Screenshot> screenshots = pluginsRepository.getScreenshots(pluginData.getId());

        List<VersionView> versionViews = new ArrayList<>();
        versions.forEach(version -> {versionViews.add(VersionView.builder()
                .version(version.getVersion())
                .changelog(version.getChangelog())
                .build());});

        List<ScreenshotView> screenshotViews = new ArrayList<>();
        screenshots.forEach(screenshot -> {screenshotViews.add(ScreenshotView.builder()
                .screenshotId(screenshot.getScreenshotId())
                .screenshotUrl(s3Service.generateDownloadUrl(screenshot.getS3ScreenshotKey()))
                .build());});

        String iconUrl = s3Service.generateDownloadUrl(pluginData.getIconUrlKey());

        return PluginViewExtend.builder()
                .id(pluginData.getId())
                .authorId(pluginData.getAuthorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(iconUrl)
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .versions(versionViews)
                .screenshots(screenshotViews)
                .build();
    }


    //обновить метаданные плагина
    @Transactional
    public PluginViewExtend updatePlugin(UUID pluginId, PluginRequest pluginRequest) {
        PluginData pluginData = pluginsRepository.updatePlugin(
                pluginId,
                pluginRequest.getName(),
                pluginRequest.getDescription(),
                pluginRequest.getCategory(),
                pluginRequest.getTags());

        List<Version> versions = pluginsRepository.getVersionsOfPlugin(pluginData.getId());
        List<Screenshot> screenshots = pluginsRepository.getScreenshots(pluginData.getId());

        List<VersionView> versionViews = new ArrayList<>();
        versions.forEach(version -> {versionViews.add(VersionView.builder()
                .version(version.getVersion())
                .changelog(version.getChangelog())
                .build());});

        List<ScreenshotView> screenshotViews = new ArrayList<>();
        screenshots.forEach(screenshot -> screenshotViews.add(ScreenshotView.builder()
                .screenshotId(screenshot.getScreenshotId())
                .screenshotUrl(s3Service.generateDownloadUrl(screenshot.getS3ScreenshotKey()))
                .build()));

        return PluginViewExtend.builder()
                .id(pluginData.getId())
                .authorId(pluginData.getAuthorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(s3Service.generateDownloadUrl(pluginData.getIconUrlKey()))
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .versions(versionViews)
                .screenshots(screenshotViews)
                .build();
    }


    //Удалить плагин
    @Transactional
    public ChangeStatusResponse deletePlugin(UUID pluginId) {
        // Получаем текущие данные плагина до изменения статуса
        PluginData before = pluginsRepository.getPlugin(pluginId);

        // Помечаем как удалённый
        PluginData after = pluginsRepository.deletePlugin(pluginId);

        return ChangeStatusResponse.builder()
                .pluginId(pluginId)
                .newStatus(after.getStatus())
                .oldStatus(before.getStatus())
                .build();
    }

    //подтвердить создание плагина
    @Transactional
    public PluginViewExtend commitPlugin(UUID pluginId, CommitPluginRequest commitPluginRequest) {

        commitPluginRequest.getKeys().forEach(key -> {
            String fileName = key.substring(key.lastIndexOf("/") + 1);
            byte[] file = s3Service.getObject(key);
            //VALIDATION TODO
        });

        PluginData pluginData = pluginsRepository.addFiles(
                pluginId,
                commitPluginRequest.getVersionId(),
                MinioKeyParser.getAllIcons(commitPluginRequest.getKeys()).get(0),
                MinioKeyParser.getAllScreenshots(commitPluginRequest.getKeys()),
                MinioKeyParser.getAllFiles(commitPluginRequest.getKeys()));

        List<Version> versions = pluginsRepository.getVersionsOfPlugin(pluginData.getId());
        List<Screenshot> screenshots = pluginsRepository.getScreenshots(pluginData.getId());

        List<VersionView> versionViews = new ArrayList<>();
        versions.forEach(version -> {versionViews.add(VersionView.builder()
                .version(version.getVersion())
                .changelog(version.getChangelog())
                .build());});

        List<ScreenshotView> screenshotViews = new ArrayList<>();
        screenshots.forEach(screenshot -> screenshotViews.add(ScreenshotView.builder()
                .screenshotId(screenshot.getScreenshotId())
                .screenshotUrl(s3Service.generateDownloadUrl(screenshot.getS3ScreenshotKey()))
                .build()));

        return PluginViewExtend.builder()
                .id(pluginData.getId())
                .authorId(pluginData.getAuthorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(s3Service.generateDownloadUrl(pluginData.getIconUrlKey()))
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .versions(versionViews)
                .screenshots(screenshotViews)
                .build();
    }

    @Transactional
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

        //генерация url для иконки
        //--------
        TypeParser.Result resultType = TypeParser.parse(request.getIcon().getType());
        String key = null;
        if (resultType.container==Container.IMAGE) {
            key = "plugins/" + pluginId + "/icon";
        }
        if (key==null) {
            throw new RuntimeException();
        }
        FileUploadData iconUpload = FileUploadData.builder()
                .fileName("icon")
                .key(key)
                .uploadUrl(s3Service.generateUploadUrl(key))
                .build();
        //--------

        pluginsRepository.addPlugin(pluginId, request.getName(), request.getDescription(), request.getCategory(), request.getTags(), request.getAuthorId());

        // Создаём первую версию плагина
        UUID versionId = pluginsRepository.createVersion(pluginId, request.getVersion(), "Initial release", request.getRuntime());

        return AddPluginResponse.builder()
                .pluginId(pluginId)
                .versionId(versionId)
                .uploads(uploads)
                .iconUpload(iconUpload)
                .build();
    }

    @Transactional
    public AddVersionResponse initPluginVersionUpload(UUID pluginId, AddPluginVersionRequest request) {
        UUID versionId = pluginsRepository.createVersion(pluginId, request.getVersion(), request.getChangelog(), request.getRuntime());

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

    @Transactional
    public PluginViewExtend commitVersion(UUID pluginId, CommitVersionRequest commitVersionRequest) {
        PluginData pluginData = pluginsRepository.addVersion(
                pluginId,
                commitVersionRequest.getVersion(),
                MinioKeyParser.getAllFiles(commitVersionRequest.getKeys()));

        List<Version> versions = pluginsRepository.getVersionsOfPlugin(pluginData.getId());
        List<Screenshot> screenshots = pluginsRepository.getScreenshots(pluginData.getId());

        List<VersionView> versionViews = new ArrayList<>();
        versions.forEach(version -> {versionViews.add(VersionView.builder()
                .version(version.getVersion())
                .changelog(version.getChangelog())
                .build());});

        List<ScreenshotView> screenshotViews = new ArrayList<>();
        screenshots.forEach(screenshot -> screenshotViews.add(ScreenshotView.builder()
                .screenshotId(screenshot.getScreenshotId())
                .screenshotUrl(s3Service.generateDownloadUrl(screenshot.getS3ScreenshotKey()))
                .build()));

        return PluginViewExtend.builder()
                .id(pluginData.getId())
                .authorId(pluginData.getAuthorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(s3Service.generateDownloadUrl(pluginData.getIconUrlKey()))
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .versions(versionViews)
                .screenshots(screenshotViews)
                .build();

    }

    @Transactional
    public UpdateScreenshotsResponse updateScreenshots(UUID pluginId, UpdateScreenshotsRequest request) {
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

        return UpdateScreenshotsResponse.builder()
                .pluginId(pluginId)
                .uploads(uploads)
                .build();
    }

    @Transactional
    public PluginViewExtend commitScreenshots(UUID pluginId, CommitScreenshotsRequest commitScreenshotsRequest) {

        PluginData pluginData = pluginsRepository.addScreenshots(
                pluginId,
                MinioKeyParser.getAllFiles(commitScreenshotsRequest.getKeys()));

        List<Version> versions = pluginsRepository.getVersionsOfPlugin(pluginData.getId());
        List<Screenshot> screenshots = pluginsRepository.getScreenshots(pluginData.getId());

        List<VersionView> versionViews = new ArrayList<>();
        versions.forEach(version -> {versionViews.add(VersionView.builder()
                .version(version.getVersion())
                .changelog(version.getChangelog())
                .build());});

        List<ScreenshotView> screenshotViews = new ArrayList<>();
        screenshots.forEach(screenshot -> screenshotViews.add(ScreenshotView.builder()
                .screenshotId(screenshot.getScreenshotId())
                .screenshotUrl(s3Service.generateDownloadUrl(screenshot.getS3ScreenshotKey()))
                .build()));

        return PluginViewExtend.builder()
                .id(pluginData.getId())
                .authorId(pluginData.getAuthorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginData.getTags())
                .status(pluginData.getStatus())
                .iconUrl(s3Service.generateDownloadUrl(pluginData.getIconUrlKey()))
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .versions(versionViews)
                .screenshots(screenshotViews)
                .build();
    }

    @Transactional
    public void deleteScreenshot(UUID pluginId, UUID screenshotId) {
        String key = pluginsRepository.getScreenshotKey(pluginId, screenshotId);

        s3Service.deleteScreenshot(key);
        pluginsRepository.deleteScreenshot(screenshotId);
    }

    @Transactional
    public ChangeStatusResponse changeStatus(UUID pluginId, Status status) {
        PluginData before = pluginsRepository.getPlugin(pluginId);
        pluginsRepository.changeStatus(pluginId, status);
        PluginData after = pluginsRepository.getPlugin(pluginId);
        return ChangeStatusResponse.builder()
                .pluginId(pluginId)
                .oldStatus(before.getStatus())
                .newStatus(after.getStatus())
                .build();
    }

    @Transactional
    public CodeLinksResponse getPluginCodeClient(UUID pluginId, String version, String runtime) {
        List<String> keys = pluginsRepository.getCode(pluginId, version, runtime);

        List<String> clientKeys = s3Service.getOnlyClientKeys(keys);


        List<FileDownloadData> downloads = clientKeys.stream().map(key -> FileDownloadData.builder()
                .downloadUrl(s3Service.generateDownloadUrl(key))
                .build()).toList();


        return CodeLinksResponse.builder()
                .files(downloads)
                .build();
    }

    @Transactional
    public CodeLinksResponse getPluginCodeServer(UUID pluginId, String version, String runtime) {
        List<String> keys = pluginsRepository.getCode(pluginId, version, runtime);

        List<String> serverKeys = s3Service.getOnlyServerKeys(keys);


        List<FileDownloadData> downloads = serverKeys.stream().map(key -> FileDownloadData.builder()
                .downloadUrl(s3Service.generateDownloadUrl(key))
                .build()).toList();


        return CodeLinksResponse.builder()
                .files(downloads)
                .build();
    }
}
