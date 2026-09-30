package ru.gatewayservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.gatewayservice.identity.IdentityCache;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeControllerTest {

    private IdentityCache cache;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cache = mock(IdentityCache.class);
        mvc = MockMvcBuilders.standaloneSetup(new MeController(cache)).build();
    }

    @Test
    void returnsBothIds() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(cache.lookupBatch(List.of(uuid))).thenReturn(Map.of(uuid, "@ilya:fstick.local"));

        mvc.perform(get("/api/v1/me").header("X-User-Id", uuid.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internal_uuid").value(uuid.toString()))
                .andExpect(jsonPath("$.mxid").value("@ilya:fstick.local"));
    }

    @Test
    void missingHeaderIs401() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());

        verifyNoInteractions(cache);
    }

    @Test
    void unknownUserIs401() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(cache.lookupBatch(List.of(uuid))).thenReturn(Map.of());

        mvc.perform(get("/api/v1/me").header("X-User-Id", uuid.toString()))
                .andExpect(status().isUnauthorized());
    }
}
