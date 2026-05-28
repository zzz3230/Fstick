package ru.fstick.runtimeservice.mock;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import org.mockserver.integration.ClientAndServer;
import ru.fstick.runtimeservice.utils.FileUtils;

@Configuration
public class MockServerConfig {

    private ClientAndServer mockServer;

    @PostConstruct
    public void start() {
        mockServer = ClientAndServer.startClientAndServer(1031);

        mockServer.when(
                request()
                        .withMethod("GET")
                        .withPath("/api/v1/plugins/123e4567-e89b-12d3-a456-426614174000/code/server")
                        .withQueryStringParameter("version", "last")
                        .withQueryStringParameter("runtime", "sv.lua@^0")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                {
                  "files": [
                    {
                      "download_url": "http://localhost:1031/files/plugin.lua"
                    }
                  ]
                }
                """)
        );

        mockServer.when(
                request()
                        .withMethod("GET")
                        .withPath("/files/plugin.lua")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(FileUtils.loadStringFromResource("lua_source/test_plugin.lua"))
        );
    }

    @PreDestroy
    public void stop() {
        mockServer.stop();
    }
}