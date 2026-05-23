package com.julio.lifeorganizer.categories.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    List<CategoryEntity> findByUserIdAndArchivedFalseOrderByNameAsc(Long userId);

    Optional<CategoryEntity> findByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT c FROM CategoryEntity c
            WHERE c.userId = :userId AND LOWER(c.name) = LOWER(:name)
            """)
    Optional<CategoryEntity> findByUserAndNameIgnoreCase(
            @Param("userId") Long userId, @Param("name") String name);
}
