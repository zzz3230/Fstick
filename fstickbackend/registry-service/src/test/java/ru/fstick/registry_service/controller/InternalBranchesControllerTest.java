package ru.fstick.registry_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.fstick.registry_service.dto.api.response.InternalBranchResponse;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.exception.ApiExceptionHandler;
import ru.fstick.registry_service.service.CodeService;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalBranchesControllerTest {

    private static final UUID PLUGIN_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock
    private CodeService codeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalBranchesController(codeService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getBranch_noUserHeaderRequired() throws Exception {
        when(codeService.getInternalBranch(BRANCH_ID)).thenReturn(InternalBranchResponse.builder()
                .branchId(BRANCH_ID)
                .pluginId(PLUGIN_ID)
                .status("RELEASED")
                .semver("1.2.0")
                .build());

        mockMvc.perform(get("/internal/branches/" + BRANCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.semver").value("1.2.0"));
    }

    @Test
    void getBranch_unknown_returns404() throws Exception {
        when(codeService.getInternalBranch(BRANCH_ID))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "branch_not_found", "Branch not found"));

        mockMvc.perform(get("/internal/branches/" + BRANCH_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("branch_not_found"));
    }
}
