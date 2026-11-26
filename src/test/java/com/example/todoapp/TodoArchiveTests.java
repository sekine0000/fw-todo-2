package com.example.todoapp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TodoArchiveTests {
    JdbcTemplate jdbc;
    TodoArchiveService service;
    TodoMapper todos;
    final LocalDateTime now = LocalDateTime.of(2026, 11, 26, 2, 0);

    @BeforeEach
    void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + System.nanoTime() + ";MODE=MySQL", "sa", "");
        // Keep this database alive across mapper connections.
        ds.setUrl(ds.getUrl() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE todos (id BIGINT PRIMARY KEY, parent_id BIGINT REFERENCES todos(id) ON DELETE CASCADE, title VARCHAR(255), detail VARCHAR(255), category VARCHAR(255), priority INT, due_date DATE, completed BOOLEAN, completed_at TIMESTAMP, deleted_at TIMESTAMP, display_order INT DEFAULT 0, pinned BOOLEAN DEFAULT FALSE, created_at TIMESTAMP, updated_at TIMESTAMP)");
        var initializer = new ArchiveSchemaInitializer(ds);
        initializer.afterPropertiesSet();
        initializer.afterPropertiesSet();
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(new ClassPathResource("mapper/TodoMapper.xml"));
        var sf = factory.getObject();
        sf.getConfiguration().setMapUnderscoreToCamelCase(true);
        sf.getConfiguration().addMapper(TodoArchiveMapper.class);
        var session = new SqlSessionTemplate(sf);
        service = new TodoArchiveService(session.getMapper(TodoArchiveMapper.class));
        todos = session.getMapper(TodoMapper.class);
    }

    void task(long id, Long parent, boolean completed, LocalDateTime completedAt) {
        jdbc.update("INSERT INTO todos (id, parent_id, title, completed, completed_at) VALUES (?, ?, ?, ?, ?)",
                id, parent, "task " + id, completed, completedAt);
    }

    @Test
    void archiveRestoreAndPhysicalDeleteIncludeChildren() {
        task(1, null, true, now.minusDays(31));
        task(2, 1L, true, now.minusDays(31));
        service.archive(1L);
        assertThat(service.list()).extracting(Todo::getId).containsExactly(1L);
        assertThat(service.list().getFirst().getArchivedAt()).isNotNull();
        assertThat(todos.count(null, null, true)).isZero();
        assertThat(todos.findById(1L)).isNull();
        service.unarchive(1L);
        assertThat(todos.count(null, null, true)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todos WHERE archived_at IS NOT NULL", Integer.class)).isZero();
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ResponseStatusException.class);
        service.archive(1L);
        service.delete(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todos", Integer.class)).isZero();
    }

    @Test
    void automaticArchiveHonorsBoundaryCompletionAndTrash() {
        task(1, null, true, now.minusDays(30));
        task(2, null, true, now.minusDays(30).plusSeconds(1));
        task(3, null, false, now.minusDays(40));
        task(4, null, true, null);
        task(5, null, true, now.minusDays(40));
        jdbc.update("UPDATE todos SET deleted_at = ? WHERE id = 5", now);
        task(6, null, true, now.minusDays(40));
        task(7, 6L, true, now.minusDays(1));
        assertThat(service.archiveExpired(now.minusDays(30), now)).isEqualTo(1);
        assertThat(service.list()).extracting(Todo::getId).containsExactly(1L);
        assertThat(service.list().getFirst().getArchivedAt()).isEqualTo(now);
        assertThat(service.archiveExpired(now.minusDays(30), now)).isZero();
        assertThatThrownBy(() -> service.archive(3L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.archive(7L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void archiveTemplateRendersActionsAndEscapesTitles() throws Exception {
        task(1, null, true, now.minusDays(31));
        jdbc.update("UPDATE todos SET title = '<script>unsafe</script>' WHERE id = 1");
        service.archive(1L);
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var views = new org.thymeleaf.spring6.view.ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        var mvc = MockMvcBuilders.standaloneSetup(new TodoArchiveController(service)).setViewResolvers(views).build();
        mvc.perform(get("/todo/archive")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/todo/1/unarchive")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/todo/1/archive/delete")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;unsafe&lt;/script&gt;")));
    }

    @Test
    void routesAndConfiguredRetention() throws Exception {
        var mock = mock(TodoArchiveService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new TodoArchiveController(mock)).build();
        mvc.perform(get("/todo/archive")).andExpect(status().isOk()).andExpect(view().name("todo-archive"));
        mvc.perform(post("/todo/1/archive")).andExpect(redirectedUrl("/todos"));
        mvc.perform(post("/todo/1/unarchive")).andExpect(redirectedUrl("/todo/archive"));
        mvc.perform(post("/todo/1/archive/delete")).andExpect(redirectedUrl("/todo/archive"));
        verify(mock).archive(1L);
        verify(mock).unarchive(1L);
        verify(mock).delete(1L);
        new TodoArchiveScheduler(mock, 14, "Asia/Tokyo").archiveCompleted();
        verify(mock).archiveExpired(any(), argThat(t -> t != null));
        var captor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        var nowCaptor = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mock).archiveExpired(captor.capture(), nowCaptor.capture());
        assertThat(captor.getValue()).isEqualTo(nowCaptor.getValue().minusDays(14));
        assertThatThrownBy(() -> new TodoArchiveScheduler(mock, 0, "Asia/Tokyo"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
