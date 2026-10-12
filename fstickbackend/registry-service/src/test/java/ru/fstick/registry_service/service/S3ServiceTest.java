package ru.fstick.registry_service.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.fstick.registry_service.config.MinioProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class S3ServiceTest {

    @Mock private MinioClient minioClient;
    @Mock private MinioClient presignMinioClient;
    @Mock private MinioClient presignMinioClientInternal;
    @Mock private MinioProperties props;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        when(props.getBucket()).thenReturn("test-bucket");
        s3Service = new S3Service(minioClient, presignMinioClient, presignMinioClientInternal, props);
    }

    // ── generateDownloadUrl ───────────────────────────────────────────────────

    @Test
    void generateDownloadUrl_publicKey_usesPublicClient() throws Exception {
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio/test-bucket/icon?token=abc");

        String url = s3Service.generateDownloadUrl("plugins/123/icon", true);

        assertEquals("http://minio/test-bucket/icon?token=abc", url);
        verifyNoInteractions(presignMinioClientInternal);
    }

    @Test
    void generateDownloadUrl_internalKey_usesInternalClient() throws Exception {
        when(presignMinioClientInternal.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio:9000/test-bucket/key");

        assertEquals("http://minio:9000/test-bucket/key", s3Service.generateDownloadUrl("some/key", false));
        verifyNoInteractions(presignMinioClient);
    }

    @Test
    void generateDownloadUrl_nullKey_returnsNull() {
        assertNull(s3Service.generateDownloadUrl(null, true));
    }

    @Test
    void generateDownloadUrl_blankKey_returnsNull() {
        assertNull(s3Service.generateDownloadUrl("   ", true));
    }

    @Test
    void generateDownloadUrl_minioThrows_wrapsException() throws Exception {
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("minio error"));

        assertThrows(RuntimeException.class, () -> s3Service.generateDownloadUrl("some/key", true));
    }

    // ── generateUploadUrl ─────────────────────────────────────────────────────

    @Test
    void generateUploadUrl_validKey_returnsUrl() throws Exception {
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio/upload?token=xyz");

        assertEquals("http://minio/upload?token=xyz", s3Service.generateUploadUrl("plugins/123/icon"));
    }

    // ── exists ────────────────────────────────────────────────────────────────

    @Test
    void exists_objectPresent_returnsTrue() {
        assertTrue(s3Service.exists("plugins/123/icon"));
    }

    @Test
    void exists_noSuchKey_returnsFalse() throws Exception {
        ErrorResponseException noSuchKey = errorResponse("NoSuchKey");
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(noSuchKey);

        assertFalse(s3Service.exists("plugins/123/icon"));
    }

    @Test
    void exists_otherError_throws() throws Exception {
        ErrorResponseException denied = errorResponse("AccessDenied");
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(denied);

        assertThrows(RuntimeException.class, () -> s3Service.exists("plugins/123/icon"));
    }

    // ── putObject ─────────────────────────────────────────────────────────────

    @Test
    void putObject_uploadsBytes() throws Exception {
        s3Service.putObject("templates/icon.png", new byte[]{1, 2, 3}, "image/png");

        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void putObject_minioThrows_wrapsException() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> s3Service.putObject("k", new byte[0], "text/plain"));
    }

    private static ErrorResponseException errorResponse(String code) {
        ErrorResponse response = mock(ErrorResponse.class);
        when(response.code()).thenReturn(code);
        ErrorResponseException ex = mock(ErrorResponseException.class);
        when(ex.errorResponse()).thenReturn(response);
        return ex;
    }
}
