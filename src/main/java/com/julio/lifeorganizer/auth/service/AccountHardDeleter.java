package com.julio.lifeorganizer.auth.service;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performs the per-user explicit DELETE cascade. Separate from
 * {@link AccountLifecycleJob} so that the @Transactional boundary is honored
 * (Spring proxies do not intercept self-invocation).
 */
@Component
public class AccountHardDeleter {

    private static final Logger log = LoggerFactory.getLogger(AccountHardDeleter.class);

    private final EntityManager em;

    public AccountHardDeleter(EntityManager em) {
        this.em = em;
    }

    @Transactional
    public void hardDelete(long userId) {
        int recurring = em.createQuery(
                "DELETE FROM RecurringTransactionEntity r WHERE r.userId = :u")
                .setParameter("u", userId).executeUpdate();
        int budgets = em.createQuery(
                "DELETE FROM BudgetEntity b WHERE b.userId = :u")
                .setParameter("u", userId).executeUpdate();
        int transactions = em.createQuery(
                "DELETE FROM TransactionEntity t WHERE t.userId = :u")
                .setParameter("u", userId).executeUpdate();
        int categories = em.createQuery(
                "DELETE FROM CategoryEntity c WHERE c.userId = :u")
                .setParameter("u", userId).executeUpdate();
        int users = em.createQuery(
                "DELETE FROM UserEntity u WHERE u.id = :u")
                .setParameter("u", userId).executeUpdate();
        log.info("hard delete user {}: recurring={}, budgets={}, transactions={}, "
                + "categories={}, user={}",
                userId, recurring, budgets, transactions, categories, users);
    }
}
