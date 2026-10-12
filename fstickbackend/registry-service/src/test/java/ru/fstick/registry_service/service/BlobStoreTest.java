package ru.fstick.registry_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlobStoreTest {

    private static final String PLUGIN_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Mock private S3Service s3Service;

    private BlobStore blobStore;

    @BeforeEach
    void setUp() {
        blobStore = new BlobStore(s3Service);
    }

    @Test
    void hex_matchesKnownSha256Vector() {
        assertEquals(ABC_SHA256, BlobStore.hex("abc"));
    }

    @Test
    void hex_cyrillicIsHashedAsUtf8Bytes() throws Exception {
        String text = "привет";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));

        assertEquals(HexFormat.of().formatHex(digest), BlobStore.hex(text));
    }

    @Test
    void put_newBlob_uploadsUnderContentAddressedKey() {
        String key = "plugins/" + PLUGIN_ID + "/blobs/" + ABC_SHA256;
        when(s3Service.exists(key)).thenReturn(false);

        String hex = blobStore.put(PLUGIN_ID, "abc");

        assertEquals(ABC_SHA256, hex);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(s3Service).putObject(eq(key), bytes.capture(), eq("text/plain; charset=utf-8"));
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), bytes.getValue());
    }

    @Test
    void put_sameTextTwice_uploadsOnce() {
        when(s3Service.exists(anyString())).thenReturn(false, true);

        String first = blobStore.put(PLUGIN_ID, "abc");
        String second = blobStore.put(PLUGIN_ID, "abc");

        assertEquals(first, second);
        verify(s3Service, times(1)).putObject(anyString(), any(byte[].class), anyString());
    }

    @Test
    void get_returnsUtf8Text() {
        when(s3Service.getObject("plugins/" + PLUGIN_ID + "/blobs/" + ABC_SHA256))
                .thenReturn("привет".getBytes(StandardCharsets.UTF_8));

        assertEquals("привет", blobStore.get(PLUGIN_ID, ABC_SHA256));
    }

    @Test
    void get_missingBlob_throwsIllegalState() {
        when(s3Service.getObject(anyString())).thenThrow(new RuntimeException("NoSuchKey"));

        assertThrows(IllegalStateException.class, () -> blobStore.get(PLUGIN_ID, ABC_SHA256));
    }
}
