package com.example.todoapp;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class BurndownTests {
    JdbcTemplate jdbc;
    SprintMapper mapper;
    BurndownService service;
    Sprint sprint;

    @BeforeEach
    void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:sprint" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE todos (id BIGINT PRIMARY KEY, title VARCHAR(255), parent_id BIGINT, deleted_at TIMESTAMP, archived BOOLEAN DEFAULT FALSE, completed BOOLEAN DEFAULT FALSE, completed_at TIMESTAMP)");
        var schema = new ResourceDatabasePopulator(new ClassPathResource("db/06_create_sprint.sql"));
        schema.execute(ds);
        schema.execute(ds); // Existing databases can run initialization again.
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        var sf = factory.getObject();
        sf.getConfiguration().setMapUnderscoreToCamelCase(true);
        sf.getConfiguration().addMapper(SprintMapper.class);
        mapper = new SqlSessionTemplate(sf).getMapper(SprintMapper.class);
        service = new BurndownService(mapper);
        jdbc.execute("INSERT INTO todos (id, title, completed, completed_at) VALUES (1, 'A', TRUE, '2026-01-02 23:59:59'), (2, 'B', FALSE, NULL)");
        sprint = new Sprint();
        sprint.setName("Sprint 1");
        sprint.setStartDate(LocalDate.of(2026, 1, 1));
        sprint.setEndDate(LocalDate.of(2026, 1, 4));
        sprint.setTaskIds(java.util.List.of(1L, 2L, 1L));
        service.create(sprint);
    }

    @Test
    void dailyCountsIdealFutureAndStatus() {
        var data = service.load(sprint, LocalDate.of(2026, 1, 2));
        assertThat(data.labels()).containsExactly("2025-12-31", "2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04");
        assertThat(data.ideal()).containsExactly(2.0, 1.5, 1.0, 0.5, 0.0);
        assertThat(data.actual()).containsExactly(2L, 2L, 1L, null, null);
        assertThat(data.status()).isEqualTo("順調");
        assertThat(service.load(sprint, LocalDate.of(2026, 1, 1)).status()).isEqualTo("遅延");
        jdbc.execute("UPDATE todos SET completed_at = '2026-01-01 00:00:00' WHERE id = 1");
        assertThat(service.load(sprint, LocalDate.of(2026, 1, 1)).status()).isEqualTo("前倒し");
        assertThat(service.load(sprint, LocalDate.of(2025, 12, 30)).actual()).containsOnlyNulls();
        assertThat(service.load(sprint, LocalDate.of(2025, 12, 30)).status()).isEqualTo("開始前");
        assertThat(service.load(sprint, LocalDate.of(2026, 2, 1)).status()).isEqualTo("遅延");
    }

    @Test
    void scopeStaysFixedAfterArchiveAndDeletionAndSingleDayWorks() {
        jdbc.execute("UPDATE todos SET archived = TRUE, deleted_at = CURRENT_TIMESTAMP WHERE id = 1");
        assertThat(service.load(sprint, sprint.getEndDate()).remaining()).isEqualTo(1);
        assertThat(service.load(sprint, sprint.getEndDate()).total()).isEqualTo(2);
        sprint.setStartDate(sprint.getEndDate());
        assertThat(service.load(sprint, sprint.getEndDate()).ideal()).containsExactly(2.0, 0.0);
        sprint.setEndDate(sprint.getStartDate().minusDays(1));
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(sprint));
    }

    @Test
    void templatesSelectionEmptyAndValidation() throws Exception {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var views = new ThymeleafViewResolver(); views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8");
        var mvc = MockMvcBuilders.standaloneSetup(new BurndownController(mapper, service)).setViewResolvers(views).build();
        String html = mvc.perform(get("/burndown").param("sprintId", sprint.getId().toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("burndown-chart", "borderDash: [6, 4]", "Sprint 1");
        mvc.perform(get("/burndown").param("sprintId", "9999")).andExpect(status().isNotFound());
        mvc.perform(get("/sprints/new")).andExpect(status().isOk()).andExpect(view().name("sprint-create"));
        mvc.perform(post("/sprints").param("name", "bad").param("startDate", "2026-01-04").param("endDate", "2026-01-01"))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        mvc.perform(post("/sprints").param("name", "Empty sprint").param("startDate", "2026-01-01").param("endDate", "2026-01-01"))
                .andExpect(status().is3xxRedirection());
        Sprint empty = mapper.findAll().stream().filter(s -> s.getName().equals("Empty sprint")).findFirst().orElseThrow();
        assertThat(service.load(empty, LocalDate.of(2026, 1, 1)).actual()).containsExactly(0L, 0L);
        assertThat(service.load(empty, LocalDate.of(2026, 1, 1)).status()).isEqualTo("順調");
        mvc.perform(get("/burndown").param("sprintId", empty.getId().toString()))
                .andExpect(status().isOk()).andExpect(model().attribute("selected", empty));
        jdbc.execute("DELETE FROM sprint_task"); jdbc.execute("DELETE FROM sprint");
        mvc.perform(get("/burndown")).andExpect(status().isOk()).andExpect(view().name("burndown"));
    }
}
