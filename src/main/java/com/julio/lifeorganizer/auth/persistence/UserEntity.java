package com.julio.lifeorganizer.auth.persistence;

import com.julio.lifeorganizer.auth.domain.Role;
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
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    // Slice 12: revocation epoch. Every issued JWT carries this value as the
    // "tv" claim; a bump invalidates every prior token for this user.
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    // NULL while the account is active. Set to a future timestamp when the user
    // requests deletion; cleared on restore. The scheduled hard-delete job picks
    // up rows where this is non-null and in the past.
    @Column(name = "deletion_scheduled_at")
    private Instant deletionScheduledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Required by JPA. Use createNew(...) factory for application code.
    protected UserEntity() {
    }

    private UserEntity(String email, String passwordHash, String displayName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
    }

    public static UserEntity createNew(String email, String passwordHash, String displayName, Role role) {
        return new UserEntity(email, passwordHash, displayName, role);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void changeEmail(String newEmail) {
        this.email = newEmail;
    }

    public void changeDisplayName(String newDisplayName) {
        this.displayName = newDisplayName;
    }

    public Instant getDeletionScheduledAt() {
        return deletionScheduledAt;
    }

    public boolean isDeletionPending() {
        return deletionScheduledAt != null;
    }

    public void scheduleDeletion(Instant when) {
        this.deletionScheduledAt = when;
    }

    public void cancelDeletion() {
        this.deletionScheduledAt = null;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    /**
     * Slice 12: invalidate every JWT previously issued for this user. Called
     * by AccountService (logout-all, password change) and AuthService
     * (password reset). The bump is monotonic - we never decrement, so a
     * leaked old token cannot become valid again.
     */
    public void bumpTokenVersion() {
        this.tokenVersion += 1;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
