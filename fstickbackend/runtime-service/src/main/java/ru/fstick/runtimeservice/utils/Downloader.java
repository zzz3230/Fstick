package ru.fstick.runtimeservice.utils;

import org.springframework.web.reactive.function.client.WebClient;

public class Downloader {

    private final WebClient webClient = WebClient.create();

    public String download(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}