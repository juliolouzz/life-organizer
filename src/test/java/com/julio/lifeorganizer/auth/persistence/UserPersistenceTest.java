package com.julio.lifeorganizer.auth.persistence;

import com.julio.lifeorganizer.AbstractJpaTest;
import com.julio.lifeorganizer.auth.domain.Role;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class UserPersistenceTest extends AbstractJpaTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void save_whenCalled_assignsIdAndTimestamps() {
        UserEntity user = UserEntity.createNew(
                "alice@example.com", "$2a$12$abc", "Alice", Role.ROLE_USER);

        UserEntity saved = userRepository.save(user);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    void findByEmail_whenUserExists_returnsUser() {
        userRepository.save(UserEntity.createNew(
                "bob@example.com", "$2a$12$xyz", "Bob", Role.ROLE_USER));
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findByEmail("bob@example.com"))
                .isPresent()
                .hasValueSatisfying(u -> assertThat(u.getDisplayName()).isEqualTo("Bob"));
    }

    @Test
    void existsByEmail_whenUserMissing_returnsFalse() {
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }
}
