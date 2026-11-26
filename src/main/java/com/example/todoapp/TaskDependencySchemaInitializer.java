package com.example.todoapp;

import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
public class TaskDependencySchemaInitializer implements InitializingBean {
    private final DataSource dataSource;
    public TaskDependencySchemaInitializer(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public void afterPropertiesSet() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            String type = "MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())
                    ? "BIGINT UNSIGNED" : "BIGINT";
            statement.execute("CREATE TABLE IF NOT EXISTS task_dependencies (prerequisite_id " + type
                    + " NOT NULL, successor_id " + type + " NOT NULL, PRIMARY KEY (prerequisite_id, successor_id),"
                    + " CONSTRAINT fk_dependency_prerequisite FOREIGN KEY (prerequisite_id) REFERENCES todos(id) ON DELETE CASCADE,"
                    + " CONSTRAINT fk_dependency_successor FOREIGN KEY (successor_id) REFERENCES todos(id) ON DELETE CASCADE,"
                    + " CONSTRAINT chk_dependency_self CHECK (prerequisite_id <> successor_id))");
            statement.execute("CREATE TABLE IF NOT EXISTS task_dependency_lock (id INT PRIMARY KEY)");
            statement.execute("INSERT INTO task_dependency_lock (id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM task_dependency_lock WHERE id = 1)");
        }
    }
}
