package com.julio.lifeorganizer.transactions.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    // First page (no cursor). Split from the keyset variant because PostgreSQL cannot infer
    // bind parameter types when all cursor parameters are NULL inside an OR predicate.
    @Query("""
            SELECT t FROM TransactionEntity t
            WHERE t.userId = :userId
              AND t.deletedAt IS NULL
              AND (:from IS NULL OR t.transactionDate >= :from)
              AND (:to   IS NULL OR t.transactionDate <= :to)
            ORDER BY t.transactionDate DESC, t.id DESC
            """)
    List<TransactionEntity> findFirstPage(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    // Subsequent pages with composite keyset (Amendment 1).
    @Query("""
            SELECT t FROM TransactionEntity t
            WHERE t.userId = :userId
              AND t.deletedAt IS NULL
              AND (:from IS NULL OR t.transactionDate >= :from)
              AND (:to   IS NULL OR t.transactionDate <= :to)
              AND ( t.transactionDate <  :cursorDate
                 OR (t.transactionDate = :cursorDate AND t.id < :cursorId) )
            ORDER BY t.transactionDate DESC, t.id DESC
            """)
    List<TransactionEntity> findPageAfterCursor(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("cursorDate") LocalDate cursorDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    Optional<TransactionEntity> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
