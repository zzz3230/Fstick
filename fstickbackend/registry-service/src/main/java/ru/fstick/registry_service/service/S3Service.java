package ru.fstick.registry_service.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.fstick.registry_service.config.MinioProperties;

import java.util.List;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final MinioClient minioClient;
    private final MinioProperties props;

    public String generateUploadUrl(String key) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(props.getBucket())
                            .object(key)
                            .expiry(60 * 10) //10 минут
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String generateDownloadUrl(String key) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(props.getBucket())
                            .object(key)
                            .expiry(60 * 10) //10 минут
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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