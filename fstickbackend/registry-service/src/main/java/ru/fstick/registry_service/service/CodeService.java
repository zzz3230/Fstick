package ru.fstick.registry_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.fstick.registry_service.dto.api.response.CodeResponse;
import ru.fstick.registry_service.dto.api.response.InternalBranchResponse;
import ru.fstick.registry_service.dto.api.response.InternalPluginResponse;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.repository.BranchRepository;
import ru.fstick.registry_service.repository.PluginsRepository;
import ru.fstick.registry_service.util.ShaFormat;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CodeService {

    private final PluginsRepository pluginsRepository;
    private final BranchRepository branchRepository;
    private final BlobStore blobStore;
    private final AccessGuard accessGuard;

    public CodeResponse getServerCode(UUID pluginId, UUID branchId, UUID caller) {
        Branch branch = readableBranch(pluginId, branchId, caller, null);
        return read(pluginId, branch.getServerBlobSha());
    }

    // empty result means the caller's cached copy (ifNoneMatch) is current
    public Optional<CodeResponse> getClientCode(UUID pluginId, UUID branchId, UUID caller, String chatId, String ifNoneMatch) {
        Branch branch = readableBranch(pluginId, branchId, caller, chatId);
        if (ifNoneMatch != null && matches(ifNoneMatch, ShaFormat.format(branch.getClientBlobSha()))) {
            return Optional.empty();
        }
        return Optional.of(read(pluginId, branch.getClientBlobSha()));
    }

    public CodeResponse getInternalServerCode(UUID pluginId, UUID branchId) {
        return read(pluginId, branchOf(pluginId, branchId).getServerBlobSha());
    }

    public InternalBranchResponse getInternalBranch(UUID branchId) {
        Branch branch = branchRepository.findById(branchId).orElseThrow(() -> branchNotFound(branchId));

        return InternalBranchResponse.builder()
                .branchId(branch.getId())
                .pluginId(branch.getPluginId())
                .status(branch.getStatus().name())
                .semver(branch.getSemver())
                .build();
    }

    public InternalPluginResponse getInternalPlugin(UUID pluginId) {
        PluginData plugin = pluginsRepository.getPlugin(pluginId);

        return InternalPluginResponse.builder()
                .id(plugin.getId())
                .status(plugin.getStatus())
                .authorId(plugin.getAuthorId())
                .branches(branchRepository.findByPlugin(pluginId).stream()
                        .map(branch -> InternalPluginResponse.InternalBranch.builder()
                                .id(branch.getId())
                                .status(branch.getStatus().name())
                                .semver(branch.getSemver())
                                .serverBlobSha(ShaFormat.format(branch.getServerBlobSha()))
                                .clientBlobSha(ShaFormat.format(branch.getClientBlobSha()))
                                .build())
                        .toList())
                .build();
    }

    private Branch readableBranch(UUID pluginId, UUID branchId, UUID caller, String chatId) {
        PluginData plugin = pluginsRepository.findPlugin(pluginId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "plugin_not_found", "Plugin not found: " + pluginId));
        Branch branch = branchOf(pluginId, branchId);
        if (!accessGuard.canReadBranch(caller, plugin, branch, chatId)) {
            throw branchNotFound(branchId);
        }
        return branch;
    }

    private Branch branchOf(UUID pluginId, UUID branchId) {
        return branchRepository.findById(branchId)
                .filter(branch -> branch.getPluginId().equals(pluginId))
                .orElseThrow(() -> branchNotFound(branchId));
    }

    private CodeResponse read(UUID pluginId, String hex) {
        return new CodeResponse(blobStore.get(pluginId.toString(), hex), ShaFormat.format(hex));
    }

    private static boolean matches(String ifNoneMatch, String sha) {
        for (String tag : ifNoneMatch.split(",")) {
            String value = tag.trim();
            if (value.startsWith("W/")) {
                value = value.substring(2);
            }
            if (value.equals("*") || value.replace("\"", "").equals(sha)) {
                return true;
            }
        }
        return false;
    }

    private static ApiException branchNotFound(UUID branchId) {
        return new ApiException(HttpStatus.NOT_FOUND, "branch_not_found", "Branch not found: " + branchId);
    }
}
