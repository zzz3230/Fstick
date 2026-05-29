package ru.fstick.registry_service.config;


import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties props;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(props.getUrl())
                .credentials(
                        props.getAccessKey(),
                        props.getSecretKey()
                )
                .build();
    }

    /**
     * MinioClient used only for generating presigned URLs.
     * It is configured with the public-facing URL (minio.public-url) so that
     * the generated presigned URL contains the host the browser will use.
     *
     * When running inside Docker Desktop (Windows/Mac), 'localhost' in
     * publicUrl is not reachable from within the container. We solve this
     * by injecting a custom OkHttp Dns that resolves 'localhost'/'127.0.0.1'
     * to 'host.docker.internal' at TCP connect time, while the URL itself
     * keeps 'localhost' so the browser receives a usable presigned URL.
     */
    @Bean("presignMinioClient")
    public MinioClient presignMinioClient() {
        String endpoint = props.getPublicUrl() != null && !props.getPublicUrl().isBlank()
                ? props.getPublicUrl() : props.getUrl();

        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(props.getAccessKey(), props.getSecretKey());

        if (endpoint != null && (endpoint.contains("localhost") || endpoint.contains("127.0.0.1"))) {
            OkHttpClient ok = new OkHttpClient.Builder()
                    .dns(hostname -> {
                        if ("localhost".equals(hostname) || "127.0.0.1".equals(hostname)) {
                            try {
                                List<InetAddress> addrs = Dns.SYSTEM.lookup("host.docker.internal");
                                if (!addrs.isEmpty()) return addrs;
                            } catch (Exception ignored) {
                                // host.docker.internal not available — fall through
                            }
                        }
                        return Dns.SYSTEM.lookup(hostname);
                    })
                    .build();
            builder.httpClient(ok);
        }

        return builder.build();
    }

    @Bean("presignMinioClientInternal")
    public MinioClient presignMinioClientInternal() {
        String endpoint = props.getInternalUrl() != null && !props.getInternalUrl().isBlank()
                ? props.getInternalUrl() : props.getUrl();

        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(props.getAccessKey(), props.getSecretKey());

        if (endpoint != null && (endpoint.contains("localhost") || endpoint.contains("127.0.0.1"))) {
            OkHttpClient ok = new OkHttpClient.Builder()
                    .dns(hostname -> {
                        if ("localhost".equals(hostname) || "127.0.0.1".equals(hostname)) {
                            try {
                                List<InetAddress> addrs = Dns.SYSTEM.lookup("host.docker.internal");
                                if (!addrs.isEmpty()) return addrs;
                            } catch (Exception ignored) {
                                // host.docker.internal not available — fall through
                            }
                        }
                        return Dns.SYSTEM.lookup(hostname);
                    })
                    .build();
            builder.httpClient(ok);
        }

        return builder.build();
    }
}
