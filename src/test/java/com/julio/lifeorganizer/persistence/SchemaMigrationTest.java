package com.julio.lifeorganizer.persistence;

import com.julio.lifeorganizer.AbstractJpaTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class SchemaMigrationTest extends AbstractJpaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void usersTable_afterFlywayMigrate_hasExpectedColumnsAndConstraints() {
        // Sanity check: every column from spec section 4.1 is present with expected nullability.
        List<Map<String, Object>> cols = jdbc.queryForList("""
                SELECT column_name, data_type, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_name = 'users'
                ORDER BY ordinal_position
                """);

        List<String> names = cols.stream().map(c -> (String) c.get("column_name")).toList();
        assertThat(names).containsExactly(
                "id", "email", "password_hash", "display_name", "role", "created_at", "updated_at"
        );

        Integer emailLen = (Integer) cols.get(1).get("character_maximum_length");
        assertThat(emailLen).isEqualTo(255);
    }

    @Test
    void usersTable_uniqueIndexOnEmail_isPresent() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)::int FROM pg_indexes
                WHERE tablename = 'users' AND indexdef LIKE '%UNIQUE%(email)%'
                """, Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void transactionsTable_partialIndex_exists_withDeletedAtIsNullPredicate() {
        String indexDef = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE indexname = 'idx_transactions_user_active'
                """, String.class);

        assertThat(indexDef)
                .as("partial index must filter on deleted_at IS NULL and order by user_id, transaction_date DESC, id DESC")
                .contains("WHERE")
                .contains("deleted_at IS NULL")
                .contains("user_id")
                .contains("transaction_date DESC")
                .contains("id DESC");
    }

    @Test
    void flywaySchemaHistory_recordsBothMigrations_asSuccess() {
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(history)
                .extracting(row -> row.get("version"))
                .containsExactly("1", "2");
        assertThat(history)
                .extracting(row -> row.get("success"))
                .containsOnly(true);
    }
}
