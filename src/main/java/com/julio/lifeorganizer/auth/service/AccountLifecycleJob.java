package com.julio.lifeorganizer.auth.service;

import com.julio.lifeorganizer.auth.persistence.UserEntity;
import com.julio.lifeorganizer.auth.persistence.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hard-delete job (Slice 9, decision 6 / ADR-S9-02).
 *
 * <p>Daily at 03:00 UTC, finds users whose deletion_scheduled_at is in the past
 * and removes them along with every row they own. The actual DELETE cascade
 * lives in {@link AccountHardDeleter} so the Spring @Transactional proxy is
 * honored (this class would self-invoke and bypass it).
 *
 * <p>Each user is processed in its own transaction so a partial failure does
 * not block the rest of the batch.
 */
@Component
public class AccountLifecycleJob {

    private static final Logger log = LoggerFactory.getLogger(AccountLifecycleJob.class);

    private final UserRepository userRepository;
    private final AccountHardDeleter hardDeleter;
    private final Clock clock;

    public AccountLifecycleJob(UserRepository userRepository,
                               AccountHardDeleter hardDeleter,
                               Clock clock) {
        this.userRepository = userRepository;
        this.hardDeleter = hardDeleter;
        this.clock = clock;
    }

    /**
     * Cron: 03:00 UTC every day. The exact second does not matter; we just want
     * a low-traffic window. The job is idempotent - re-running after a crash
     * picks up anything that was not finished.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void runScheduled() {
        runOnce();
    }

    /**
     * Visible for tests and for any future ops endpoint. Returns the number of
     * users successfully hard-deleted.
     */
    public int runOnce() {
        Instant cutoff = clock.instant();
        List<UserEntity> due = userRepository.findUsersDueForHardDelete(cutoff);
        log.info("hard delete sweep: cutoff={} due={}", cutoff, due.size());
        int deleted = 0;
        for (UserEntity user : due) {
            try {
                hardDeleter.hardDelete(user.getId());
                deleted++;
            } catch (RuntimeException ex) {
                log.error("hard delete failed for user {} - will retry on next run",
                        user.getId(), ex);
            }
        }
        if (deleted > 0) {
            log.info("hard delete completed: {} user(s) removed", deleted);
        }
        return deleted;
    }
}
