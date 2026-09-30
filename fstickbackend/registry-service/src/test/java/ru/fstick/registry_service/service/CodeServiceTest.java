package ru.fstick.registry_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.fstick.registry_service.client.InstallationClient;
import ru.fstick.registry_service.client.IntegrationClient;
import ru.fstick.registry_service.dto.BranchStatus;
import ru.fstick.registry_service.dto.api.response.CodeResponse;
import ru.fstick.registry_service.dto.api.response.InternalBranchResponse;
import ru.fstick.registry_service.dto.api.response.InternalPluginResponse;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.repository.BranchRepository;
import ru.fstick.registry_service.repository.PluginsRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static ru.fstick.registry_service.TestData.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CodeServiceTest {

    private static final UUID PLUGIN_ID = UUID.randomUUID();
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final String CHAT = "!room:example.org";
    private static final String CLIENT_SHA = "sha256:" + CLIENT_HEX;

    @Mock private PluginsRepository pluginsRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private BlobStore blobStore;
    @Mock private InstallationClient installationClient;
    @Mock private IntegrationClient integrationClient;
    @Mock private ModeratorService moderatorService;

    private CodeService codeService;

    @BeforeEach
    void setUp() {
        AccessGuard guard = new AccessGuard(pluginsRepository, installationClient, integrationClient, moderatorService);
        codeService = new CodeService(pluginsRepository, branchRepository, blobStore, guard);

        PluginData plugin = plugin(PLUGIN_ID, AUTHOR);
        when(pluginsRepository.findPlugin(PLUGIN_ID)).thenReturn(Optional.of(plugin));
        when(pluginsRepository.getPlugin(PLUGIN_ID)).thenReturn(plugin);
        when(blobStore.get(PLUGIN_ID.toString(), SERVER_HEX)).thenReturn("lua source");
        when(blobStore.get(PLUGIN_ID.toString(), CLIENT_HEX)).thenReturn("js source");
    }

    private Branch stub(BranchStatus status) {
        Branch branch = branch(PLUGIN_ID, status);
        when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        return branch;
    }

    private static void assertNotFound(org.junit.jupiter.api.function.Executable call, String code) {
        ApiException ex = assertThrows(ApiException.class, call);
        assertEquals(404, ex.getStatus().value());
        assertEquals(code, ex.getCode());
    }

    // ── public server code ────────────────────────────────────────────────────

    @Test
    void getServerCode_released_anonymousGetsTextAndSha() {
        Branch released = stub(BranchStatus.RELEASED);

        CodeResponse response = codeService.getServerCode(PLUGIN_ID, released.getId(), null);

        assertEquals("lua source", response.getText());
        assertEquals("sha256:" + SERVER_HEX, response.getSha());
    }

    @Test
    void getServerCode_candidate_authorOnly() {
        Branch candidate = stub(BranchStatus.WAITING_APPROVE);

        assertEquals("lua source", codeService.getServerCode(PLUGIN_ID, candidate.getId(), AUTHOR).getText());
        assertNotFound(() -> codeService.getServerCode(PLUGIN_ID, candidate.getId(), STRANGER), "branch_not_found");
        assertNotFound(() -> codeService.getServerCode(PLUGIN_ID, candidate.getId(), null), "branch_not_found");
    }

    @Test
    void getServerCode_dev_authorOnly() {
        Branch dev = stub(BranchStatus.WORKING);

        assertEquals("lua source", codeService.getServerCode(PLUGIN_ID, dev.getId(), AUTHOR).getText());
        assertNotFound(() -> codeService.getServerCode(PLUGIN_ID, dev.getId(), STRANGER), "branch_not_found");
    }

    @Test
    void getServerCode_rejectedAndCancelled_authorOnly() {
        for (BranchStatus status : List.of(BranchStatus.REJECTED, BranchStatus.CANCELLED)) {
            Branch branch = stub(status);

            assertEquals("lua source", codeService.getServerCode(PLUGIN_ID, branch.getId(), AUTHOR).getText());
            assertNotFound(() -> codeService.getServerCode(PLUGIN_ID, branch.getId(), STRANGER), "branch_not_found");
        }
    }

    @Test
    void getServerCode_branchOfAnotherPlugin_404() {
        Branch foreign = branch(UUID.randomUUID(), BranchStatus.RELEASED);
        when(branchRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertNotFound(() -> codeService.getServerCode(PLUGIN_ID, foreign.getId(), AUTHOR), "branch_not_found");
    }

    @Test
    void getServerCode_unknownBranch_404() {
        UUID unknown = UUID.randomUUID();
        when(branchRepository.findById(unknown)).thenReturn(Optional.empty());

        assertNotFound(() -> codeService.getServerCode(PLUGIN_ID, unknown, AUTHOR), "branch_not_found");
    }

    @Test
    void getServerCode_unknownPlugin_404() {
        UUID unknown = UUID.randomUUID();
        when(pluginsRepository.findPlugin(unknown)).thenReturn(Optional.empty());

        assertNotFound(() -> codeService.getServerCode(unknown, UUID.randomUUID(), AUTHOR), "plugin_not_found");
    }

    // ── public client code ────────────────────────────────────────────────────

    @Test
    void getClientCode_released_returnsTextAndSha() {
        Branch released = stub(BranchStatus.RELEASED);

        CodeResponse response = codeService.getClientCode(PLUGIN_ID, released.getId(), null, null, null).orElseThrow();

        assertEquals("js source", response.getText());
        assertEquals(CLIENT_SHA, response.getSha());
    }

    @Test
    void getClientCode_dev_memberOfChatRunningBranch_allowed() {
        Branch dev = stub(BranchStatus.WORKING);
        when(installationClient.resolve(PLUGIN_ID, CHAT)).thenReturn(Optional.of(new InstallationClient.Resolved(dev.getId(), "WORKING")));
        when(integrationClient.isMember(CHAT, STRANGER)).thenReturn(true);

        assertTrue(codeService.getClientCode(PLUGIN_ID, dev.getId(), STRANGER, CHAT, null).isPresent());
    }

    @Test
    void getClientCode_dev_notAMember_404() {
        Branch dev = stub(BranchStatus.WORKING);
        when(installationClient.resolve(PLUGIN_ID, CHAT)).thenReturn(Optional.of(new InstallationClient.Resolved(dev.getId(), "WORKING")));
        when(integrationClient.isMember(CHAT, STRANGER)).thenReturn(false);

        assertNotFound(() -> codeService.getClientCode(PLUGIN_ID, dev.getId(), STRANGER, CHAT, null), "branch_not_found");
    }

    @Test
    void getClientCode_dev_strangerWithoutChat_404() {
        Branch dev = stub(BranchStatus.WORKING);

        assertNotFound(() -> codeService.getClientCode(PLUGIN_ID, dev.getId(), STRANGER, null, null), "branch_not_found");
    }

    @Test
    void getClientCode_ifNoneMatchEqualsSha_returnsEmpty() {
        Branch released = stub(BranchStatus.RELEASED);

        assertTrue(codeService.getClientCode(PLUGIN_ID, released.getId(), null, null, "\"" + CLIENT_SHA + "\"").isEmpty());
        verify(blobStore, never()).get(anyString(), anyString());
    }

    @Test
    void getClientCode_ifNoneMatchWeakOrListed_returnsEmpty() {
        Branch released = stub(BranchStatus.RELEASED);

        assertTrue(codeService.getClientCode(PLUGIN_ID, released.getId(), null, null, "W/\"" + CLIENT_SHA + "\"").isEmpty());
        assertTrue(codeService.getClientCode(PLUGIN_ID, released.getId(), null, null, "\"sha256:other\", \"" + CLIENT_SHA + "\"").isEmpty());
    }

    @Test
    void getClientCode_ifNoneMatchDiffers_returnsBody() {
        Branch released = stub(BranchStatus.RELEASED);

        assertTrue(codeService.getClientCode(PLUGIN_ID, released.getId(), null, null, "\"sha256:" + "0".repeat(64) + "\"").isPresent());
    }

    @Test
    void getClientCode_hiddenBranchWithMatchingEtag_stillReturns404() {
        Branch candidate = stub(BranchStatus.APPROVING);

        assertNotFound(() -> codeService.getClientCode(PLUGIN_ID, candidate.getId(), STRANGER, null, "\"" + CLIENT_SHA + "\""), "branch_not_found");
    }

    // ── internal ──────────────────────────────────────────────────────────────

    @Test
    void getInternalServerCode_anyBranchWithoutGate() {
        for (BranchStatus status : BranchStatus.values()) {
            Branch branch = stub(status);

            assertEquals("lua source", codeService.getInternalServerCode(PLUGIN_ID, branch.getId()).getText());
        }
    }

    @Test
    void getInternalServerCode_branchOfAnotherPlugin_404() {
        Branch foreign = branch(UUID.randomUUID(), BranchStatus.RELEASED);
        when(branchRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertNotFound(() -> codeService.getInternalServerCode(PLUGIN_ID, foreign.getId()), "branch_not_found");
    }

    @Test
    void getInternalPlugin_listsAllBranchesWithShas() {
        Branch dev = branch(PLUGIN_ID, BranchStatus.WORKING);
        Branch rejected = branch(PLUGIN_ID, BranchStatus.REJECTED);
        when(branchRepository.findByPlugin(PLUGIN_ID)).thenReturn(List.of(dev, rejected));

        InternalPluginResponse response = codeService.getInternalPlugin(PLUGIN_ID);

        assertEquals(PLUGIN_ID, response.getId());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals(AUTHOR, response.getAuthorId());
        assertEquals(2, response.getBranches().size());
        assertEquals("WORKING", response.getBranches().get(0).getStatus());
        assertNull(response.getBranches().get(0).getSemver());
        assertEquals("sha256:" + SERVER_HEX, response.getBranches().get(0).getServerBlobSha());
        assertEquals(CLIENT_SHA, response.getBranches().get(1).getClientBlobSha());
    }

    @Test
    void getInternalBranch_returnsPluginStatusAndSemver() {
        Branch dev = branch(PLUGIN_ID, BranchStatus.WORKING);
        when(branchRepository.findById(dev.getId())).thenReturn(Optional.of(dev));

        InternalBranchResponse response = codeService.getInternalBranch(dev.getId());

        assertEquals(dev.getId(), response.getBranchId());
        assertEquals(PLUGIN_ID, response.getPluginId());
        assertEquals("WORKING", response.getStatus());
        assertNull(response.getSemver());
    }

    @Test
    void getInternalBranch_unknown_404() {
        UUID unknown = UUID.randomUUID();
        when(branchRepository.findById(unknown)).thenReturn(Optional.empty());

        assertNotFound(() -> codeService.getInternalBranch(unknown), "branch_not_found");
    }

    @Test
    void getInternalPlugin_unknownPlugin_404() {
        UUID unknown = UUID.randomUUID();
        when(pluginsRepository.getPlugin(unknown)).thenThrow(
                new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "plugin_not_found", "Plugin not found"));

        assertNotFound(() -> codeService.getInternalPlugin(unknown), "plugin_not_found");
    }
}
