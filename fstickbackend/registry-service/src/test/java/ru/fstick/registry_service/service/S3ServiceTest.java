package ru.fstick.registry_service.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.fstick.registry_service.config.MinioProperties;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class S3ServiceTest {

    @Mock private MinioClient minioClient;
    @Mock private MinioClient presignMinioClient;
    @Mock private MinioProperties props;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        when(props.getBucket()).thenReturn("test-bucket");
        s3Service = new S3Service(minioClient, presignMinioClient, props);
    }

    // ── generateDownloadUrl ───────────────────────────────────────────────────

    @Test
    void generateDownloadUrl_validKey_returnsUrl() throws Exception {
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio/test-bucket/icon?token=abc");

        String url = s3Service.generateDownloadUrl("plugins/123/icon");

        assertEquals("http://minio/test-bucket/icon?token=abc", url);
    }

    @Test
    void generateDownloadUrl_nullKey_returnsNull() {
        assertNull(s3Service.generateDownloadUrl(null));
    }

    @Test
    void generateDownloadUrl_blankKey_returnsNull() {
        assertNull(s3Service.generateDownloadUrl("   "));
    }

    @Test
    void generateDownloadUrl_minioThrows_wrapsException() throws Exception {
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("minio error"));

        assertThrows(RuntimeException.class, () -> s3Service.generateDownloadUrl("some/key"));
    }

    // ── generateUploadUrl ─────────────────────────────────────────────────────

    @Test
    void generateUploadUrl_validKey_returnsUrl() throws Exception {
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio/upload?token=xyz");

        String url = s3Service.generateUploadUrl("plugins/123/icon");

        assertEquals("http://minio/upload?token=xyz", url);
    }

    // ── getOnlyClientKeys / getOnlyServerKeys ─────────────────────────────────

    @Test
    void getOnlyClientKeys_filtersCorrectly() {
        List<String> keys = List.of(
                "plugins/id/versions/1.0.0/cl/js/1.0.0/app.js",
                "plugins/id/versions/1.0.0/sv/java/21.0.0/main.jar",
                "plugins/id/versions/1.0.0/cl/ts/2.0.0/index.ts"
        );

        List<String> result = s3Service.getOnlyClientKeys(keys);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(k -> k.contains("/cl/")));
    }

    @Test
    void getOnlyServerKeys_filtersCorrectly() {
        List<String> keys = List.of(
                "plugins/id/versions/1.0.0/cl/js/1.0.0/app.js",
                "plugins/id/versions/1.0.0/sv/java/21.0.0/main.jar"
        );

        List<String> result = s3Service.getOnlyServerKeys(keys);

        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("/sv/"));
    }

    @Test
    void getOnlyClientKeys_noMatches_returnsEmpty() {
        List<String> result = s3Service.getOnlyClientKeys(
                List.of("plugins/id/versions/1.0.0/sv/java/21.0.0/main.jar")
        );
        assertTrue(result.isEmpty());
    }
}