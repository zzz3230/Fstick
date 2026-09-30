package ru.fstick.registry_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.fstick.registry_service.dto.Status;
import ru.fstick.registry_service.dto.api.request.AddPluginRequest;
import ru.fstick.registry_service.dto.api.response.AddPluginResponse;
import ru.fstick.registry_service.dto.api.response.CodeResponse;
import ru.fstick.registry_service.dto.api.view.PluginViewOwned;
import ru.fstick.registry_service.dto.api.view.PluginViewShrink;
import ru.fstick.registry_service.dto.api.view.PluginsView;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.exception.ApiExceptionHandler;
import ru.fstick.registry_service.service.CodeService;
import ru.fstick.registry_service.service.PluginsService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PluginsControllerTest {

    private static final String BODY = """
            {"name":"P","description":"D"}
            """;

    private static final UUID PLUGIN_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final String SHA = "sha256:" + "a".repeat(64);

    @Mock
    private PluginsService pluginsService;
    @Mock
    private CodeService codeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PluginsController(pluginsService, codeService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void addPlugin_headerUuidBecomesAuthorAndReturns201() throws Exception {
        UUID userId = UUID.randomUUID();
        when(pluginsService.createPlugin(any(), eq(userId))).thenReturn(AddPluginResponse.builder()
                .pluginId(PLUGIN_ID).devBranchId(BRANCH_ID).build());

        mockMvc.perform(post("/api/v1/plugins")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());

        ArgumentCaptor<UUID> author = ArgumentCaptor.forClass(UUID.class);
        verify(pluginsService).createPlugin(any(AddPluginRequest.class), author.capture());
        assertEquals(userId, author.getValue());
    }

    @Test
    void addPlugin_missingHeader_returns400WithErrorBody() throws Exception {
        mockMvc.perform(post("/api/v1/plugins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing_user"));

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

    @Test
    void getPlugins_public_needsNoHeader() throws Exception {
        when(pluginsService.getPlugins(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(PluginsView.<PluginViewShrink>builder().items(List.of()).build());

        mockMvc.perform(get("/api/v1/plugins")).andExpect(status().isOk());

        verify(pluginsService, never()).getOwnedPlugins(any(), anyInt(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void getPlugins_ownedWithoutHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/plugins").param("owned", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing_user"));

        verifyNoInteractions(pluginsService);
    }

    @Test
    void getPlugins_ownedWithHeader_usesCaller() throws Exception {
        UUID userId = UUID.randomUUID();
        when(pluginsService.getOwnedPlugins(eq(userId), anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(PluginsView.<PluginViewOwned>builder().items(List.of()).build());

        mockMvc.perform(get("/api/v1/plugins").param("owned", "true").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        verify(pluginsService, never()).getPlugins(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void getPlugin_passesOptionalCallerAndChatId() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/plugins/" + PLUGIN_ID)
                        .header("X-User-Id", userId.toString())
                        .param("chat_id", "!room:example.org"))
                .andExpect(status().isOk());

        verify(pluginsService).getPlugin(PLUGIN_ID, userId, "!room:example.org");
    }

    @Test
    void getPlugin_anonymous_passesNullCaller() throws Exception {
        mockMvc.perform(get("/api/v1/plugins/" + PLUGIN_ID)).andExpect(status().isOk());

        verify(pluginsService).getPlugin(eq(PLUGIN_ID), isNull(), isNull());
    }

    @Test
    void getPlugin_notFound_mapsApiExceptionToErrorBody() throws Exception {
        when(pluginsService.getPlugin(any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "plugin_not_found", "Plugin not found"));

        mockMvc.perform(get("/api/v1/plugins/" + PLUGIN_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("plugin_not_found"))
                .andExpect(jsonPath("$.message").value("Plugin not found"));
    }

    @Test
    void updatePlugin_nonAuthor_returns403() throws Exception {
        when(pluginsService.updatePlugin(any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "not_author", "Only the plugin author can do this"));

        mockMvc.perform(put("/api/v1/plugins/" + PLUGIN_ID)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("not_author"));
    }

    @Test
    void updatePlugin_missingHeader_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/plugins/" + PLUGIN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pluginsService);
    }

    @Test
    void changeStatus_readsBareEnumBody() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/plugins/" + PLUGIN_ID + "/status")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"HIDDEN\""))
                .andExpect(status().isOk());

        verify(pluginsService).changeStatus(PLUGIN_ID, userId, Status.HIDDEN);
    }

    @Test
    void commitAssets_passesCallerAndKeys() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/plugins/" + PLUGIN_ID + "/assets/commit")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[\"plugins/" + PLUGIN_ID + "/icon\"]}"))
                .andExpect(status().isOk());

        verify(pluginsService).commitAssets(eq(PLUGIN_ID), eq(userId), any());
    }

    @Test
    void getCodeServer_returnsTextAndSha() throws Exception {
        when(codeService.getServerCode(PLUGIN_ID, BRANCH_ID, null)).thenReturn(new CodeResponse("lua", SHA));

        mockMvc.perform(get("/api/v1/plugins/" + PLUGIN_ID + "/code/server").param("branch_id", BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("lua"))
                .andExpect(jsonPath("$.sha").value(SHA));
    }

    @Test
    void getCodeServer_missingBranchId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/plugins/" + PLUGIN_ID + "/code/server"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCodeClient_returnsEtag() throws Exception {
        when(codeService.getClientCode(PLUGIN_ID, BRANCH_ID, null, null, null))
                .thenReturn(Optional.of(new CodeResponse("js", SHA)));

        mockMvc.perform(get("/api/v1/plugins/" + PLUGIN_ID + "/code/client").param("branch_id", BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + SHA + "\""))
                .andExpect(jsonPath("$.text").value("js"));
    }

    @Test
    void getCodeClient_unchanged_returns304() throws Exception {
        String etag = "\"" + SHA + "\"";
        when(codeService.getClientCode(PLUGIN_ID, BRANCH_ID, null, "!room:example.org", etag)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/plugins/" + PLUGIN_ID + "/code/client")
                        .param("branch_id", BRANCH_ID.toString())
                        .param("chat_id", "!room:example.org")
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void getCodeClient_hiddenBranch_returns404() throws Exception {
        when(codeService.getClientCode(any(), any(), any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "branch_not_found", "Branch not found"));

        mockMvc.perform(get("/api/v1/plugins/" + PLUGIN_ID + "/code/client").param("branch_id", BRANCH_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("branch_not_found"));
    }
}
