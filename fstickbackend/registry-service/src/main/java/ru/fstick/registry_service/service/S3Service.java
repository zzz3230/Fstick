package ru.fstick.registry_service.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.fstick.registry_service.config.MinioProperties;

import java.io.ByteArrayInputStream;

@Service
public class S3Service {

    private static final Logger log = LoggerFactory.getLogger(S3Service.class);

    private final MinioClient minioClient;
    private final MinioClient presignMinioClient;
    private final MinioClient presignMinioClientInternal;
    private final MinioProperties props;

    public S3Service(
            MinioClient minioClient,
            @Qualifier("presignMinioClient") MinioClient presignMinioClient,
            @Qualifier("presignMinioClientInternal") MinioClient presignMinioClientInternal,
            MinioProperties props) {
        this.minioClient = minioClient;
        this.presignMinioClient = presignMinioClient;
        this.presignMinioClientInternal = presignMinioClientInternal;
        this.props = props;
    }

    public String generateUploadUrl(String key) {
        log.info("[S3] generateUploadUrl called");
        log.info("[S3] bucket = {}", props.getBucket());
        log.info("[S3] key = {}", key);

        try {
            String url = presignMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(props.getBucket())
                            .object(key)
                            .expiry(60 * 10)
                            .build()
            );

            log.info("[S3] UPLOAD presigned URL generated");
            log.info("[S3] key = {}", key);
            log.debug("[S3] upload url = {}", url);

            return url;

        } catch (Exception e) {
            log.error("[S3] ERROR generating upload URL for key={}", key, e);
            throw new RuntimeException(e);
        }
    }

    public String generateDownloadUrl(String key, boolean isPublic) {
        log.info("[S3] generateDownloadUrl called");
        log.info("[S3] bucket = {}", props.getBucket());
        log.info("[S3] key = [{}]", key);

        if (key == null || key.isBlank()) {
            log.warn("[S3] EMPTY KEY passed to generateDownloadUrl");
            return null;
        }

        try {
            String url = (isPublic ? presignMinioClient : presignMinioClientInternal)
                    .getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(props.getBucket())
                            .object(key)
                            .expiry(60 * 10)
                            .build()
            );

            log.info("[S3] DOWNLOAD presigned URL generated");
            log.info("[S3] key = [{}]", key);
            log.info("[S3] url = {}", url);

            return url;

        } catch (Exception e) {
            log.error("[S3] ERROR generating download URL for key={}", key, e);
            throw new RuntimeException(
                    "Failed to generate presigned URL for object: " + key, e
            );
        }
    }

    public byte[] getObject(String key) {
        log.info("[S3] getObject called");
        log.info("[S3] key = [{}]", key);

        try (GetObjectResponse stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(props.getBucket())
                        .object(key)
                        .build()
        )) {

            byte[] data = stream.readAllBytes();

            log.info("[S3] object downloaded successfully");
            log.info("[S3] key = [{}], size = {}", key, data.length);

            return data;

        } catch (Exception e) {
            log.error("[S3] ERROR reading object key={}", key, e);
            throw new RuntimeException("Failed to read object: " + key, e);
        }
    }

    public void deleteScreenshot(String key) {
        log.info("[S3] deleteScreenshot called key={}", key);

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(props.getBucket())
                            .object(key)
                            .build()
            );

            log.info("[S3] object deleted key={}", key);

        } catch (Exception e) {
            log.error("[S3] ERROR deleting object key={}", key, e);
            throw new RuntimeException(e);
        }
    }

    public boolean exists(String key) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(key)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            String code = e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                return false;
            }
            throw new RuntimeException("Failed to stat object: " + key, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to stat object: " + key, e);
        }
    }

    public void putObject(String key, byte[] data, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(key)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to put object: " + key, e);
        }
    }
}
