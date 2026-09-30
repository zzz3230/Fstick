package ru.fstick.registry_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.fstick.registry_service.dto.BranchStatus;
import ru.fstick.registry_service.dto.api.moderation.ApproveResponse;
import ru.fstick.registry_service.dto.api.moderation.BranchStatusResponse;
import ru.fstick.registry_service.dto.api.moderation.ClaimResponse;
import ru.fstick.registry_service.dto.api.moderation.PublishRequest;
import ru.fstick.registry_service.dto.api.moderation.PublishResponse;
import ru.fstick.registry_service.dto.api.moderation.QueueView;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.dto.model.ModerationAction;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.repository.BranchRepository;
import ru.fstick.registry_service.repository.ModerationLogRepository;
import ru.fstick.registry_service.repository.PluginsRepository;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ModerationService {

    static final int MAX_TEXT = 2000;
    static final int MAX_PAGE_SIZE = 100;

    private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final List<BranchStatus> OPEN = List.of(BranchStatus.WAITING_APPROVE, BranchStatus.APPROVING);

    private final PluginsRepository pluginsRepository;
    private final BranchRepository branchRepository;
    private final ModerationLogRepository logRepository;
    private final AccessGuard accessGuard;
    private final AuthorNotifier authorNotifier;
    private final TransactionTemplate transactionTemplate;

    public PublishResponse publish(UUID pluginId, UUID caller, PublishRequest request) {
        accessGuard.requireAuthor(pluginId, caller);
        validatePublish(request);
        try {
            return transactionTemplate.execute(status -> publishLocked(pluginId, caller, request));
        } catch (DuplicateKeyException ex) {
            throw openCandidateExists();
        }
    }

    public QueueView queue(UUID caller, String status, int page, int limit) {
        requireModerator(caller);
        List<BranchStatus> statuses = queueStatuses(status);
        int safePage = Math.max(page, 0);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);

        List<QueueView.Item> items = branchRepository.findQueue(statuses, safePage * safeLimit, safeLimit).stream()
                .map(item -> new QueueView.Item(item.getPluginId(), item.getPluginName(), item.getBranchId(),
                        item.getSemver(), item.getChangelog(), item.getStatus().name(),
                        new QueueView.Author(item.getAuthorId()), item.getSubmittedAt(), item.getClaimedBy()))
                .toList();
        return new QueueView(items, safePage, branchRepository.countQueue(statuses));
    }

    public ClaimResponse claim(UUID pluginId, UUID branchId, UUID caller) {
        requireModerator(caller);
        return transactionTemplate.execute(status -> {
            Branch branch = lock(pluginId, branchId);
            switch (branch.getStatus()) {
                case WAITING_APPROVE -> {
                    branchRepository.markClaimed(branchId, caller);
                    logRepository.append(pluginId, branchId, ModerationAction.CLAIM, caller, null);
                }
                case APPROVING -> {
                    if (!caller.equals(branch.getClaimedBy())) {
                        throw new ApiException(HttpStatus.CONFLICT, "claimed_by_other",
                                "Branch is claimed by another moderator");
                    }
                }
                default -> throw invalidTransition(branch);
            }
            return new ClaimResponse(branchId, BranchStatus.APPROVING.name(), caller);
        });
    }

    public ApproveResponse approve(UUID pluginId, UUID branchId, UUID caller) {
        requireModerator(caller);
        Outcome outcome = transactionTemplate.execute(status -> {
            Branch branch = lock(pluginId, branchId);
            switch (branch.getStatus()) {
                case APPROVING -> {
                    requireClaimer(branch, caller);
                    branchRepository.markReleased(branchId);
                    pluginsRepository.clearLastRejection(pluginId);
                    logRepository.append(pluginId, branchId, ModerationAction.APPROVE, caller, null);
                    return new Outcome(branch, true);
                }
                case RELEASED -> {
                    return new Outcome(branch, false);
                }
                default -> throw invalidTransition(branch);
            }
        });
        if (outcome.changed()) {
            authorNotifier.released(pluginsRepository.getPlugin(pluginId), outcome.branch().getSemver());
        }
        return new ApproveResponse(branchId, BranchStatus.RELEASED.name(), outcome.branch().getSemver());
    }

    public BranchStatusResponse reject(UUID pluginId, UUID branchId, UUID caller, String reason) {
        requireModerator(caller);
        if (reason == null || reason.isBlank() || reason.length() > MAX_TEXT) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_reason",
                    "reason is required and must be at most " + MAX_TEXT + " characters");
        }
        Outcome outcome = transactionTemplate.execute(status -> {
            Branch branch = lock(pluginId, branchId);
            switch (branch.getStatus()) {
                case WAITING_APPROVE, APPROVING -> {
                    if (branch.getStatus() == BranchStatus.APPROVING) {
                        requireClaimer(branch, caller);
                    }
                    branchRepository.markRejected(branchId, reason);
                    pluginsRepository.setLastRejection(pluginId, reason, branch.getSemver(), branchId);
                    logRepository.append(pluginId, branchId, ModerationAction.REJECT, caller, reason);
                    return new Outcome(branch, true);
                }
                case REJECTED -> {
                    return new Outcome(branch, false);
                }
                default -> throw invalidTransition(branch);
            }
        });
        if (outcome.changed()) {
            authorNotifier.rejected(pluginsRepository.getPlugin(pluginId), outcome.branch().getSemver(), reason);
        }
        return new BranchStatusResponse(branchId, BranchStatus.REJECTED.name());
    }

    public BranchStatusResponse cancel(UUID pluginId, UUID branchId, UUID caller) {
        accessGuard.requireAuthor(pluginId, caller);
        return transactionTemplate.execute(status -> {
            Branch branch = lock(pluginId, branchId);
            switch (branch.getStatus()) {
                case WAITING_APPROVE, APPROVING -> {
                    branchRepository.markCancelled(branchId);
                    logRepository.append(pluginId, branchId, ModerationAction.CANCEL, caller, null);
                }
                case CANCELLED -> { }
                default -> throw invalidTransition(branch);
            }
            return new BranchStatusResponse(branchId, BranchStatus.CANCELLED.name());
        });
    }

    private PublishResponse publishLocked(UUID pluginId, UUID caller, PublishRequest request) {
        Branch source = lock(pluginId, request.sourceBranchId());
        if (source.getStatus() != BranchStatus.WORKING) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "not_dev_branch",
                    "Only the development branch can be published");
        }
        if (branchRepository.findOpenCandidate(pluginId).isPresent()) {
            throw openCandidateExists();
        }
        boolean greater = branchRepository.findReleasedSemvers(pluginId).stream()
                .allMatch(released -> compare(request.semver(), released) > 0);
        if (!greater) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "semver_not_greater",
                    "semver must be greater than the latest released version");
        }

        UUID branchId = branchRepository.createCandidate(source, request.semver(), request.changelog());
        logRepository.append(pluginId, branchId, ModerationAction.PUBLISH, caller, null);
        return new PublishResponse(branchId, request.semver(), BranchStatus.WAITING_APPROVE.name());
    }

    private static void validatePublish(PublishRequest request) {
        if (request.sourceBranchId() == null || request.semver() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed",
                    "source_branch_id and semver are required");
        }
        if (!SEMVER.matcher(request.semver()).matches()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_semver",
                    "semver must look like 1.2.0");
        }
        if (request.changelog() != null && request.changelog().length() > MAX_TEXT) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "changelog_too_long",
                    "changelog must be at most " + MAX_TEXT + " characters");
        }
    }

    static int compare(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < 3; i++) {
            int result = new BigInteger(a[i]).compareTo(new BigInteger(b[i]));
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static List<BranchStatus> queueStatuses(String status) {
        if (status == null || status.isBlank()) {
            return OPEN;
        }
        return OPEN.stream()
                .filter(open -> open.name().equals(status))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_status",
                        "status must be WAITING_APPROVE or APPROVING"));
    }

    private void requireModerator(UUID caller) {
        if (!accessGuard.isModerator(caller)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_moderator", "Only moderators can do this");
        }
    }

    private static void requireClaimer(Branch branch, UUID caller) {
        if (!caller.equals(branch.getClaimedBy())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_claimer", "Only the claiming moderator can do this");
        }
    }

    private Branch lock(UUID pluginId, UUID branchId) {
        return branchRepository.lockForUpdate(branchId)
                .filter(branch -> branch.getPluginId().equals(pluginId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "branch_not_found",
                        "Branch not found: " + branchId));
    }

    private static ApiException invalidTransition(Branch branch) {
        return new ApiException(HttpStatus.CONFLICT, "invalid_transition",
                "Branch in status " + branch.getStatus() + " cannot make this transition");
    }

    private static ApiException openCandidateExists() {
        return new ApiException(HttpStatus.CONFLICT, "open_candidate_exists",
                "The plugin already has a candidate under review");
    }

    private record Outcome(Branch branch, boolean changed) {}
}
