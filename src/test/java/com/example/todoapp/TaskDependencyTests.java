package com.example.todoapp;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.todoapp.api.TaskDependencyController;
import com.example.todoapp.TaskDependencyMapper.Edge;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskDependencyTests {
    private Todo task(long id, boolean completed) {
        var task = new Todo();
        task.setId(id);
        task.setTitle("Task " + id);
        task.setCompleted(completed);
        return task;
    }

    @Test
    void bfsRejectsSelfAndIndirectCyclesButAcceptsDiamondAndDuplicate() {
        var mapper = mock(TaskDependencyMapper.class);
        var service = new TaskDependencyService(mapper);
        when(mapper.tasks()).thenReturn(List.of(task(1, false), task(2, false), task(3, false), task(4, false)));
        when(mapper.edges()).thenReturn(List.of(new Edge(1L, 2L), new Edge(1L, 3L), new Edge(2L, 4L)));
        service.add(4L, 3L);
        verify(mapper).insert(3L, 4L);
        service.add(2L, 1L);
        verify(mapper, never()).insert(1L, 2L);
        assertThatThrownBy(() -> service.add(1L, 4L)).hasMessageContaining("409");
        assertThatThrownBy(() -> service.add(1L, 1L)).hasMessageContaining("409");
        verify(mapper, never()).insert(4L, 1L);
        verify(mapper, never()).insert(1L, 1L);
    }

    @Test
    void apiValidatesInputAndReportsBlockedTasks() throws Exception {
        var mapper = mock(TaskDependencyMapper.class);
        when(mapper.tasks()).thenReturn(List.of(task(1, false), task(2, false), task(3, true)));
        when(mapper.edges()).thenReturn(List.of(new Edge(1L, 2L), new Edge(3L, 2L), new Edge(99L, 2L)));
        var service = new TaskDependencyService(mapper);
        var mvc = MockMvcBuilders.standaloneSetup(new TaskDependencyController(service)).build();
        mvc.perform(get("/api/todos/2/dependencies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedIds[0]").value(2))
                .andExpect(jsonPath("$.edges.length()").value(2));
        for (String body : List.of("{}", "{\"prerequisiteId\":0}", "{\"prerequisiteId\":-1}")) {
            mvc.perform(post("/api/todos/2/dependencies").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/todos/1/dependencies").contentType("application/json")
                .content("{\"prerequisiteId\":2}")).andExpect(status().isConflict());
        mvc.perform(post("/api/todos/2/dependencies").contentType("application/json")
                .content("{\"prerequisiteId\":99}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/todos/3/dependencies").contentType("application/json")
                .content("{\"prerequisiteId\":1}")).andExpect(status().isNoContent());
        verify(mapper).insert(1L, 3L);
        mvc.perform(delete("/api/todos/2/dependencies/1")).andExpect(status().isNoContent());
        verify(mapper).delete(1L, 2L);
        when(mapper.tasks()).thenReturn(List.of(task(1, true), task(2, false), task(3, true)));
        assertThat(service.get(2L).blockedIds()).isEmpty();
        mvc.perform(get("/api/todos/99/dependencies")).andExpect(status().isNotFound());
    }

    @Test
    void detailPageRendersDependencyControlsAndContextAwareApiUrl() throws Exception {
        var todos = mock(TodoService.class);
        when(todos.findById(2L)).thenReturn(task(2, false));
        when(todos.subtasks(2L)).thenReturn(List.of());
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var views = new org.thymeleaf.spring6.view.ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        var mvc = MockMvcBuilders.standaloneSetup(new SubtaskController(todos)).setViewResolvers(views).build();
        mvc.perform(get("/app/todos/2").contextPath("/app")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-api-url=\"/app/api/todos/2/dependencies\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"prerequisite-select\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/app/js/task-dependencies.js")));
    }

    @Test
    void schemaAndMybatisPersistManyToManyAndCascadePhysicalDeletes() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:dependencies;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE todos (id BIGINT PRIMARY KEY, title VARCHAR(255), completed BOOLEAN, deleted_at TIMESTAMP)");
        jdbc.update("INSERT INTO todos (id, title, completed) VALUES (1, 'first', FALSE), (2, 'second', FALSE), (3, 'third', TRUE)");
        var initializer = new TaskDependencySchemaInitializer(ds);
        initializer.afterPropertiesSet();
        initializer.afterPropertiesSet();
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        var config = new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(TaskDependencyMapper.class);
        factory.setConfiguration(config);
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(TaskDependencyMapper.class);
        var service = new TaskDependencyService(mapper);
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        tx.executeWithoutResult(s -> service.add(2L, 1L));
        tx.executeWithoutResult(s -> service.add(2L, 3L));
        tx.executeWithoutResult(s -> service.add(3L, 1L));
        assertThat(mapper.edges()).containsExactly(new Edge(1L, 2L), new Edge(1L, 3L), new Edge(3L, 2L));
        assertThat(service.get(2L).blockedIds()).containsExactly(2L, 3L);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> service.add(1L, 2L))).hasMessageContaining("409");
        jdbc.update("UPDATE todos SET deleted_at = CURRENT_TIMESTAMP WHERE id = 3");
        assertThat(service.get(2L).edges()).containsExactly(new Edge(1L, 2L));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> service.add(1L, 2L))).hasMessageContaining("409");
        tx.executeWithoutResult(s -> service.remove(2L, 1L));
        assertThat(service.get(2L).blockedIds()).isEmpty();
        jdbc.update("DELETE FROM todos WHERE id = 1");
        assertThat(mapper.edges()).containsExactly(new Edge(3L, 2L));
        assertThatThrownBy(() -> mapper.insert(2L, 2L)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> mapper.insert(99L, 2L)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        // Opposite edges racing in separate transactions must never both succeed.
        jdbc.update("INSERT INTO todos (id, title, completed) VALUES (4, 'fourth', FALSE), (5, 'fifth', FALSE)");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentAdd(tx, service, start, 4L, 5L));
            var second = executor.submit(() -> concurrentAdd(tx, service, start, 5L, 4L));
            start.countDown();
            assertThat(List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(mapper.edges().stream().filter(e -> e.prerequisiteId() >= 4).count()).isEqualTo(1);
    }

    private boolean concurrentAdd(org.springframework.transaction.support.TransactionTemplate tx,
            TaskDependencyService service, java.util.concurrent.CountDownLatch start,
            Long id, Long prerequisiteId) throws InterruptedException {
        start.await();
        try {
            tx.executeWithoutResult(s -> service.add(id, prerequisiteId));
            return true;
        } catch (org.springframework.web.server.ResponseStatusException e) {
            if (e.getStatusCode().value() != 409) throw e;
            return false;
        }
    }
}
