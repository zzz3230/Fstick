package ru.gatewayservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RequestDecompressionFilterTest {

    private static final String CODE_PATH = "/api/v1/plugins/p1/branches/b1/code";
    private static final String BROTLI_HELLO = "iwWAaGVsbG8gYnJvdGxpAw==";

    private RequestDecompressionFilter filter;
    private FilterChain chain;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new RequestDecompressionFilter();
        chain = mock(FilterChain.class);
        response = new MockHttpServletResponse();
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(data);
        }
        return out.toByteArray();
    }

    private MockHttpServletRequest put(String path, String encoding, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", path);
        if (encoding != null) {
            request.addHeader("Content-Encoding", encoding);
        }
        request.setContent(body);
        return request;
    }

    private byte[] forwardedBody() throws Exception {
        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(captor.capture(), any());
        HttpServletRequest forwarded = captor.getValue();
        assertNull(forwarded.getHeader("Content-Encoding"));
        byte[] body = forwarded.getInputStream().readAllBytes();
        assertEquals(body.length, forwarded.getContentLength());
        assertEquals(String.valueOf(body.length), forwarded.getHeader("Content-Length"));
        return body;
    }

    @Test
    void gzipBodyIsDecoded() throws Exception {
        byte[] plain = "print('hi')\n".repeat(2000).getBytes();

        filter.doFilter(put(CODE_PATH, "gzip", gzip(plain)), response, chain);

        assertArrayEquals(plain, forwardedBody());
    }

    @Test
    void brotliBodyIsDecoded() throws Exception {
        byte[] compressed = Base64.getDecoder().decode(BROTLI_HELLO);
        filter.doFilter(put(CODE_PATH, "br", compressed), response, chain);

        assertEquals("hello brotli", new String(forwardedBody()));
    }

    @Test
    void identityBodyPassesThroughUnderCap() throws Exception {
        byte[] plain = new byte[500_000];
        Arrays.fill(plain, (byte) 'a');

        filter.doFilter(put(CODE_PATH, null, plain), response, chain);

        assertArrayEquals(plain, forwardedBody());
    }

    @Test
    void identityBodyOverCapIs413() throws Exception {
        byte[] big = new byte[RequestDecompressionFilter.MAX_DECODED_BYTES + 1];
        new Random(1).nextBytes(big);

        filter.doFilter(put(CODE_PATH, "identity", big), response, chain);

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"body_too_large\""));
        assertTrue(response.getContentAsString().contains("2200000"));
        verifyNoInteractions(chain);
    }

    @Test
    void gzipDecodedOverCapIs413EvenWithSmallEncodedSize() throws Exception {
        byte[] noisy = new byte[RequestDecompressionFilter.MAX_DECODED_BYTES + 1];
        new Random(2).nextBytes(noisy);
        for (int i = 0; i < noisy.length; i++) {
            noisy[i] = (byte) ('a' + (noisy[i] & 0x07));
        }

        filter.doFilter(put(CODE_PATH, "gzip", gzip(noisy)), response, chain);

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"body_too_large\""));
        verifyNoInteractions(chain);
    }

    @Test
    void ratioBombIsRejectedBeforeFullyDecoded() throws Exception {
        byte[] compressed = gzip(new byte[10_000_000]);
        MockHttpServletRequest request = put(CODE_PATH, "gzip", compressed);

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("compression_ratio_exceeded"));
        verifyNoInteractions(chain);
    }

    @Test
    void unsupportedEncodingIs415() throws Exception {
        filter.doFilter(put(CODE_PATH, "deflate", new byte[] {1, 2, 3}), response, chain);

        assertEquals(415, response.getStatus());
        assertTrue(response.getContentAsString().contains("unsupported_encoding"));
        verifyNoInteractions(chain);
    }

    @Test
    void corruptGzipIs400() throws Exception {
        filter.doFilter(put(CODE_PATH, "gzip", new byte[] {1, 2, 3, 4, 5}), response, chain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("invalid_body"));
    }

    @Test
    void otherRoutesAreNotTouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/plugins/p1/command");
        request.addHeader("Content-Encoding", "deflate");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
