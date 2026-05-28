package ru.fstick.runtimeservice.utils;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import java.net.URI;
import java.time.Duration;

public class Downloader {

    private final WebClient webClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(
                    HttpClient.create()
                            .followRedirect(true)
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                            .responseTimeout(Duration.ofSeconds(30))
            ))
            .build();

    public String download(String url) {
        byte[] data = webClient.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToMono(byte[].class)
                .block();

        return new String(data);
    }
}