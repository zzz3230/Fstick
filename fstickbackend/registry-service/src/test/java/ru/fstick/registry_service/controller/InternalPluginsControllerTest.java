package ru.fstick.registry_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.fstick.registry_service.dto.api.response.CodeResponse;
import ru.fstick.registry_service.dto.api.response.InternalPluginResponse;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.exception.ApiExceptionHandler;
import ru.fstick.registry_service.service.CodeService;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalPluginsControllerTest {

    private static final UUID PLUGIN_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock
    private CodeService codeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalPluginsController(codeService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getPlugin_noUserHeaderRequired() throws Exception {
        when(codeService.getInternalPlugin(PLUGIN_ID)).thenReturn(InternalPluginResponse.builder()
                .id(PLUGIN_ID)
                .status("ACTIVE")
                .authorId(UUID.randomUUID())
                .branches(List.of(InternalPluginResponse.InternalBranch.builder().id(BRANCH_ID).status("WORKING").build()))
                .build());

        mockMvc.perform(get("/internal/plugins/" + PLUGIN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.branches[0].status").value("WORKING"));
    }

    @Test
    void getServerCode_noUserHeaderRequired() throws Exception {
        when(codeService.getInternalServerCode(PLUGIN_ID, BRANCH_ID)).thenReturn(new CodeResponse("lua", "sha256:x"));

        mockMvc.perform(get("/internal/plugins/" + PLUGIN_ID + "/code/server").param("branch_id", BRANCH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("lua"));
    }

    @Test
    void getServerCode_unknownBranch_returns404() throws Exception {
        when(codeService.getInternalServerCode(PLUGIN_ID, BRANCH_ID))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "branch_not_found", "Branch not found"));

        mockMvc.perform(get("/internal/plugins/" + PLUGIN_ID + "/code/server").param("branch_id", BRANCH_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("branch_not_found"));
    }
}
