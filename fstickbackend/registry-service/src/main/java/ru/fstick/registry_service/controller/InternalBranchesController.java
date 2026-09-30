package ru.fstick.registry_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.registry_service.dto.api.response.InternalBranchResponse;
import ru.fstick.registry_service.service.CodeService;

import java.util.UUID;

@RestController
@RequestMapping("/internal/branches")
@RequiredArgsConstructor
public class InternalBranchesController {

    private final CodeService codeService;

    @GetMapping("/{branchId}")
    public InternalBranchResponse getBranch(@PathVariable UUID branchId) {
        return codeService.getInternalBranch(branchId);
    }
}
