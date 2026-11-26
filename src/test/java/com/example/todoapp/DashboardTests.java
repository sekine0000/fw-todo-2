package com.example.todoapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class DashboardTests {
    JdbcTemplate jdbc;
    DashboardService service;
    LocalDate today = LocalDate.of(2026, 1, 3);

    @BeforeEach
    void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:dashboard" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE todos (id BIGINT PRIMARY KEY, parent_id BIGINT, category VARCHAR(255), "
                + "completed BOOLEAN DEFAULT FALSE, due_date DATE, completed_at TIMESTAMP, "
                + "deleted_at TIMESTAMP, archived BOOLEAN DEFAULT FALSE)");
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        var sf = factory.getObject();
        sf.getConfiguration().setMapUnderscoreToCamelCase(true);
        sf.getConfiguration().addMapper(DashboardMapper.class);
        service = new DashboardService(new SqlSessionTemplate(sf).getMapper(DashboardMapper.class));
    }

    @Test
    void aggregatesRespectScopeDateBoundariesAndMissingDays() {
        jdbc.execute("INSERT INTO todos (id, category, completed, due_date, completed_at) VALUES "
                + "(1, '仕事', TRUE, '2025-12-01', '2025-12-28 00:00:00'),"
                + "(2, '仕事', TRUE, NULL, '2026-01-03 23:59:59'),"
                + "(3, '仕事', TRUE, NULL, '2025-12-27 23:59:59'),"
                + "(4, '個人', FALSE, '2026-01-02', NULL),"
                + "(5, '個人', FALSE, '2026-01-03', NULL),"
                + "(6, NULL, FALSE, NULL, NULL),"
                + "(7, '仕事', TRUE, NULL, '2026-01-04 00:00:00')");
        jdbc.execute("INSERT INTO todos (id, parent_id, deleted_at, archived, completed, completed_at) VALUES "
                + "(8, 1, NULL, FALSE, TRUE, '2026-01-03 12:00:00'),"
                + "(9, NULL, CURRENT_TIMESTAMP, FALSE, TRUE, '2026-01-03 12:00:00'),"
                + "(10, NULL, NULL, TRUE, TRUE, '2026-01-03 12:00:00')");
        var data = service.load(today);
        assertThat(data.summary().getTotal()).isEqualTo(7);
        assertThat(data.summary().getCompleted()).isEqualTo(4);
        assertThat(data.summary().getIncomplete()).isEqualTo(3);
        assertThat(data.summary().getOverdue()).isEqualTo(1);
        assertThat(data.daily().keySet()).containsExactly("2025-12-28", "2025-12-29", "2025-12-30",
                "2025-12-31", "2026-01-01", "2026-01-02", "2026-01-03");
        assertThat(data.daily().values()).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 1L);
        assertThat(data.categories()).extracting(DashboardMapper.CategoryCount::getTaskCount)
                .containsExactly(4L, 2L, 1L);
        assertThat(data.categories().getLast().getCategory()).isEqualTo("未分類");
    }

    @Test
    void emptyDashboardRendersRealTemplateAndInlineData() throws Exception {
        var data = service.load(today);
        assertThat(data.summary().getTotal()).isZero();
        assertThat(data.summary().getOverdue()).isZero();
        assertThat(data.daily().values()).containsOnly(0L).hasSize(7);
        assertThat(data.categories()).isEmpty();
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        var mvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).setViewResolvers(views).build();
        var result = mvc.perform(get("/dashboard")).andExpect(status().isOk())
                .andExpect(view().name("dashboard")).andReturn();
        String html = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("ダッシュボード", "タスクがありません", "const completed = 0;",
                "const categories = [];", "chart.js@4.4.8", "id=\"category-chart\"");
        jdbc.update("INSERT INTO todos (id, category) VALUES (1, ?)", "</script><script>alert(1)</script>");
        String populated = mvc.perform(get("/dashboard")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(populated).doesNotContain("</script><script>alert(1)</script>")
                .contains("const incomplete = 1;");
    }
}
