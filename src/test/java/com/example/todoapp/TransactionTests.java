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
        jdbc.execute("CREATE TABLE todos (archived BOOLEAN NOT NULL DEFAULT FALSE, id BIGINT AUTO_INCREMENT PRIMARY KEY, parent_id BIGINT REFERENCES todos(id) ON DELETE CASCADE, display_order INT NOT NULL DEFAULT 2147483647, title VARCHAR(255) NOT NULL, detail VARCHAR(255), category VARCHAR(255), priority INT, due_date DATE, completed BOOLEAN)");
        for (String column : java.util.List.of("pinned BOOLEAN DEFAULT FALSE", "deleted_at TIMESTAMP",
                "completed_at TIMESTAMP", "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP")) {
            jdbc.execute("ALTER TABLE todos ADD " + column);
        }
        jdbc.execute("CREATE TABLE todo_history (id BIGINT AUTO_INCREMENT PRIMARY KEY, todo_id BIGINT, operation VARCHAR(32))");
        jdbc.execute("CREATE TABLE audit_logs (id BIGINT AUTO_INCREMENT PRIMARY KEY, action VARCHAR(32), entity_type VARCHAR(64), entity_id BIGINT, user_id VARCHAR(255), old_value TEXT, new_value TEXT, ip_address VARCHAR(45), status VARCHAR(32), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new ClassPathResource("db/04_create_todo_templates.sql")).execute(ds);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(new ClassPathResource("mapper/TodoMapper.xml"));
        var sf = factory.getObject();
        sf.getConfiguration().addMapper(TodoHistoryMapper.class);
        sf.getConfiguration().addMapper(AuditLogMapper.class);
        sf.getConfiguration().addMapper(TodoTemplateMapper.class);
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
        context.registerBean(TodoTemplateMapper.class, () -> template.getMapper(TodoTemplateMapper.class));
        context.registerBean(org.springframework.validation.beanvalidation.LocalValidatorFactoryBean.class);
        context.registerBean(TodoTemplateService.class, () -> new TodoTemplateService(
                context.getBean(TodoTemplateMapper.class), context.getBean("todoService", TodoService.class),
                context.getBean(jakarta.validation.Validator.class)));
        context.register(AuditLogService.class, TodoService.class, com.example.todoapp.aspect.AuditAspect.class);
        context.registerBean(CheckedFailureService.class, () -> new CheckedFailureService(
                todos, history));
        context.refresh();
        service = context.getBean("todoService", TodoService.class);
    }

    @AfterEach
    void tearDown() { if (context != null) context.close(); }

    @Test
    void quickCreatePersistsWithProductionCategoryConstraint() throws Exception {
        String schema = java.nio.file.Files.readString(java.nio.file.Path.of("initdb/01_create_table.sql"));
        var constraint = java.util.regex.Pattern.compile(
                "(?s)CONSTRAINT chk_todos_category CHECK \\(.*?\\n    \\)").matcher(schema);
        assertThat(constraint.find()).isTrue();
        // H2's IN constraint retains its creating session; keep it open for this check.
        try (var connection = jdbc.getDataSource().getConnection(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE todos ADD " + constraint.group());
            var controller = new com.example.todoapp.api.TodoApiController(service);
            var response = controller.quickCreate(new com.example.todoapp.api.TodoApiController.QuickCreateRequest(" quick task "));
            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody().getTitle()).isEqualTo("quick task");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todos WHERE id = ?", Integer.class,
                    response.getBody().getId())).isEqualTo(1);
        }
    }

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
    }

    @Test
    void duplicateCopiesFieldsAndOptionalChildrenWithoutChangingOriginals() {
        Todo source = todo();
        source.setTitle("a".repeat(255));
        source.setDetail("memo");
        source.setDueDate(java.time.LocalDate.of(2026, 12, 1));
        service.create(source);
        service.addSubtask(source.getId(), "child");
        Long childId = service.subtasks(source.getId()).getFirst().getId();
        service.toggleSubtask(source.getId(), childId);
        Todo copy = service.duplicate(source.getId(), true);
        Todo stored = service.findById(copy.getId());
        assertThat(stored.getTitle()).isEqualTo("a".repeat(250) + "（コピー）");
        assertThat(stored.getDetail()).isEqualTo(source.getDetail());
        assertThat(stored.getCategory()).isEqualTo(source.getCategory());
        assertThat(stored.getPriority()).isEqualTo(source.getPriority());
        assertThat(stored.getDueDate()).isEqualTo(source.getDueDate());
        assertThat(stored.getCompleted()).isFalse();
        assertThat(stored.getCompletedAt()).isNull();
        var children = service.subtasks(copy.getId());
        assertThat(children).hasSize(1);
        assertThat(children.getFirst().getId()).isNotEqualTo(childId);
        assertThat(children.getFirst().getTitle()).isEqualTo("child（コピー）");
        assertThat(children.getFirst().getCompleted()).isFalse();
        assertThat(children.getFirst().getCompletedAt()).isNull();
        assertThat(service.findById(source.getId()).getCompleted()).isTrue();
        assertThat(service.findById(childId).getCompleted()).isTrue();
        assertThat(service.subtasks(service.duplicate(source.getId(), false).getId())).isEmpty();
    }

    @Test
    void duplicateChildFailureRollsBackParentChildrenAndHistory() {
        Todo source = todo();
        service.create(source);
        service.addSubtask(source.getId(), "child");
        int originalHistory = count("todo_history");
        doAnswer(invocation -> {
            Todo inserted = invocation.getArgument(0);
            invocation.callRealMethod();
            if (inserted.getParentId() != null) throw new IllegalStateException("child failure");
            return null;
        }).when(todos).insert(any(Todo.class));
        assertThatThrownBy(() -> service.duplicate(source.getId(), true)).isInstanceOf(IllegalStateException.class);
        assertThat(count("todos")).isEqualTo(2);
        assertThat(count("todo_history")).isEqualTo(originalHistory);
    }

    @Test
    void duplicateEndpointAndFormsWorkAndMissingOrDeletedTasksReturn404() throws Exception {
        Todo source = todo();
        service.create(source);
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var views = new org.thymeleaf.spring6.view.ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new HomeController(service), new SubtaskController(service)).setViewResolvers(views).build();
        for (String path : java.util.List.of("/todos", "/todos/" + source.getId())) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                    .requestAttr("_csrf", new org.springframework.security.web.csrf.DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                            org.hamcrest.Matchers.containsString("/todo/" + source.getId() + "/duplicate")));
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/todo/" + source.getId() + "/duplicate").param("includeSubtasks", "true"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/todos"));
        service.delete(source.getId());
        for (Long id : java.util.List.of(source.getId(), 999L)) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/todo/" + id + "/duplicate"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        }
    }

    private TodoTemplate savedTemplate() {
        var template = new TodoTemplate();
        template.setName("週の準備");
        template.setDescription("毎週使うタスク");
        template.setItems(java.util.List.of("計画", "振り返り"));
        context.getBean(TodoTemplateService.class).create(template);
        return template;
    }

    private TemplateTaskForm templateTaskForm(Long id) {
        var form = new TemplateTaskForm();
        form.setTemplateId(id);
        form.setCategory("資格");
        form.setDueDate(java.time.LocalDate.of(2026, 12, 1));
        return form;
    }

    @Test
    void templateLifecycleCreatesIndependentTasksAndCascadesItems() {
        var templates = context.getBean(TodoTemplateService.class);
        var template = savedTemplate();
        assertThat(templates.findAll().getFirst().getItems()).containsExactly("計画", "振り返り");
        assertThat(templates.createTasks(templateTaskForm(template.getId()))).isEqualTo(2);
        assertThat(service.search(null, null, "asc")).extracting(Todo::getTitle)
                .containsExactlyInAnyOrder("計画", "振り返り");
        assertThat(count("todo_history")).isEqualTo(2);
        templates.delete(template.getId());
        assertThat(count("todo_template_items")).isZero();
        assertThat(count("todo_templates")).isZero();
        assertThat(count("todos")).isEqualTo(2);
    }

    @Test
    void templateBulkCreationRollsBackEarlierTasksAndHistory() {
        var template = savedTemplate();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (count("todo_history") == 2) throw new IllegalStateException("second task failed");
            return null;
        }).when(history).insert(anyLong(), eq("CREATE"));
        assertThatThrownBy(() -> context.getBean(TodoTemplateService.class)
                .createTasks(templateTaskForm(template.getId()))).isInstanceOf(IllegalStateException.class);
        assertThat(count("todos")).isZero();
        assertThat(count("todo_history")).isZero();
        assertThat(count("todo_template_items")).isEqualTo(2);
    }

    @Test
    void templateValidationAndMissingIdDoNotCreateTasks() {
        var templates = context.getBean(TodoTemplateService.class);
        var invalid = new TodoTemplate();
        invalid.setName("invalid");
        invalid.setItems(java.util.List.of(" "));
        assertThatThrownBy(() -> templates.create(invalid))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(count("todo_templates")).isZero();
        assertThatThrownBy(() -> templates.createTasks(templateTaskForm(999L)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(count("todos")).isZero();
    }

    @Test
    void templatePagesRenderAndPreserveValidationErrors() throws Exception {
        savedTemplate();
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var views = new org.thymeleaf.spring6.view.ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new TodoTemplateController(context.getBean(TodoTemplateService.class)))
                .setViewResolvers(views)
                .addFilters(new org.springframework.web.filter.CharacterEncodingFilter("UTF-8", true)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/templates"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.containsString("週の準備")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/templates/new"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/templates")
                        .param("name", "入力保持").param("items", ""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().hasErrors())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("template-create"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/templates/apply")
                        .param("templateId", "1").param("category", ""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().hasErrors())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("todo-templates"));
    }
}
