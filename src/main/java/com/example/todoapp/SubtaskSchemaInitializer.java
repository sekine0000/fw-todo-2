package com.example.todoapp;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

/** Adds the subtask schema to existing databases without removing task data. */
@Component
@DependsOnDatabaseInitialization
public class SubtaskSchemaInitializer implements InitializingBean {
    private final DataSource dataSource;

    public SubtaskSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            boolean hasParentId = false;
            boolean hasDisplayOrder = false;
            String table;
            try (ResultSet rows = statement.executeQuery("SELECT * FROM todos WHERE 1 = 0")) {
                var metadata = rows.getMetaData();
                table = metadata.getTableName(1);
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    if ("parent_id".equalsIgnoreCase(metadata.getColumnName(i))) hasParentId = true;
                    if ("display_order".equalsIgnoreCase(metadata.getColumnName(i))) hasDisplayOrder = true;
                }
            }
            if (!hasParentId) {
                boolean mysql = "MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
                statement.execute("ALTER TABLE todos ADD COLUMN parent_id BIGINT" + (mysql ? " UNSIGNED" : "") + " NULL");
            }
            if (!hasDisplayOrder) {
                statement.execute("ALTER TABLE todos ADD COLUMN display_order INT NOT NULL DEFAULT 2147483647");
            }
            boolean hasCascade = false;
            try (ResultSet keys = connection.getMetaData().getImportedKeys(
                    connection.getCatalog(), connection.getSchema(), table)) {
                while (keys.next()) {
                    if ("parent_id".equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                            && "todos".equalsIgnoreCase(keys.getString("PKTABLE_NAME"))
                            && keys.getShort("DELETE_RULE") == java.sql.DatabaseMetaData.importedKeyCascade) {
                        hasCascade = true;
                    }
                }
            }
            if (!hasCascade) {
                statement.execute("ALTER TABLE todos ADD CONSTRAINT fk_todos_parent "
                        + "FOREIGN KEY (parent_id) REFERENCES todos(id) ON DELETE CASCADE");
            }
        }
    }
}
