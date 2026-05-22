package com.julio.lifeorganizer.transactions.persistence;

import com.julio.lifeorganizer.AbstractJpaTest;
import com.julio.lifeorganizer.auth.domain.Role;
import com.julio.lifeorganizer.auth.persistence.UserEntity;
import com.julio.lifeorganizer.auth.persistence.UserRepository;
import com.julio.lifeorganizer.transactions.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class TransactionPersistenceTest extends AbstractJpaTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void save_whenCalled_assignsIdAndTimestampsAndPreservesScale() {
        UserEntity owner = userRepository.save(UserEntity.createNew(
                "owner@example.com", "$2a$12$abc", "Owner", Role.ROLE_USER));
        entityManager.flush();

        TransactionEntity tx = TransactionEntity.createNew(
                owner.getId(),
                new BigDecimal("1234.56"),
                TransactionType.EXPENSE,
                "Groceries",
                "Weekly supermarket",
                LocalDate.of(2026, 5, 12));

        TransactionEntity saved = transactionRepository.save(tx);
        entityManager.flush();
        entityManager.clear();

        TransactionEntity reloaded = transactionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getAmount()).isEqualByComparingTo("1234.56");
        assertThat(reloaded.getAmount().scale()).isEqualTo(2);
        assertThat(reloaded.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(reloaded.getDeletedAt()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void markDeleted_whenCalled_setsDeletedAtAndFilterByOwnerExcludesIt() {
        UserEntity owner = userRepository.save(UserEntity.createNew(
                "owner2@example.com", "$2a$12$abc", "Owner 2", Role.ROLE_USER));
        TransactionEntity tx = transactionRepository.save(TransactionEntity.createNew(
                owner.getId(), new BigDecimal("10.00"),
                TransactionType.INCOME, "Bonus", "extra", LocalDate.now()));

        tx.markDeleted(java.time.Instant.now());
        entityManager.flush();
        entityManager.clear();

        assertThat(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(tx.getId(), owner.getId()))
                .isEmpty();
    }

    @Test
    void findPage_appliesUserScopingAndOrderByDateDescIdDesc() {
        UserEntity owner = userRepository.save(UserEntity.createNew(
                "list@example.com", "$2a$12$abc", "Lister", Role.ROLE_USER));
        UserEntity other = userRepository.save(UserEntity.createNew(
                "other@example.com", "$2a$12$abc", "Other", Role.ROLE_USER));
        entityManager.flush();

        TransactionEntity recent = transactionRepository.save(TransactionEntity.createNew(
                owner.getId(), new BigDecimal("1.00"),
                TransactionType.EXPENSE, "a", "a", LocalDate.of(2026, 5, 20)));
        TransactionEntity older = transactionRepository.save(TransactionEntity.createNew(
                owner.getId(), new BigDecimal("2.00"),
                TransactionType.EXPENSE, "b", "b", LocalDate.of(2026, 5, 10)));
        TransactionEntity foreign = transactionRepository.save(TransactionEntity.createNew(
                other.getId(), new BigDecimal("3.00"),
                TransactionType.INCOME, "c", "c", LocalDate.of(2026, 5, 20)));

        var page = transactionRepository.findFirstPage(
                owner.getId(), null, null,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page).extracting(TransactionEntity::getId)
                .containsExactly(recent.getId(), older.getId())
                .doesNotContain(foreign.getId());
    }
}
