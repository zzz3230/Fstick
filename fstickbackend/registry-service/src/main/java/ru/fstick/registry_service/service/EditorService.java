package ru.fstick.registry_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.fstick.registry_service.client.RuntimeClient;
import ru.fstick.registry_service.dto.BranchStatus;
import ru.fstick.registry_service.dto.api.editor.EditResponse;
import ru.fstick.registry_service.dto.api.editor.ReloadResponse;
import ru.fstick.registry_service.dto.api.editor.SaveCodeRequest;
import ru.fstick.registry_service.dto.api.editor.SaveCodeResponse;
import ru.fstick.registry_service.dto.api.response.CodeResponse;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.repository.BranchRepository;
import ru.fstick.registry_service.repository.PluginsRepository;
import ru.fstick.registry_service.util.ShaFormat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EditorService {

    static final int MAX_SOURCE_BYTES = 500_000;

    private static final String CLIENT = "client";
    private static final String SERVER = "server";

    private final PluginsRepository pluginsRepository;
    private final BranchRepository branchRepository;
    private final BlobStore blobStore;
    private final AccessGuard accessGuard;
    private final RuntimeClient runtimeClient;
    private final ReloadNotifier reloadNotifier;
    private final LuaSyntaxChecker luaSyntaxChecker;
    private final TransactionTemplate transactionTemplate;

    public EditResponse edit(UUID pluginId, UUID branchId, UUID caller, String chatId) {
        PluginData plugin = findPlugin(pluginId);
        Branch branch = branchOf(pluginId, branchId);
        checkEditable(plugin, branch, caller, chatId);

        return new EditResponse(
                new EditResponse.PluginInfo(plugin.getId(), plugin.getName(), plugin.getAuthorId()),
                new EditResponse.BranchInfo(branch.getId(), branch.getStatus().name(),
                        new EditResponse.Runtime(branch.getRuntimeClient(), branch.getRuntimeServer())),
                new EditResponse.Source(read(pluginId, branch.getClientBlobSha()), read(pluginId, branch.getServerBlobSha())),
                chatId);
    }

    public SaveCodeResponse save(UUID pluginId, UUID branchId, UUID caller, String chatId, SaveCodeRequest request) {
        PluginData plugin = findPlugin(pluginId);
        Saved saved = transactionTemplate.execute(status -> saveLocked(plugin, branchId, caller, chatId, request));

        List<SaveCodeResponse.Warning> warnings = new ArrayList<>();
        if (request.server() != null) {
            luaSyntaxChecker.check(request.server().text()).ifPresent(problem ->
                    warnings.add(new SaveCodeResponse.Warning(SERVER, problem.message(), problem.line())));
        }

        boolean reloaded = true;
        Long retryAfterMs = null;
        if (saved.serverChanged()) {
            try {
                RuntimeClient.ReloadResult result = runtimeClient.reload(pluginId, chatId, branchId);
                reloaded = result.reloaded();
                retryAfterMs = result.retryAfterMs();
            } catch (ApiException ex) {
                reloaded = false;
                retryAfterMs = 0L;
                warnings.add(new SaveCodeResponse.Warning(SERVER, "runtime unavailable, retry hot-reload", null));
            }
        }
        if (saved.clientChanged()) {
            reloadNotifier.clientChanged(pluginId, branchId);
        }

        return new SaveCodeResponse(warnings,
                new SaveCodeResponse.Sha(ShaFormat.format(saved.clientHex()), ShaFormat.format(saved.serverHex())),
                reloaded, retryAfterMs, saved.clientChanged());
    }

    public ReloadResponse reload(UUID pluginId, UUID branchId, UUID caller, String chatId) {
        PluginData plugin = findPlugin(pluginId);
        Branch branch = branchOf(pluginId, branchId);
        checkEditable(plugin, branch, caller, chatId);

        RuntimeClient.ReloadResult result = runtimeClient.reload(pluginId, chatId, branchId);
        if (!result.reloaded()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "reload_throttled",
                    "Reload throttled, retry later", Map.of("retry_after_ms", result.retryAfterMs()));
        }
        return new ReloadResponse(true);
    }

    private Saved saveLocked(PluginData plugin, UUID branchId, UUID caller, String chatId, SaveCodeRequest request) {
        Branch branch = branchRepository.lockForUpdate(branchId)
                .filter(found -> found.getPluginId().equals(plugin.getId()))
                .orElseThrow(() -> branchNotFound(branchId));
        checkEditable(plugin, branch, caller, chatId);

        if (request.runtime() != null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "runtime_change_unsupported",
                    "Changing runtime versions is not supported");
        }
        if (request.client() == null && request.server() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed",
                    "At least one of client, server is required");
        }

        String newClient = resolveSide(CLIENT, request.client(), branch);
        String newServer = resolveSide(SERVER, request.server(), branch);

        boolean clientChanged = !newClient.equals(branch.getClientBlobSha());
        boolean serverChanged = !newServer.equals(branch.getServerBlobSha());
        String pluginId = plugin.getId().toString();
        if (clientChanged) {
            blobStore.put(pluginId, request.client().text());
        }
        if (serverChanged) {
            blobStore.put(pluginId, request.server().text());
        }
        if (clientChanged || serverChanged) {
            branchRepository.updateShas(branchId, newClient, newServer);
        }
        return new Saved(newClient, newServer, clientChanged, serverChanged);
    }

    private String resolveSide(String side, SaveCodeRequest.Side change, Branch branch) {
        String currentHex = CLIENT.equals(side) ? branch.getClientBlobSha() : branch.getServerBlobSha();
        if (change == null) {
            return currentHex;
        }
        if (change.text() == null || change.baseSha() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed",
                    side + ".text and " + side + ".base_sha are required");
        }
        int size = change.text().getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_SOURCE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large",
                    "Source file is too large", Map.of("side", side, "limit", MAX_SOURCE_BYTES, "actual", size));
        }
        if (!ShaFormat.parse(change.baseSha()).equals(currentHex)) {
            throw new ApiException(HttpStatus.CONFLICT, "stale_code",
                    "Code was changed since it was loaded", Map.of("current", Map.of(
                            CLIENT, ShaFormat.format(branch.getClientBlobSha()),
                            SERVER, ShaFormat.format(branch.getServerBlobSha()))));
        }
        return BlobStore.hex(change.text());
    }

    private void checkEditable(PluginData plugin, Branch branch, UUID caller, String chatId) {
        if (!accessGuard.isAuthor(plugin, caller)) {
            if (!accessGuard.canReadBranch(caller, plugin, branch, chatId)) {
                throw branchNotFound(branch.getId());
            }
            throw new ApiException(HttpStatus.FORBIDDEN, "not_author", "Only the plugin author can do this");
        }
        if (branch.getStatus() != BranchStatus.WORKING) {
            throw new ApiException(HttpStatus.CONFLICT, "not_dev_branch", "Only the development branch can be edited");
        }
    }

    private PluginData findPlugin(UUID pluginId) {
        return pluginsRepository.findPlugin(pluginId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "plugin_not_found", "Plugin not found: " + pluginId));
    }

    private Branch branchOf(UUID pluginId, UUID branchId) {
        return branchRepository.findById(branchId)
                .filter(branch -> branch.getPluginId().equals(pluginId))
                .orElseThrow(() -> branchNotFound(branchId));
    }

    private CodeResponse read(UUID pluginId, String hex) {
        return new CodeResponse(blobStore.get(pluginId.toString(), hex), ShaFormat.format(hex));
    }

    private static ApiException branchNotFound(UUID branchId) {
        return new ApiException(HttpStatus.NOT_FOUND, "branch_not_found", "Branch not found: " + branchId);
    }

    private record Saved(String clientHex, String serverHex, boolean clientChanged, boolean serverChanged) {}
}
