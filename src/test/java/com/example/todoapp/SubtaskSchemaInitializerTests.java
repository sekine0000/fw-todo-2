package com.example.todoapp;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class SubtaskSchemaInitializerTests {
    @Test
    void upgradesExistingDatabasePreservesDataAndCanRunRepeatedly() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:subtask-schema;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE todos (id BIGINT PRIMARY KEY, title VARCHAR(255))");
        jdbc.update("INSERT INTO todos (id, title) VALUES (1, 'existing')");
        var initializer = new SubtaskSchemaInitializer(ds);
        initializer.afterPropertiesSet();
        initializer.afterPropertiesSet();
        assertThat(jdbc.queryForObject("SELECT display_order FROM todos WHERE id = 1", Integer.class))
                .isEqualTo(Integer.MAX_VALUE);
        assertThat(jdbc.queryForObject("SELECT title FROM todos WHERE id = 1", String.class)).isEqualTo("existing");
        jdbc.update("INSERT INTO todos (id, parent_id, title) VALUES (2, 1, 'child')");
        jdbc.update("DELETE FROM todos WHERE id = 1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todos", Integer.class)).isZero();
    }
}
