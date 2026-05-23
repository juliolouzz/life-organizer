package com.julio.lifeorganizer.auth.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Users whose grace period has elapsed and should now be hard-deleted.
     * Backed by the partial index `idx_users_deletion_scheduled`. Written
     * as an explicit JPQL query (rather than a derived method) so the SQL
     * is obvious in test logs and the predicate cannot be misinterpreted
     * by the derived-name parser.
     */
    @Query("""
            SELECT u FROM UserEntity u
            WHERE u.deletionScheduledAt IS NOT NULL
              AND u.deletionScheduledAt <= :cutoff
            """)
    List<UserEntity> findUsersDueForHardDelete(@Param("cutoff") Instant cutoff);
}
