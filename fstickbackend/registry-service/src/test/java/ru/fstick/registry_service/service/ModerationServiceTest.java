package ru.fstick.registry_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import ru.fstick.registry_service.client.InstallationClient;
import ru.fstick.registry_service.client.IntegrationClient;
import ru.fstick.registry_service.dto.BranchStatus;
import ru.fstick.registry_service.dto.api.moderation.ApproveResponse;
import ru.fstick.registry_service.dto.api.moderation.BranchStatusResponse;
import ru.fstick.registry_service.dto.api.moderation.ClaimResponse;
import ru.fstick.registry_service.dto.api.moderation.PublishRequest;
import ru.fstick.registry_service.dto.api.moderation.PublishResponse;
import ru.fstick.registry_service.dto.api.moderation.QueueView;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.dto.model.ModerationAction;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.dto.model.QueueItem;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.repository.BranchRepository;
import ru.fstick.registry_service.repository.ModerationLogRepository;
import ru.fstick.registry_service.repository.PluginsRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static ru.fstick.registry_service.TestData.branch;
import static ru.fstick.registry_service.TestData.plugin;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModerationServiceTest {

    private static final UUID PLUGIN_ID = UUID.randomUUID();
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID MODERATOR = UUID.randomUUID();
    private static final UUID OTHER_MODERATOR = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    @Mock private PluginsRepository pluginsRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ModerationLogRepository logRepository;
    @Mock private InstallationClient installationClient;
    @Mock private IntegrationClient integrationClient;
    @Mock private ModeratorService moderatorService;
    @Mock private AuthorNotifier authorNotifier;
    @Mock private TransactionTemplate transactionTemplate;

    private ModerationService service;
    private PluginData plugin;
    private Branch dev;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        AccessGuard guard = new AccessGuard(pluginsRepository, installationClient, integrationClient, moderatorService);
        service = new ModerationService(pluginsRepository, branchRepository, logRepository, guard,
                authorNotifier, transactionTemplate);

        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
        when(moderatorService.isModerator(MODERATOR)).thenReturn(true);
        when(moderatorService.isModerator(OTHER_MODERATOR)).thenReturn(true);

        plugin = plugin(PLUGIN_ID, AUTHOR);
        when(pluginsRepository.findPlugin(PLUGIN_ID)).thenReturn(Optional.of(plugin));
        when(pluginsRepository.getPlugin(PLUGIN_ID)).thenReturn(plugin);

        dev = register(BranchStatus.WORKING, null);
        when(branchRepository.findOpenCandidate(PLUGIN_ID)).thenReturn(Optional.empty());
        when(branchRepository.findReleasedSemvers(PLUGIN_ID)).thenReturn(List.of());
    }

    private Branch register(BranchStatus status, UUID claimedBy) {
        Branch branch = branch(PLUGIN_ID, status);
        branch.setClaimedBy(claimedBy);
        when(branchRepository.lockForUpdate(branch.getId())).thenReturn(Optional.of(branch));
        return branch;
    }

    private static ApiException thrown(Runnable action) {
        return assertThrows(ApiException.class, action::run);
    }

    private PublishRequest publishRequest(String semver) {
        return new PublishRequest(dev.getId(), semver, "notes");
    }

    // ── publish ───────────────────────────────────────────────────────────────

    @Test
    void publish_happyPath_createsCandidateAndLogsOnce() {
        UUID candidateId = UUID.randomUUID();
        when(branchRepository.createCandidate(dev, "1.2.0", "notes")).thenReturn(candidateId);

        PublishResponse response = service.publish(PLUGIN_ID, AUTHOR, publishRequest("1.2.0"));

        assertEquals(candidateId, response.branchId());
        assertEquals("1.2.0", response.semver());
        assertEquals("WAITING_APPROVE", response.status());
        verify(logRepository, times(1)).append(PLUGIN_ID, candidateId, ModerationAction.PUBLISH, AUTHOR, null);
        verifyNoMoreInteractions(logRepository);
    }

    @Test
    void publish_openCandidateExists_is409() {
        when(branchRepository.findOpenCandidate(PLUGIN_ID))
                .thenReturn(Optional.of(branch(PLUGIN_ID, BranchStatus.WAITING_APPROVE)));

        ApiException ex = thrown(() -> service.publish(PLUGIN_ID, AUTHOR, publishRequest("1.2.0")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("open_candidate_exists", ex.getCode());
        verifyNoInteractions(logRepository);
    }

    @Test
    void publish_uniqueIndexRace_is409() {
        when(branchRepository.createCandidate(any(), anyString(), any())).thenThrow(new DuplicateKeyException("dup"));

        ApiException ex = thrown(() -> service.publish(PLUGIN_ID, AUTHOR, publishRequest("1.2.0")));

        assertEquals("open_candidate_exists", ex.getCode());
        verifyNoInteractions(logRepository);
    }

    @Test
    void publish_semverNotGreaterThanReleased_is422() {
        when(branchRepository.findReleasedSemvers(PLUGIN_ID)).thenReturn(List.of("1.2.0", "1.10.0"));

        for (String semver : List.of("1.10.0", "1.9.9", "0.99.99")) {
            ApiException ex = thrown(() -> service.publish(PLUGIN_ID, AUTHOR, publishRequest(semver)));
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
            assertEquals("semver_not_greater", ex.getCode());
        }
        verifyNoInteractions(logRepository);
    }

    @Test
    void publish_semverComparedNumerically() {
        when(branchRepository.findReleasedSemvers(PLUGIN_ID)).thenReturn(List.of("1.9.0"));
        when(branchRepository.createCandidate(dev, "1.10.0", "notes")).thenReturn(UUID.randomUUID());

        assertEquals("1.10.0", service.publish(PLUGIN_ID, AUTHOR, publishRequest("1.10.0")).semver());
    }

    @Test
    void publish_invalidSemver_is422() {
        for (String semver : List.of("1.2", "v1.2.0", "1.2.0-beta", "1.2.x", "")) {
            ApiException ex = thrown(() -> service.publish(PLUGIN_ID, AUTHOR, publishRequest(semver)));
            assertEquals("invalid_semver", ex.getCode());
        }
    }

    @Test
    void publish_changelogTooLong_is422() {
        PublishRequest request = new PublishRequest(dev.getId(), "1.0.0", "x".repeat(2001));

        assertEquals("changelog_too_long", thrown(() -> service.publish(PLUGIN_ID, AUTHOR, request)).getCode());
    }

    @Test
    void publish_sourceIsNotDevBranch_is422() {
        Branch released = register(BranchStatus.RELEASED, null);

        ApiException ex = thrown(() -> service.publish(PLUGIN_ID, AUTHOR,
                new PublishRequest(released.getId(), "2.0.0", null)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("not_dev_branch", ex.getCode());
    }

    @Test
    void publish_sourceOfAnotherPlugin_is404() {
        Branch foreign = branch(UUID.randomUUID(), BranchStatus.WORKING);
        when(branchRepository.lockForUpdate(foreign.getId())).thenReturn(Optional.of(foreign));

        ApiException ex = thrown(() -> service.publish(PLUGIN_ID, AUTHOR,
                new PublishRequest(foreign.getId(), "1.0.0", null)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void publish_nonAuthor_is403() {
        ApiException ex = thrown(() -> service.publish(PLUGIN_ID, STRANGER, publishRequest("1.0.0")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("not_author", ex.getCode());
        verifyNoInteractions(logRepository);
    }

    // ── queue ─────────────────────────────────────────────────────────────────

    @Test
    void queue_moderator_returnsItemsWithAuthorId() {
        QueueItem item = QueueItem.builder().pluginId(PLUGIN_ID).pluginName("P").branchId(UUID.randomUUID())
                .semver("1.0.0").status(BranchStatus.WAITING_APPROVE).authorId(AUTHOR).build();
        List<BranchStatus> both = List.of(BranchStatus.WAITING_APPROVE, BranchStatus.APPROVING);
        when(branchRepository.findQueue(both, 0, 20)).thenReturn(List.of(item));
        when(branchRepository.countQueue(both)).thenReturn(7);

        QueueView view = service.queue(MODERATOR, null, 0, 20);

        assertEquals(7, view.total());
        assertEquals(0, view.page());
        assertEquals(AUTHOR, view.items().get(0).author().id());
        assertEquals("WAITING_APPROVE", view.items().get(0).status());
    }

    @Test
    void queue_statusFilterAndPaging() {
        List<BranchStatus> approving = List.of(BranchStatus.APPROVING);
        when(branchRepository.findQueue(approving, 40, 20)).thenReturn(List.of());

        service.queue(MODERATOR, "APPROVING", 2, 20);

        verify(branchRepository).findQueue(approving, 40, 20);
    }

    @Test
    void queue_invalidStatus_is422() {
        assertEquals("invalid_status", thrown(() -> service.queue(MODERATOR, "RELEASED", 0, 20)).getCode());
    }

    @Test
    void queue_nonModerator_is403() {
        ApiException ex = thrown(() -> service.queue(STRANGER, null, 0, 20));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("not_moderator", ex.getCode());
        verifyNoInteractions(branchRepository);
    }

    // ── claim ─────────────────────────────────────────────────────────────────

    @Test
    void claim_waiting_movesToApprovingAndLogsOnce() {
        Branch candidate = register(BranchStatus.WAITING_APPROVE, null);

        ClaimResponse response = service.claim(PLUGIN_ID, candidate.getId(), MODERATOR);

        assertEquals("APPROVING", response.status());
        assertEquals(MODERATOR, response.claimedBy());
        verify(branchRepository).markClaimed(candidate.getId(), MODERATOR);
        verify(logRepository, times(1)).append(PLUGIN_ID, candidate.getId(), ModerationAction.CLAIM, MODERATOR, null);
    }

    @Test
    void claim_sameModeratorAgain_is200WithoutLog() {
        Branch candidate = register(BranchStatus.APPROVING, MODERATOR);

        ClaimResponse response = service.claim(PLUGIN_ID, candidate.getId(), MODERATOR);

        assertEquals(MODERATOR, response.claimedBy());
        verify(branchRepository, never()).markClaimed(any(), any());
        verifyNoInteractions(logRepository);
    }

    @Test
    void claim_byAnotherModerator_is409ClaimedByOther() {
        Branch candidate = register(BranchStatus.APPROVING, MODERATOR);

        ApiException ex = thrown(() -> service.claim(PLUGIN_ID, candidate.getId(), OTHER_MODERATOR));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("claimed_by_other", ex.getCode());
        verifyNoInteractions(logRepository);
    }

    @Test
    void claim_terminalBranch_is409InvalidTransition() {
        Branch released = register(BranchStatus.RELEASED, null);

        assertEquals("invalid_transition", thrown(() -> service.claim(PLUGIN_ID, released.getId(), MODERATOR)).getCode());
    }

    @Test
    void claim_nonModerator_is403() {
        Branch candidate = register(BranchStatus.WAITING_APPROVE, null);

        assertEquals("not_moderator", thrown(() -> service.claim(PLUGIN_ID, candidate.getId(), STRANGER)).getCode());
        verifyNoInteractions(logRepository);
    }

    // ── approve ───────────────────────────────────────────────────────────────

    @Test
    void approve_byClaimer_releasesClearsRejectionLogsAndNotifies() {
        Branch candidate = register(BranchStatus.APPROVING, MODERATOR);

        ApproveResponse response = service.approve(PLUGIN_ID, candidate.getId(), MODERATOR);

        assertEquals("RELEASED", response.status());
        assertEquals("1.0.0", response.semver());
        verify(branchRepository).markReleased(candidate.getId());
        verify(pluginsRepository).clearLastRejection(PLUGIN_ID);
        verify(logRepository, times(1)).append(PLUGIN_ID, candidate.getId(), ModerationAction.APPROVE, MODERATOR, null);
        verify(authorNotifier).released(plugin, "1.0.0");
    }

    @Test
    void approve_byNonClaimerModerator_is403NotClaimer() {
        Branch candidate = register(BranchStatus.APPROVING, MODERATOR);

        ApiException ex = thrown(() -> service.approve(PLUGIN_ID, candidate.getId(), OTHER_MODERATOR));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("not_claimer", ex.getCode());
        verifyNoInteractions(logRepository, authorNotifier);
    }

    @Test
    void approve_twice_secondIs200WithoutLogOrNotification() {
        Branch released = register(BranchStatus.RELEASED, MODERATOR);

        ApproveResponse response = service.approve(PLUGIN_ID, released.getId(), MODERATOR);

        assertEquals("RELEASED", response.status());
        verifyNoInteractions(logRepository, authorNotifier);
        verify(branchRepository, never()).markReleased(any());
    }

    @Test
    void approve_unclaimedWaitingBranch_is409() {
        Branch waiting = register(BranchStatus.WAITING_APPROVE, null);

        assertEquals("invalid_transition", thrown(() -> service.approve(PLUGIN_ID, waiting.getId(), MODERATOR)).getCode());
        verifyNoInteractions(logRepository, authorNotifier);
    }

    @Test
    void approve_nonModerator_is403() {
        Branch candidate = register(BranchStatus.APPROVING, MODERATOR);

        assertEquals("not_moderator", thrown(() -> service.approve(PLUGIN_ID, candidate.getId(), STRANGER)).getCode());
    }

    // ── reject ────────────────────────────────────────────────────────────────

    @Test
    void reject_fromWaitingByAnyModerator_setsLastRejectionLogsAndNotifies() {
        Branch candidate = register(BranchStatus.WAITING_APPROVE, null);

        BranchStatusResponse response = service.reject(PLUGIN_ID, candidate.getId(), OTHER_MODERATOR, "needs work");

        assertEquals("REJECTED", response.status());
        verify(branchRepository).markRejected(candidate.getId(), "needs work");
        verify(pluginsRepository).setLastRejection(PLUGIN_ID, "needs work", "1.0.0", candidate.getId());
        verify(logRepository, times(1)).append(PLUGIN_ID, candidate.getId(), ModerationAction.REJECT, OTHER_MODERATOR, "needs work");
        verify(authorNotifier).rejected(plugin, "1.0.0", "needs work");
    }

    @Test
    void reject_fromApprovingByClaimer_succeeds() {
        Branch candidate = register(BranchStatus.APPROVING, MODERATOR);

        assertEquals("REJECTED", service.reject(PLUGIN_ID, candidate.getId(), MODERATOR, "no").status());
        verify(logRepository, times(1)).append(any(), any(), eq(ModerationAction.REJECT), any(), any());
    }

    @Test
    void reject_fromApprovingByNonClaimer_is403() {
        Branch candidate = register(BranchStatus.APPROVING, MODERATOR);

        ApiException ex = thrown(() -> service.reject(PLUGIN_ID, candidate.getId(), OTHER_MODERATOR, "no"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("not_claimer", ex.getCode());
        verifyNoInteractions(logRepository, authorNotifier);
        verify(pluginsRepository, never()).setLastRejection(any(), any(), any(), any());
    }

    @Test
    void reject_twice_secondIs200WithoutSideEffects() {
        Branch rejected = register(BranchStatus.REJECTED, null);

        assertEquals("REJECTED", service.reject(PLUGIN_ID, rejected.getId(), MODERATOR, "no").status());
        verifyNoInteractions(logRepository, authorNotifier);
        verify(pluginsRepository, never()).setLastRejection(any(), any(), any(), any());
    }

    @Test
    void reject_cancelledOrReleased_is409() {
        Branch cancelled = register(BranchStatus.CANCELLED, null);
        Branch released = register(BranchStatus.RELEASED, null);

        assertEquals("invalid_transition", thrown(() -> service.reject(PLUGIN_ID, cancelled.getId(), MODERATOR, "no")).getCode());
        assertEquals("invalid_transition", thrown(() -> service.reject(PLUGIN_ID, released.getId(), MODERATOR, "no")).getCode());
        verifyNoInteractions(logRepository, authorNotifier);
    }

    @Test
    void reject_invalidReason_is422() {
        Branch candidate = register(BranchStatus.WAITING_APPROVE, null);

        for (String reason : new String[]{null, "", "   ", "x".repeat(2001)}) {
            ApiException ex = thrown(() -> service.reject(PLUGIN_ID, candidate.getId(), MODERATOR, reason));
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
            assertEquals("invalid_reason", ex.getCode());
        }
        verifyNoInteractions(logRepository);
    }

    @Test
    void reject_nonModerator_is403() {
        Branch candidate = register(BranchStatus.WAITING_APPROVE, null);

        assertEquals("not_moderator", thrown(() -> service.reject(PLUGIN_ID, candidate.getId(), AUTHOR, "no")).getCode());
    }

    // ── cancel ────────────────────────────────────────────────────────────────

    @Test
    void cancel_fromBothOpenStates_logsOnceEachAndDoesNotNotify() {
        Branch waiting = register(BranchStatus.WAITING_APPROVE, null);
        Branch approving = register(BranchStatus.APPROVING, MODERATOR);

        assertEquals("CANCELLED", service.cancel(PLUGIN_ID, waiting.getId(), AUTHOR).status());
        assertEquals("CANCELLED", service.cancel(PLUGIN_ID, approving.getId(), AUTHOR).status());

        verify(branchRepository).markCancelled(waiting.getId());
        verify(branchRepository).markCancelled(approving.getId());
        verify(logRepository, times(1)).append(PLUGIN_ID, waiting.getId(), ModerationAction.CANCEL, AUTHOR, null);
        verify(logRepository, times(1)).append(PLUGIN_ID, approving.getId(), ModerationAction.CANCEL, AUTHOR, null);
        verify(pluginsRepository, never()).setLastRejection(any(), any(), any(), any());
        verifyNoInteractions(authorNotifier);
    }

    @Test
    void cancel_twice_secondIs200WithoutLog() {
        Branch cancelled = register(BranchStatus.CANCELLED, null);

        assertEquals("CANCELLED", service.cancel(PLUGIN_ID, cancelled.getId(), AUTHOR).status());
        verifyNoInteractions(logRepository);
        verify(branchRepository, never()).markCancelled(any());
    }

    @Test
    void cancel_releasedOrRejected_is409() {
        Branch released = register(BranchStatus.RELEASED, null);
        Branch rejected = register(BranchStatus.REJECTED, null);

        assertEquals("invalid_transition", thrown(() -> service.cancel(PLUGIN_ID, released.getId(), AUTHOR)).getCode());
        assertEquals("invalid_transition", thrown(() -> service.cancel(PLUGIN_ID, rejected.getId(), AUTHOR)).getCode());
        verifyNoInteractions(logRepository);
    }

    @Test
    void cancel_nonAuthor_is403() {
        Branch waiting = register(BranchStatus.WAITING_APPROVE, null);

        assertEquals("not_author", thrown(() -> service.cancel(PLUGIN_ID, waiting.getId(), STRANGER)).getCode());
        verifyNoInteractions(logRepository);
    }

    @Test
    void transitions_unknownBranch_is404() {
        UUID unknown = UUID.randomUUID();
        when(branchRepository.lockForUpdate(unknown)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, thrown(() -> service.claim(PLUGIN_ID, unknown, MODERATOR)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, thrown(() -> service.cancel(PLUGIN_ID, unknown, AUTHOR)).getStatus());
    }
}
