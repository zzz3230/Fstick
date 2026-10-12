package ru.gatewayservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.brotli.dec.BrotliInputStream;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestDecompressionFilter extends OncePerRequestFilter {

    static final int MAX_DECODED_BYTES = 2_200_000;
    static final int RATIO_CHECK_THRESHOLD = 64 * 1024;
    static final int MAX_RATIO = 200;

    private static final String CODE_PATH = "/api/v1/plugins/*/branches/*/code";

    private final AntPathMatcher matcher = new AntPathMatcher();

    private static final class RejectedBodyException extends IOException {
        final int status;
        final String body;

        RejectedBodyException(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"PUT".equals(request.getMethod()) || !matcher.match(CODE_PATH, request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] decoded;
        try {
            decoded = decode(request);
        } catch (RejectedBodyException ex) {
            response.setStatus(ex.status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(ex.body);
            return;
        }
        chain.doFilter(new DecodedRequest(request, decoded), response);
    }

    private byte[] decode(HttpServletRequest request) throws IOException {
        String header = request.getHeader("Content-Encoding");
        String encoding = header == null || header.isBlank() ? "identity" : header.trim().toLowerCase();

        CountingInputStream raw = new CountingInputStream(request.getInputStream());
        try {
            InputStream source = switch (encoding) {
                case "identity" -> raw;
                case "gzip" -> new GZIPInputStream(raw);
                case "br" -> new BrotliInputStream(raw);
                default -> throw new RejectedBodyException(415,
                        "{\"error\":\"unsupported_encoding\",\"message\":\"Unsupported Content-Encoding\"}");
            };
            return readBounded(source, raw);
        } catch (RejectedBodyException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            if ("identity".equals(encoding)) {
                throw ex;
            }
            throw new RejectedBodyException(400,
                    "{\"error\":\"invalid_body\",\"message\":\"Request body cannot be decoded\"}");
        }
    }

    private byte[] readBounded(InputStream source, CountingInputStream raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long decoded = 0;
        int read;
        while ((read = source.read(buffer)) != -1) {
            decoded += read;
            if (decoded > MAX_DECODED_BYTES) {
                throw new RejectedBodyException(413, "{\"error\":\"body_too_large\","
                        + "\"message\":\"Request body is too large\",\"limit\":" + MAX_DECODED_BYTES + "}");
            }
            if (decoded > RATIO_CHECK_THRESHOLD && decoded / Math.max(1, raw.count) > MAX_RATIO) {
                throw new RejectedBodyException(413, "{\"error\":\"compression_ratio_exceeded\","
                        + "\"message\":\"Compression ratio is too high\"}");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static final class CountingInputStream extends FilterInputStream {
        long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                count++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                count += n;
            }
            return n;
        }
    }

    private static final class DecodedRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        DecodedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public String getHeader(String name) {
            if ("Content-Encoding".equalsIgnoreCase(name)) {
                return null;
            }
            if ("Content-Length".equalsIgnoreCase(name)) {
                return String.valueOf(body.length);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Content-Encoding".equalsIgnoreCase(name)) {
                return Collections.emptyEnumeration();
            }
            if ("Content-Length".equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(String.valueOf(body.length)));
            }
            return super.getHeaders(name);
        }
    }
}
