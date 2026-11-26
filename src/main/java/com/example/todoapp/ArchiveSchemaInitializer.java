package com.example.todoapp;

import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/** Adds archive columns to existing databases without removing task data. */
@Component
@DependsOnDatabaseInitialization
public class ArchiveSchemaInitializer implements InitializingBean {
    private final DataSource dataSource;
    public ArchiveSchemaInitializer(DataSource dataSource) { this.dataSource = dataSource; }
    @Override
    public void afterPropertiesSet() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            boolean archived = false;
            boolean archivedAt = false;
            try (var rows = statement.executeQuery("SELECT * FROM todos WHERE 1 = 0")) {
                var metadata = rows.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    archived |= "archived".equalsIgnoreCase(metadata.getColumnName(i));
                    archivedAt |= "archived_at".equalsIgnoreCase(metadata.getColumnName(i));
                }
            }
            if (!archived) statement.execute("ALTER TABLE todos ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE");
            if (!archivedAt) statement.execute("ALTER TABLE todos ADD COLUMN archived_at TIMESTAMP NULL");
        }
    }
}
