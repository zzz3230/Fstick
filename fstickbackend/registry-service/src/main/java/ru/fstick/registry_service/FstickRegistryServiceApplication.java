package ru.fstick.registry_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
@ConfigurationPropertiesScan
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class}) //убрать потом
public class FstickRegistryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FstickRegistryServiceApplication.class, args);
    }

}
