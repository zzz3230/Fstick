package ru.fstick.registry_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "minio")
@Getter
@Setter
public class MinioProperties {

    private String url;
    private String accessKey;
    private String secretKey;
    private String bucket;

}