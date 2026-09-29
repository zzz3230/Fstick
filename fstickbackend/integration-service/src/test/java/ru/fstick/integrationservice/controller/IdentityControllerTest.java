package ru.fstick.integrationservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.fstick.integrationservice.dto.request.LookupBatchRequest;
import ru.fstick.integrationservice.dto.request.ResolveBatchRequest;
import ru.fstick.integrationservice.dto.request.ResolveIdentityRequest;
import ru.fstick.integrationservice.dto.response.IdentityMapResponse;
import ru.fstick.integrationservice.dto.response.MxidResponse;
import ru.fstick.integrationservice.dto.response.ResolveIdentityResponse;
import ru.fstick.integrationservice.exception.ApiException;
import ru.fstick.integrationservice.repository.IdentityRepository;
import ru.fstick.integrationservice.service.IdentityService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityControllerTest {

    @Mock
    private IdentityService identityService;

    private IdentityController controller;

    @BeforeEach
    void setUp() {
        controller = new IdentityController(identityService);
    }

    @Test
    void resolve_returnsUuidAndCreatedFlag() {
        UUID uuid = UUID.randomUUID();
        when(identityService.resolve("@a:hs")).thenReturn(new IdentityRepository.Resolved(uuid, true));

        ResolveIdentityRequest request = new ResolveIdentityRequest();
        request.setMxid("@a:hs");

        assertEquals(new ResolveIdentityResponse(uuid, true), controller.resolve(request));
    }

    @Test
    void resolveBatch_returnsMap() {
        UUID uuid = UUID.randomUUID();
        when(identityService.resolveBatch(List.of("@a:hs"))).thenReturn(Map.of("@a:hs", uuid));

        ResolveBatchRequest request = new ResolveBatchRequest();
        request.setMxids(List.of("@a:hs"));

        IdentityMapResponse<String, UUID> response = controller.resolveBatch(request);

        assertEquals(Map.of("@a:hs", uuid), response.map());
    }

    @Test
    void lookup_known_returnsMxid() {
        UUID uuid = UUID.randomUUID();
        when(identityService.lookup(uuid)).thenReturn(Optional.of("@a:hs"));

        assertEquals(new MxidResponse("@a:hs"), controller.lookup(uuid));
    }

    @Test
    void lookup_unknown_throwsNotFound() {
        UUID uuid = UUID.randomUUID();
        when(identityService.lookup(uuid)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> controller.lookup(uuid));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("unknown_user", ex.getCode());
    }

    @Test
    void lookupBatch_returnsMap() {
        UUID uuid = UUID.randomUUID();
        when(identityService.lookupBatch(List.of(uuid))).thenReturn(Map.of(uuid, "@a:hs"));

        LookupBatchRequest request = new LookupBatchRequest();
        request.setUuids(List.of(uuid));

        assertEquals(Map.of(uuid, "@a:hs"), controller.lookupBatch(request).map());
    }
}
