package ru.fstick.registry_service.config;


import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
