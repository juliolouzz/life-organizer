package com.julio.lifeorganizer.recurring.service;

import com.julio.lifeorganizer.categories.persistence.CategoryEntity;
import com.julio.lifeorganizer.categories.persistence.CategoryRepository;
import com.julio.lifeorganizer.recurring.persistence.RecurringTransactionEntity;
import com.julio.lifeorganizer.recurring.persistence.RecurringTransactionRepository;
import com.julio.lifeorganizer.transactions.persistence.TransactionEntity;
import com.julio.lifeorganizer.transactions.persistence.TransactionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catches up due occurrences of recurring transactions for a user.
 * Called from TransactionService.list before fetching, so the listing always
 * reflects every transaction the user "should have" today.
 *
 * Safety: max 365 materialisations per call to avoid runaway loops (e.g., a
 * DAILY rule with a start_date five years ago would otherwise create 1825 rows).
 * If we hit the cap we leave next_due_date where it is so the next list call
 * keeps catching up.
 */
@Service
public class RecurringMaterialiser {

    private static final Logger log = LoggerFactory.getLogger(RecurringMaterialiser.class);
    private static final int MAX_OCCURRENCES_PER_CALL = 365;

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public RecurringMaterialiser(
            RecurringTransactionRepository recurringRepository,
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            Clock clock) {
        this.recurringRepository = recurringRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @Transactional
    public int materialiseFor(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<RecurringTransactionEntity> due = recurringRepository.findDueForUser(userId, today);
        if (due.isEmpty()) return 0;

        int created = 0;
        for (RecurringTransactionEntity template : due) {
            Optional<CategoryEntity> category =
                    categoryRepository.findByIdAndUserId(template.getCategoryId(), userId);
            if (category.isEmpty()) {
                log.warn("Recurring transaction {} references missing category {}; skipping",
                        template.getId(), template.getCategoryId());
                continue;
            }
            String categoryName = category.get().getName();

            int iterations = 0;
            while (template.isDue(today) && iterations < MAX_OCCURRENCES_PER_CALL) {
                TransactionEntity tx = TransactionEntity.createNew(
                        userId, template.getAmount(), template.getType(),
                        categoryName, template.getDescription(), template.getNextDueDate());
                transactionRepository.save(tx);
                template.advanceAfterMaterialise();
                iterations++;
                created++;
            }
            recurringRepository.save(template);

            if (iterations == MAX_OCCURRENCES_PER_CALL) {
                log.warn("Recurring transaction {} hit max occurrences per call ({}); "
                        + "remaining catch-up will happen on subsequent list calls",
                        template.getId(), MAX_OCCURRENCES_PER_CALL);
            }
        }
        return created;
    }
}
