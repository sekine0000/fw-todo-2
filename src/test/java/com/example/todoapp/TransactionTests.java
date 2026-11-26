package com.example.todoapp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionTests {
    @Configuration
    @EnableTransactionManagement
    static class Config {}

    static class CheckedFailureService extends TodoService {
        CheckedFailureService(TodoMapper todos, TodoHistoryMapper history, AuditLogService audit) {
            super(todos, history, audit);
        }

        public void createThenFail(Todo todo) throws IOException {
            super.create(todo);
            throw new IOException("checked failure after both inserts");
        }
    }

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private TodoService service;
    private TodoHistoryMapper history;
    private TodoMapper todos;

    @BeforeEach
    void setUp() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE todos (id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255) NOT NULL, detail VARCHAR(255), category VARCHAR(255), priority INT, due_date DATE, completed BOOLEAN)");
        jdbc.execute("CREATE TABLE todo_history (id BIGINT AUTO_INCREMENT PRIMARY KEY, todo_id BIGINT, operation VARCHAR(32))");
        jdbc.execute("CREATE TABLE audit_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, todo_id BIGINT, operation VARCHAR(32), event_type VARCHAR(32))");
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(new ClassPathResource("mapper/TodoMapper.xml"));
        var sf = factory.getObject();
        sf.getConfiguration().addMapper(TodoHistoryMapper.class);
        sf.getConfiguration().addMapper(AuditLogMapper.class);
        var template = new SqlSessionTemplate(sf);
        todos = spy(template.getMapper(TodoMapper.class));
        history = spy(template.getMapper(TodoHistoryMapper.class));
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(ds));
        context.registerBean(TodoMapper.class, () -> todos);
        context.registerBean(TodoHistoryMapper.class, () -> history);
        context.registerBean(AuditLogMapper.class, () -> template.getMapper(AuditLogMapper.class));
        context.register(AuditLogService.class, TodoService.class);
        context.registerBean(CheckedFailureService.class, () -> new CheckedFailureService(
                todos, history, context.getBean(AuditLogService.class)));
        context.refresh();
        service = context.getBean("todoService", TodoService.class);
    }

    @AfterEach
    void tearDown() { if (context != null) context.close(); }

    private Todo todo() {
        var todo = new Todo();
        todo.setTitle("transaction test");
        todo.setCategory("test");
        todo.setPriority(2);
        return todo;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Test
    void commitsTodoAndHistory() {
        service.create(todo());
        assertThat(count("todos")).isEqualTo(1);
        assertThat(count("todo_history")).isEqualTo(1);
        assertThat(count("audit_log")).isEqualTo(1);
    }

    @Test
    void historyFailureRollsBackBothInsertsButPreservesAudit() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertThat(count("todo_history")).isEqualTo(1);
            throw new IllegalStateException("history failure");
        }).when(history).insert(anyLong(), anyString());
        assertThatThrownBy(() -> service.create(todo())).isInstanceOf(Exception.class);
        assertThat(count("todos")).isZero();
        assertThat(count("todo_history")).isZero();
        assertThat(count("audit_log")).isEqualTo(1);
    }

    @Test
    void checkedExceptionRollsBackBothInsertsButPreservesAudit() {
        assertThatThrownBy(() -> context.getBean(CheckedFailureService.class).createThenFail(todo()))
                .isInstanceOf(IOException.class);
        assertThat(count("todos")).isZero();
        assertThat(count("todo_history")).isZero();
        assertThat(count("audit_log")).isEqualTo(1);
    }

    @Test
    void todoFailurePreservesAuditWithoutHistory() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("todo insert failed");
        }).when(todos).insert(any(Todo.class));
        assertThatThrownBy(() -> service.create(todo())).isInstanceOf(RuntimeException.class);
        assertThat(count("todos")).isZero();
        assertThat(count("todo_history")).isZero();
        assertThat(count("audit_log")).isEqualTo(1);
    }

    @Test
    void readMethodsStartReadOnlyTransactions() {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            return 0L;
        }).when(todos).count(any(), any(), anyBoolean());
        assertThat(service.count("", "", true)).isZero();
    }
}
