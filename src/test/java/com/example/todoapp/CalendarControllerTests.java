package com.example.todoapp;

import java.time.YearMonth;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.util.XpathExpectationsHelper;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CalendarControllerTests {
    @Test
    void csvDownloadPreservesConditionsAndEscapesCells() throws Exception {
        TodoService service = org.mockito.Mockito.mock(TodoService.class);
        Todo todo = new Todo();
        todo.setTitle("=SUM(1,2)\"日本語\"\n続き");
        todo.setCategory("+ジャンル");
        todo.setPriority(1);
        todo.setCompleted(true);
        todo.setCompletedAt(java.time.LocalDateTime.of(2026, 11, 25, 10, 0));
        org.mockito.Mockito.when(service.searchCsv("日本語", "資格", "desc", true, false))
                .thenReturn(java.util.List.of(todo));
        var csvMvc = MockMvcBuilders.standaloneSetup(
                new com.example.todoapp.api.TodoApiController(service)).build();
        csvMvc.perform(get("/api/todos.csv").param("keyword", "日本語").param("category", "資格")
                .param("order", "desc").param("showCompleted", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"todos.csv\""))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().bytes(("\uFEFF\"やること\",\"ジャンル\",\"優先度\",\"期限\",\"状態\"\r\n"
                        + "\"'=SUM(1,2)\"\"日本語\"\"\n続き\",\"'+ジャンル\",\"高\",\"\",\"完了 2026-11-25\"\r\n")
                        .getBytes(StandardCharsets.UTF_8)));
        org.mockito.Mockito.when(service.searchCsv("", "", "asc", false, true))
                .thenReturn(java.util.List.of());
        csvMvc.perform(get("/api/todos.csv").param("trash", "1").param("order", "invalid"))
                .andExpect(status().isOk())
                .andExpect(content().bytes("\uFEFF\"やること\",\"ジャンル\",\"優先度\",\"期限\",\"状態\"\r\n"
                        .getBytes(StandardCharsets.UTF_8)));
        org.mockito.Mockito.verify(service).searchCsv("", "", "asc", false, true);
    }

    @Test
    void pinTogglePreservesListConditions() throws Exception {
        TodoService service = org.mockito.Mockito.mock(TodoService.class);
        var listMvc = MockMvcBuilders.standaloneSetup(new HomeController(service)).build();
        listMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/todos/42/pin")
                .characterEncoding("UTF-8")
                .param("keyword", "調べる & 確認")
                .param("category", "プログラミング")
                .param("order", "desc").param("showCompleted", "true").param("page", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/todos?keyword="
                        + java.net.URLEncoder.encode("調べる & 確認", StandardCharsets.UTF_8)
                        + "&category=" + java.net.URLEncoder.encode("プログラミング", StandardCharsets.UTF_8)
                        + "&order=desc&showCompleted=true&page=2"));
        org.mockito.Mockito.verify(service).togglePinned(42L);
    }

    @Test
    void pinnedOrderPrecedesBothDueDateOrders() throws Exception {
        var configuration = new org.apache.ibatis.session.Configuration();
        try (var xml = getClass().getResourceAsStream("/mapper/TodoMapper.xml")) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(xml, configuration,
                    "mapper/TodoMapper.xml", configuration.getSqlFragments()).parse();
        }
        for (String order : java.util.List.of("asc", "desc")) {
            var params = new java.util.HashMap<String, Object>();
            params.put("order", order);
            params.put("showCompleted", true);
            params.put("offset", 0L);
            String sql = configuration.getMappedStatement("com.example.todoapp.TodoMapper.searchPage")
                    .getBoundSql(params).getSql().replaceAll("\\s+", " ");
            org.junit.jupiter.api.Assertions.assertTrue(sql.contains(
                    "ORDER BY pinned DESC, due_date " + order.toUpperCase() + " , id ASC LIMIT 10"), sql);
        }
    }

    private MockMvc mvc;

    // Inspect the main element so HTML void elements in the head need no XML conversion.
    private static HtmlXpath xpath(String expression) {
        return new HtmlXpath(expression);
    }

    private record HtmlXpath(String expression) {
        private byte[] main(org.springframework.test.web.servlet.MvcResult result) throws Exception {
            String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            return html.substring(html.indexOf("<main"), html.indexOf("</main>") + 7)
                    .getBytes(StandardCharsets.UTF_8);
        }

        ResultMatcher string(String expected) {
            return result -> new XpathExpectationsHelper(expression, null)
                    .assertString(main(result), "UTF-8", expected);
        }

        ResultMatcher nodeCount(int expected) {
            return result -> new XpathExpectationsHelper(expression, null)
                    .assertNodeCount(main(result), "UTF-8", expected);
        }
    }

    @BeforeEach
    void setup() {
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        ThymeleafViewResolver views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new CalendarController()).setViewResolvers(views).build();
    }

    @Test
    void aprilHasSundayFirstGridAndEmptyCellsWithoutDates() throws Exception {
        mvc.perform(get("/calendar").param("year", "2026").param("month", "4"))
                .andExpect(status().isOk())
                .andExpect(xpath("//h1").string("2026年4月"))
                .andExpect(xpath("//table/@data-from").string("2026-04-01"))
                .andExpect(xpath("//table/@data-to").string("2026-04-30"))
                .andExpect(xpath("//thead/tr/th[1]").string("日"))
                .andExpect(xpath("//thead/tr/th[7]").string("土"))
                .andExpect(xpath("//tbody/tr").nodeCount(5))
                .andExpect(xpath("//tbody/tr/td").nodeCount(35))
                .andExpect(xpath("//td[@data-date]").nodeCount(30))
                .andExpect(xpath("//td[not(@data-date)]").nodeCount(5))
                .andExpect(xpath("//td[not(@data-date)]/span").nodeCount(0))
                .andExpect(xpath("//tbody/tr[1]/td[4]/@data-date").string("2026-04-01"))
                .andExpect(xpath("//td[@data-date='2026-04-04']/span").string("4"));
    }

    @Test
    void navigationCrossesYearBoundaries() throws Exception {
        mvc.perform(get("/calendar").param("year", "2026").param("month", "1"))
                .andExpect(xpath("//nav/a[1]/@href").string("/calendar?year=2025&month=12"));
        mvc.perform(get("/calendar").param("year", "2026").param("month", "12"))
                .andExpect(xpath("//nav/a[2]/@href").string("/calendar?year=2027&month=1"));
    }

    @Test
    void leapYearAndSixWeekMonthRenderCorrectly() throws Exception {
        mvc.perform(get("/calendar").param("year", "2024").param("month", "2"))
                .andExpect(xpath("//table/@data-to").string("2024-02-29"))
                .andExpect(xpath("//td[@data-date]").nodeCount(29));
        mvc.perform(get("/calendar").param("year", "2026").param("month", "8"))
                .andExpect(xpath("//tbody/tr").nodeCount(6))
                .andExpect(xpath("//td[@data-date]").nodeCount(31));
    }

    @Test
    void omittedParametersUseCurrentYearAndMonthIndependently() throws Exception {
        YearMonth now = YearMonth.now();
        mvc.perform(get("/calendar"))
                .andExpect(model().attribute("year", now.getYear()))
                .andExpect(model().attribute("month", now.getMonthValue()));
        mvc.perform(get("/calendar").param("year", "2024"))
                .andExpect(model().attribute("year", 2024))
                .andExpect(model().attribute("month", now.getMonthValue()));
        mvc.perform(get("/calendar").param("month", "2"))
                .andExpect(model().attribute("year", now.getYear()))
                .andExpect(model().attribute("month", 2));
    }

    @Test
    void invalidMonthReturnsBadRequest() throws Exception {
        mvc.perform(get("/calendar").param("month", "13"))
                .andExpect(status().isBadRequest());
    }
}
