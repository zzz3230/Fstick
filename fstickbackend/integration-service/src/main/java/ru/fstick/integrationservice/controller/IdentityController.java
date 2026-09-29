package ru.fstick.integrationservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/identity")
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityService identityService;

    @PostMapping("/resolve")
    public ResolveIdentityResponse resolve(@RequestBody ResolveIdentityRequest request) {
        IdentityRepository.Resolved resolved = identityService.resolve(request.getMxid());
        return new ResolveIdentityResponse(resolved.internalUuid(), resolved.created());
    }

    @PostMapping("/resolve-batch")
    public IdentityMapResponse<String, UUID> resolveBatch(@RequestBody ResolveBatchRequest request) {
        List<String> mxids = request.getMxids() == null ? List.of() : request.getMxids();
        return new IdentityMapResponse<>(identityService.resolveBatch(mxids));
    }

    @GetMapping("/{internalUuid}")
    public MxidResponse lookup(@PathVariable UUID internalUuid) {
        return identityService.lookup(internalUuid)
                .map(MxidResponse::new)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "unknown_user", "Unknown user"));
    }

    @PostMapping("/lookup-batch")
    public IdentityMapResponse<UUID, String> lookupBatch(@RequestBody LookupBatchRequest request) {
        List<UUID> uuids = request.getUuids() == null ? List.of() : request.getUuids();
        return new IdentityMapResponse<>(identityService.lookupBatch(uuids));
    }
}
