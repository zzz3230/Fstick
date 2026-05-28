package ru.fstick.registry_service.service;

import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.fstick.registry_service.config.MinioProperties;

import java.util.List;

@Service
public class S3Service {

    private static final Logger log = LoggerFactory.getLogger(S3Service.class);

    private final MinioClient minioClient;
    private final MinioClient presignMinioClient;
    private final MinioProperties props;

    public S3Service(
            MinioClient minioClient,
            @Qualifier("presignMinioClient") MinioClient presignMinioClient,
            MinioProperties props) {
        this.minioClient = minioClient;
        this.presignMinioClient = presignMinioClient;
        this.props = props;
    }

    public String generateUploadUrl(String key) {
        try {
            // Use the presign client so the generated URL is signed for the public-facing host
            String url = presignMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(props.getBucket())
                            .object(key)
                            .expiry(60 * 10) //10 минут
                            .build()
            );
            if (log.isDebugEnabled()) log.debug("Generated upload presigned url for {} -> {}", key, url);
            return url;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String generateDownloadUrl(String key) {
        if (key == null || key.isBlank()) {
            return null; // или Optional, если хочешь более строгий контракт
        }

        try {
            String url = presignMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(props.getBucket())
                            .object(key)
                            .expiry(60 * 10) // 10 минут
                            .build()
            );

            if (log.isDebugEnabled()) log.debug("Generated download presigned url for {} -> {}", key, url);
            return url;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to generate presigned URL for object: " + key, e
            );
        }
    }

    /**
     * Replaces the internal MinIO endpoint (minio.url) with the public-facing URL
     * (minio.public-url) so presigned URLs returned to the browser are reachable.
     * If publicUrl is not configured, the original URL is returned unchanged.
     */
    private String toPublicUrl(String presignedUrl) {
        String publicUrl = props.getPublicUrl();
        if (publicUrl == null || publicUrl.isBlank()) return presignedUrl;
        String internalUrl = props.getUrl();
        if (internalUrl == null || internalUrl.isBlank()) return presignedUrl;
        // Normalise trailing slashes before replacing
        String base = internalUrl.endsWith("/") ? internalUrl.substring(0, internalUrl.length() - 1) : internalUrl;
        String pub  = publicUrl.endsWith("/")   ? publicUrl.substring(0, publicUrl.length() - 1)     : publicUrl;
        return presignedUrl.replace(base, pub);
    }

    public byte[] getObject(String key) {
        try (GetObjectResponse stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(props.getBucket())
                        .object(key)
                        .build()
        )) {

            return stream.readAllBytes();

        } catch (Exception e) {
            throw new RuntimeException("Failed to read object: " + key, e);
        }
    }

    public void deleteScreenshot(String key) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(props.getBucket())
                            .object(key)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getOnlyServerKeys(List<String> keys) {
        return keys.stream()
                .filter(key -> key.contains("/sv/"))
                .toList();
    }

    public List<String> getOnlyClientKeys(List<String> keys) {
        return keys.stream()
                .filter(key -> key.contains("/cl/"))
                .toList();
    }
}