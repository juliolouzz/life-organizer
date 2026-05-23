package com.julio.lifeorganizer.recurring.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransactionEntity, Long> {

    List<RecurringTransactionEntity> findByUserIdOrderByNextDueDateAsc(Long userId);

    Optional<RecurringTransactionEntity> findByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT r FROM RecurringTransactionEntity r
            WHERE r.userId = :userId
              AND r.paused = FALSE
              AND r.nextDueDate <= :today
              AND (r.endDate IS NULL OR r.nextDueDate <= r.endDate)
            """)
    List<RecurringTransactionEntity> findDueForUser(
            @Param("userId") Long userId, @Param("today") LocalDate today);
}
