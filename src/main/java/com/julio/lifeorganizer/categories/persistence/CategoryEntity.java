package com.julio.lifeorganizer.categories.persistence;

import com.julio.lifeorganizer.categories.domain.CategoryKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "categories")
public class CategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private CategoryKind kind;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CategoryEntity() {
    }

    private CategoryEntity(Long userId, String name, CategoryKind kind) {
        this.userId = userId;
        this.name = name;
        this.kind = kind;
        this.archived = false;
    }

    public static CategoryEntity createNew(Long userId, String name, CategoryKind kind) {
        return new CategoryEntity(userId, name, kind);
    }

    public void rename(String newName) { this.name = newName; }
    public void changeKind(CategoryKind newKind) { this.kind = newKind; }
    public void archive() { this.archived = true; }
    public void unarchive() { this.archived = false; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public CategoryKind getKind() { return kind; }
    public boolean isArchived() { return archived; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
