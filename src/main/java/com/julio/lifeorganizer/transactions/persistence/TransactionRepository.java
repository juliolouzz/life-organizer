package com.julio.lifeorganizer.transactions.persistence;

import com.julio.lifeorganizer.insights.persistence.CategoryTotalRow;
import com.julio.lifeorganizer.insights.persistence.DailyBucketRow;
import com.julio.lifeorganizer.insights.persistence.TypeSumRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    // First page (no cursor).
    //
    // We use COALESCE(:from, t.transactionDate) instead of "(:from IS NULL OR
    // t.transactionDate >= :from)" because Hibernate sends naked bind
    // parameters in the "IS NULL" form, and PostgreSQL refuses prepared
    // statements where it cannot infer the parameter type. COALESCE pins the
    // type to the column's type, so when the caller passes null the predicate
    // becomes "t.transactionDate >= t.transactionDate" (always true).
    @Query("""
            SELECT t FROM TransactionEntity t
            WHERE t.userId = :userId
              AND t.deletedAt IS NULL
              AND t.transactionDate >= COALESCE(:from, t.transactionDate)
              AND t.transactionDate <= COALESCE(:to,   t.transactionDate)
            ORDER BY t.transactionDate DESC, t.id DESC
            """)
    List<TransactionEntity> findFirstPage(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    // Subsequent pages with composite keyset (Amendment 1). Same COALESCE trick
    // for nullable from/to; the cursor params are required so they stay as-is.
    @Query("""
            SELECT t FROM TransactionEntity t
            WHERE t.userId = :userId
              AND t.deletedAt IS NULL
              AND t.transactionDate >= COALESCE(:from, t.transactionDate)
              AND t.transactionDate <= COALESCE(:to,   t.transactionDate)
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

    // Aggregations for the Slice 3 Insights endpoints.
    // All scope by userId + exclude soft-deleted rows; the partial index
    // idx_transactions_user_active backs the date-range predicate.

    @Query("""
            SELECT new com.julio.lifeorganizer.insights.persistence.TypeSumRow(
                t.type, SUM(t.amount), COUNT(t))
            FROM TransactionEntity t
            WHERE t.userId = :userId
              AND t.deletedAt IS NULL
              AND t.transactionDate >= :from
              AND t.transactionDate <= :to
            GROUP BY t.type
            """)
    List<TypeSumRow> sumByType(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT new com.julio.lifeorganizer.insights.persistence.CategoryTotalRow(
                t.category, t.type, SUM(t.amount), COUNT(t))
            FROM TransactionEntity t
            WHERE t.userId = :userId
              AND t.deletedAt IS NULL
              AND t.transactionDate >= :from
              AND t.transactionDate <= :to
            GROUP BY t.category, t.type
            ORDER BY SUM(t.amount) DESC
            """)
    List<CategoryTotalRow> sumByCategoryAndType(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT new com.julio.lifeorganizer.insights.persistence.DailyBucketRow(
                t.transactionDate, t.type, SUM(t.amount))
            FROM TransactionEntity t
            WHERE t.userId = :userId
              AND t.deletedAt IS NULL
              AND t.transactionDate >= :from
              AND t.transactionDate <= :to
            GROUP BY t.transactionDate, t.type
            ORDER BY t.transactionDate ASC
            """)
    List<DailyBucketRow> sumByDayAndType(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Slice 10 trends: returns the raw (date, category, type, amount) rows in
     * the trends window. The service aggregates by (year, month) in memory -
     * portability across dialects matters more than skipping a handful of
     * groupings on personal-data volumes.
     */
    @Query("""
            SELECT t FROM TransactionEntity t
            WHERE t.userId = :userId
              AND t.deletedAt IS NULL
              AND t.transactionDate >= :from
              AND t.transactionDate <= :to
            ORDER BY t.transactionDate ASC
            """)
    List<TransactionEntity> findInWindowForTrends(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
