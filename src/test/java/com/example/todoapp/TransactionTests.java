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
    @org.springframework.context.annotation.EnableAspectJAutoProxy
    @EnableTransactionManagement(order = Integer.MAX_VALUE - 1)
    static class Config {}

    static class CheckedFailureService extends TodoService {
        CheckedFailureService(TodoMapper todos, TodoHistoryMapper history) {
            super(todos, history);
        }

        @Auditable(action = "CREATE")
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
        jdbc.execute("CREATE TABLE todos (id BIGINT AUTO_INCREMENT PRIMARY KEY, parent_id BIGINT REFERENCES todos(id) ON DELETE CASCADE, title VARCHAR(255) NOT NULL, detail VARCHAR(255), category VARCHAR(255), priority INT, due_date DATE, completed BOOLEAN)");
        for (String column : java.util.List.of("pinned BOOLEAN DEFAULT FALSE", "deleted_at TIMESTAMP",
                "completed_at TIMESTAMP", "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP")) {
            jdbc.execute("ALTER TABLE todos ADD " + column);
        }
        jdbc.execute("CREATE TABLE todo_history (id BIGINT AUTO_INCREMENT PRIMARY KEY, todo_id BIGINT, operation VARCHAR(32))");
        jdbc.execute("CREATE TABLE audit_logs (id BIGINT AUTO_INCREMENT PRIMARY KEY, action VARCHAR(32), entity_type VARCHAR(64), entity_id BIGINT, user_id VARCHAR(255), old_value TEXT, new_value TEXT, ip_address VARCHAR(45), status VARCHAR(32), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(new ClassPathResource("mapper/TodoMapper.xml"));
        var sf = factory.getObject();
        sf.getConfiguration().addMapper(TodoHistoryMapper.class);
        sf.getConfiguration().addMapper(AuditLogMapper.class);
        sf.getConfiguration().setMapUnderscoreToCamelCase(true);
        var template = new SqlSessionTemplate(sf);
        todos = spy(template.getMapper(TodoMapper.class));
        history = spy(template.getMapper(TodoHistoryMapper.class));
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(ds));
        context.registerBean(TodoMapper.class, () -> todos);
        context.registerBean(TodoHistoryMapper.class, () -> history);
        context.registerBean(AuditLogMapper.class, () -> template.getMapper(AuditLogMapper.class));
        context.register(AuditLogService.class, TodoService.class, com.example.todoapp.aspect.AuditAspect.class);
        context.registerBean(CheckedFailureService.class, () -> new CheckedFailureService(
                todos, history));
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
        assertThat(count("audit_logs")).isEqualTo(1);
    }

    @Test
    void historyFailureRollsBackBothInsertsButPreservesAudit() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertThat(count("todo_history")).isEqualTo(1);
            throw new IllegalStateException("history failure");
        }).when(history).insert(anyLong(), anyString());
        assertThatThrownBy(() -> service.create(todo())).isInstanceOf(Exception.class);
        assertThat(jdbc.queryForObject("SELECT status FROM audit_logs", String.class)).isEqualTo("ROLLED_BACK");
        assertThat(count("todos")).isZero();
        assertThat(count("todo_history")).isZero();
        assertThat(count("audit_logs")).isEqualTo(1);
    }

    @Test
    void checkedExceptionRollsBackBothInsertsButPreservesAudit() {
        assertThatThrownBy(() -> context.getBean(CheckedFailureService.class).createThenFail(todo()))
                .isInstanceOf(IOException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM audit_logs", String.class)).isEqualTo("ROLLED_BACK");
        assertThat(count("todos")).isZero();
        assertThat(count("todo_history")).isZero();
        assertThat(count("audit_logs")).isEqualTo(1);
    }

    @Test
    void todoFailurePreservesAuditWithoutHistory() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("todo insert failed");
        }).when(todos).insert(any(Todo.class));
        assertThatThrownBy(() -> service.create(todo())).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM audit_logs", String.class)).isEqualTo("ROLLED_BACK");
        assertThat(count("todos")).isZero();
        assertThat(count("todo_history")).isZero();
        assertThat(count("audit_logs")).isEqualTo(1);
    }

    @Test
    void capturesSnapshotsIdentityAndBulkDeleteRestore() {
        var authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated("audit-admin", "unused", java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            Todo todo = todo();
            service.create(todo);
            todo.setTitle("changed title");
            todo.setCompleted(true);
            service.update(todo);
            assertThat(jdbc.queryForObject("SELECT old_value FROM audit_logs WHERE action='UPDATE'", String.class))
                    .contains("transaction test");
            assertThat(jdbc.queryForObject("SELECT new_value FROM audit_logs WHERE action='UPDATE'", String.class))
                    .contains("changed title");
            assertThat(jdbc.queryForObject("SELECT user_id FROM audit_logs WHERE action='UPDATE'", String.class))
                    .isEqualTo("audit-admin");
            assertThat(jdbc.queryForObject("SELECT ip_address FROM audit_logs WHERE action='UPDATE'", String.class))
                    .isEqualTo("192.0.2.10");
            service.togglePinned(todo.getId());
            service.deleteByIds(java.util.List.of(todo.getId(), todo.getId()));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='DELETE'", Integer.class)).isEqualTo(1);
            service.restore(todo.getId());
            assertThat(todos.findById(todo.getId())).isNotNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE status='SUCCESS'", Integer.class)).isEqualTo(5);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
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
    @Test
    void subtaskProgressAndCompletionFollowChanges() throws Exception {
        Todo parent = todo();
        service.create(parent);
        service.addSubtask(parent.getId(), "first");
        service.addSubtask(parent.getId(), "second");
        var children = service.subtasks(parent.getId());
        service.toggleSubtask(parent.getId(), children.get(0).getId());
        assertThat(service.findById(parent.getId()).getCompleted()).isFalse();
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var views = new org.thymeleaf.spring6.view.ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new SubtaskController(service)).setViewResolvers(views).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/todos/" + parent.getId()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute("progress", 50L));
        service.toggleSubtask(parent.getId(), children.get(1).getId());
        assertThat(service.findById(parent.getId()).getCompleted()).isTrue();
        assertThat(service.findById(parent.getId()).getCompletedAt()).isNotNull();
        service.toggleSubtask(parent.getId(), children.get(0).getId());
        assertThat(service.findById(parent.getId()).getCompleted()).isFalse();
        assertThat(service.findById(parent.getId()).getCompletedAt()).isNull();
        service.deleteSubtask(parent.getId(), children.get(0).getId());
        assertThat(service.findById(parent.getId()).getCompleted()).isTrue();
        service.addSubtask(parent.getId(), "new");
        assertThat(service.findById(parent.getId()).getCompleted()).isFalse();
        assertThat(service.search(null, null, "asc")).extracting(Todo::getId).containsExactly(parent.getId());
    }

    @Test
    void subtaskWritesRollBackAndPhysicalParentDeleteCascades() {
        Todo parent = todo();
        service.create(parent);
        service.addSubtask(parent.getId(), "child");
        Long childId = service.subtasks(parent.getId()).getFirst().getId();
        doThrow(new IllegalStateException("history unavailable")).when(history).insert(childId, "UPDATE");
        assertThatThrownBy(() -> service.toggleSubtask(parent.getId(), childId)).isInstanceOf(IllegalStateException.class);
        assertThat(service.findById(childId).getCompleted()).isFalse();
        assertThat(service.findById(parent.getId()).getCompleted()).isFalse();
        jdbc.update("DELETE FROM todos WHERE id = ?", parent.getId());
        assertThat(count("todos")).isZero();
    }

    @Test
    void invalidSubtaskRequestsDoNotChangeData() {
        Todo parent = todo();
        service.create(parent);
        Todo other = todo();
        service.create(other);
        service.addSubtask(parent.getId(), "child");
        Long childId = service.subtasks(parent.getId()).getFirst().getId();
        assertThatThrownBy(() -> service.toggleSubtask(other.getId(), childId))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.addSubtask(parent.getId(), " "))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.addSubtask(childId, "nested"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        service.deleteSubtask(parent.getId(), childId);
        assertThat(service.subtasks(parent.getId())).isEmpty();
        assertThat(service.findById(parent.getId()).getCompleted()).isFalse();
    }}
