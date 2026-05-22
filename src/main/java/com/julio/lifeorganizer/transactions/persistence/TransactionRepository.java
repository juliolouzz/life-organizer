package com.julio.lifeorganizer.transactions.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    // Canonical list query. Composite keyset predicate (Amendment 1):
    //   (transaction_date <  :cursorDate)
    //   OR (transaction_date = :cursorDate AND id < :cursorId)
    // Caller passes both as null for the first page. ORDER BY matches the partial index exactly.
    @Query("""
            SELECT t FROM TransactionEntity t
            WHERE t.userId = :userId
              AND t.deletedAt IS NULL
              AND (:from IS NULL OR t.transactionDate >= :from)
              AND (:to   IS NULL OR t.transactionDate <= :to)
              AND (
                    :cursorDate IS NULL
                 OR  t.transactionDate <  :cursorDate
                 OR (t.transactionDate = :cursorDate AND t.id < :cursorId)
              )
            ORDER BY t.transactionDate DESC, t.id DESC
            """)
    List<TransactionEntity> findPage(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("cursorDate") LocalDate cursorDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    Optional<TransactionEntity> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
