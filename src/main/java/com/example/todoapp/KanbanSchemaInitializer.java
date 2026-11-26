package com.example.todoapp;

import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
public class KanbanSchemaInitializer implements InitializingBean {
    private final DataSource dataSource;
    public KanbanSchemaInitializer(DataSource dataSource) { this.dataSource = dataSource; }
    @Override
    public void afterPropertiesSet() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            boolean found = false;
            try (var rows = statement.executeQuery("SELECT * FROM todos WHERE 1 = 0")) {
                var metadata = rows.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    found |= "in_progress".equalsIgnoreCase(metadata.getColumnName(i));
                }
            }
            if (!found) statement.execute("ALTER TABLE todos ADD COLUMN in_progress BOOLEAN NOT NULL DEFAULT FALSE");
        }
    }
}
