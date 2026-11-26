package com.example.todoapp;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class AuditSchemaTests {
    @Test
    void initializesExistingDatabaseAndCanReadLogsAfterRepeatedStartup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:audit-schema;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE todos (id BIGINT PRIMARY KEY)");
        var init = new ResourceDatabasePopulator(new ClassPathResource("db/02_create_transaction_tables.sql"),
                new ClassPathResource("db/03_create_audit_logs.sql"));
        init.execute(ds);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        var sf = factory.getObject();
        sf.getConfiguration().setMapUnderscoreToCamelCase(true);
        sf.getConfiguration().addMapper(AuditLogMapper.class);
        var mapper = new SqlSessionTemplate(sf).getMapper(AuditLogMapper.class);
        assertThat(mapper.search("", null, "", 0)).isEmpty();
        var log = new AuditLog();
        log.setAction("CREATE");
        log.setEntityType("TODO");
        log.setStatus("ATTEMPT");
        mapper.insertLog(log);
        init.execute(ds);
        var saved = mapper.search("", null, "", 0);
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getEntityType()).isEqualTo("TODO");
        assertThat(saved.getFirst().getCreatedAt()).isNotNull();
        sf.getConfiguration().addMapper(TodoHistoryMapper.class);
        new SqlSessionTemplate(sf).getMapper(TodoHistoryMapper.class).insert(1L, "DELETE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todo_history", Integer.class)).isEqualTo(1);
    }
}
