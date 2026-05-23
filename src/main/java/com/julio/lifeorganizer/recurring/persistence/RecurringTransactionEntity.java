package com.julio.lifeorganizer.recurring.persistence;

import com.julio.lifeorganizer.recurring.domain.Frequency;
import com.julio.lifeorganizer.transactions.domain.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "recurring_transactions")
public class RecurringTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private TransactionType type;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 10)
    private Frequency frequency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(name = "paused", nullable = false)
    private boolean paused;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecurringTransactionEntity() {
    }

    private RecurringTransactionEntity(
            Long userId, Long categoryId, BigDecimal amount, TransactionType type,
            String description, Frequency frequency,
            LocalDate startDate, LocalDate endDate, LocalDate nextDueDate) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.type = type;
        this.description = description == null ? "" : description;
        this.frequency = frequency;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextDueDate = nextDueDate;
        this.paused = false;
    }

    public static RecurringTransactionEntity createNew(
            Long userId, Long categoryId, BigDecimal amount, TransactionType type,
            String description, Frequency frequency,
            LocalDate startDate, LocalDate endDate) {
        return new RecurringTransactionEntity(userId, categoryId, amount, type, description,
                frequency, startDate, endDate, startDate);
    }

    public void replaceWith(BigDecimal amount, TransactionType type, String description,
                            Frequency frequency, LocalDate endDate, Long categoryId) {
        this.amount = amount;
        this.type = type;
        this.description = description == null ? "" : description;
        this.frequency = frequency;
        this.endDate = endDate;
        this.categoryId = categoryId;
    }

    public void advanceAfterMaterialise() {
        this.nextDueDate = this.frequency.advance(this.nextDueDate);
    }

    public void pause() { this.paused = true; }
    public void resume() { this.paused = false; }

    public boolean isDue(LocalDate today) {
        if (paused) return false;
        if (nextDueDate.isAfter(today)) return false;
        return endDate == null || !nextDueDate.isAfter(endDate);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getCategoryId() { return categoryId; }
    public BigDecimal getAmount() { return amount; }
    public TransactionType getType() { return type; }
    public String getDescription() { return description; }
    public Frequency getFrequency() { return frequency; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public boolean isPaused() { return paused; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
