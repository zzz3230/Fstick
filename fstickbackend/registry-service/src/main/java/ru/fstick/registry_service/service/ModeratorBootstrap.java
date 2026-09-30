package ru.fstick.registry_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.fstick.registry_service.repository.ModeratorRepository;

import java.util.Arrays;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ModeratorBootstrap {

    private final ModeratorRepository repository;

    @Value("${registry.moderators.bootstrap:}")
    private String bootstrap;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        Arrays.stream(bootstrap.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(UUID::fromString)
                .forEach(uuid -> repository.grant(uuid, null));
    }
}
