package ru.fstick.registry_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.fstick.registry_service.client.InstallationClient;
import ru.fstick.registry_service.client.IntegrationClient;
import ru.fstick.registry_service.dto.BranchStatus;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.repository.PluginsRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static ru.fstick.registry_service.TestData.branch;
import static ru.fstick.registry_service.TestData.plugin;

@ExtendWith(MockitoExtension.class)
class AccessGuardTest {

    private static final UUID PLUGIN_ID = UUID.randomUUID();
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final String CHAT = "!room:example.org";

    @Mock private PluginsRepository pluginsRepository;
    @Mock private InstallationClient installationClient;
    @Mock private IntegrationClient integrationClient;

    private AccessGuard guard;
    private PluginData plugin;

    @BeforeEach
    void setUp() {
        guard = new AccessGuard(pluginsRepository, installationClient, integrationClient);
        plugin = plugin(PLUGIN_ID, AUTHOR);
    }

    // ── requireAuthor ─────────────────────────────────────────────────────────

    @Test
    void requireAuthor_author_returnsPlugin() {
        when(pluginsRepository.findPlugin(PLUGIN_ID)).thenReturn(Optional.of(plugin));

        assertSame(plugin, guard.requireAuthor(PLUGIN_ID, AUTHOR));
    }

    @Test
    void requireAuthor_stranger_throws403() {
        when(pluginsRepository.findPlugin(PLUGIN_ID)).thenReturn(Optional.of(plugin));

        ApiException ex = assertThrows(ApiException.class, () -> guard.requireAuthor(PLUGIN_ID, STRANGER));

        assertEquals(403, ex.getStatus().value());
        assertEquals("not_author", ex.getCode());
    }

    @Test
    void requireAuthor_unknownPlugin_throws404() {
        when(pluginsRepository.findPlugin(PLUGIN_ID)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> guard.requireAuthor(PLUGIN_ID, AUTHOR));

        assertEquals(404, ex.getStatus().value());
        assertEquals("plugin_not_found", ex.getCode());
    }

    @Test
    void isModerator_isNotImplementedYet() {
        assertFalse(guard.isModerator(AUTHOR));
    }

    // ── canReadBranch ─────────────────────────────────────────────────────────

    @Test
    void canReadBranch_released_anyone() {
        Branch released = branch(PLUGIN_ID, BranchStatus.RELEASED);

        assertTrue(guard.canReadBranch(null, plugin, released, null));
        assertTrue(guard.canReadBranch(STRANGER, plugin, released, null));
        assertTrue(guard.canReadBranch(AUTHOR, plugin, released, null));
    }

    @Test
    void canReadBranch_candidates_authorOnly() {
        for (BranchStatus status : List.of(BranchStatus.WAITING_APPROVE, BranchStatus.APPROVING)) {
            Branch candidate = branch(PLUGIN_ID, status);

            assertTrue(guard.canReadBranch(AUTHOR, plugin, candidate, null));
            assertFalse(guard.canReadBranch(STRANGER, plugin, candidate, null));
            assertFalse(guard.canReadBranch(null, plugin, candidate, null));
        }
    }

    @Test
    void canReadBranch_rejectedAndCancelled_authorOnly() {
        for (BranchStatus status : List.of(BranchStatus.REJECTED, BranchStatus.CANCELLED)) {
            Branch branch = branch(PLUGIN_ID, status);

            assertTrue(guard.canReadBranch(AUTHOR, plugin, branch, null));
            assertFalse(guard.canReadBranch(STRANGER, plugin, branch, CHAT));
        }
    }

    @Test
    void canReadBranch_working_authorWithoutChatLookup() {
        Branch dev = branch(PLUGIN_ID, BranchStatus.WORKING);

        assertTrue(guard.canReadBranch(AUTHOR, plugin, dev, null));

        verifyNoInteractions(installationClient, integrationClient);
    }

    @Test
    void canReadBranch_working_strangerWithoutChat_false() {
        Branch dev = branch(PLUGIN_ID, BranchStatus.WORKING);

        assertFalse(guard.canReadBranch(STRANGER, plugin, dev, null));
        assertFalse(guard.canReadBranch(null, plugin, dev, CHAT));

        verifyNoInteractions(installationClient, integrationClient);
    }

    @Test
    void canReadBranch_working_memberOfChatInstalledOnBranch_true() {
        Branch dev = branch(PLUGIN_ID, BranchStatus.WORKING);
        when(installationClient.resolve(PLUGIN_ID, CHAT))
                .thenReturn(Optional.of(new InstallationClient.Resolved(dev.getId(), "WORKING")));
        when(integrationClient.isMember(CHAT, STRANGER)).thenReturn(true);

        assertTrue(guard.canReadBranch(STRANGER, plugin, dev, CHAT));
    }

    @Test
    void canReadBranch_working_notAMember_false() {
        Branch dev = branch(PLUGIN_ID, BranchStatus.WORKING);
        when(installationClient.resolve(PLUGIN_ID, CHAT))
                .thenReturn(Optional.of(new InstallationClient.Resolved(dev.getId(), "WORKING")));
        when(integrationClient.isMember(CHAT, STRANGER)).thenReturn(false);

        assertFalse(guard.canReadBranch(STRANGER, plugin, dev, CHAT));
    }

    @Test
    void canReadBranch_working_chatRunsAnotherBranch_falseWithoutMembershipCheck() {
        Branch dev = branch(PLUGIN_ID, BranchStatus.WORKING);
        when(installationClient.resolve(PLUGIN_ID, CHAT))
                .thenReturn(Optional.of(new InstallationClient.Resolved(UUID.randomUUID(), "RELEASED")));

        assertFalse(guard.canReadBranch(STRANGER, plugin, dev, CHAT));

        verifyNoInteractions(integrationClient);
    }

    @Test
    void canReadBranch_working_pluginNotInstalledInChat_false() {
        Branch dev = branch(PLUGIN_ID, BranchStatus.WORKING);
        when(installationClient.resolve(PLUGIN_ID, CHAT)).thenReturn(Optional.empty());

        assertFalse(guard.canReadBranch(STRANGER, plugin, dev, CHAT));
    }

    // ── visibleBranches ───────────────────────────────────────────────────────

    @Test
    void visibleBranches_author_seesAll() {
        List<Branch> all = allStatuses();

        assertEquals(all, guard.visibleBranches(AUTHOR, plugin, all, null));
    }

    @Test
    void visibleBranches_stranger_seesReleasedOnly() {
        List<Branch> visible = guard.visibleBranches(STRANGER, plugin, allStatuses(), null);

        assertEquals(1, visible.size());
        assertEquals(BranchStatus.RELEASED, visible.get(0).getStatus());
    }

    private static List<Branch> allStatuses() {
        return Arrays.stream(BranchStatus.values()).map(status -> branch(PLUGIN_ID, status)).toList();
    }
}
