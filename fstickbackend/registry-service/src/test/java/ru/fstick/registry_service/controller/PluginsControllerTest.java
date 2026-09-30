package ru.fstick.registry_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.fstick.registry_service.dto.api.request.AddPluginRequest;
import ru.fstick.registry_service.service.PluginsService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PluginsControllerTest {

    private static final String BODY = """
            {"version":"1.0.0","runtime":"sv.lua@1.0.0","name":"P","description":"D",
             "files":[{"fileName":"main.lua","type":"code/sv.lua@1.0.0"}],
             "icon":{"fileName":"icon.png","type":"image/png"}}
            """;

    @Mock
    private PluginsService pluginsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PluginsController(pluginsService)).build();
    }

    @Test
    void addPlugin_headerUuidBecomesAuthor() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/plugins")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<UUID> author = ArgumentCaptor.forClass(UUID.class);
        verify(pluginsService).initPluginUpload(any(AddPluginRequest.class), author.capture());
        assertEquals(userId, author.getValue());
    }

    @Test
    void addPlugin_missingHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/plugins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pluginsService);
    }

    @Test
    void addPlugin_nonUuidHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/plugins")
                        .header("X-User-Id", "@user:homeserver.org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pluginsService);
    }
}
