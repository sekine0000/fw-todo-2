package com.example.todoapp;

import com.example.todoapp.api.ProgressController;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProgressTests {
    @Test
    void aggregatesActiveParentsAndHandlesEmptyData() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:progress;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE todos (category VARCHAR(255), completed BOOLEAN, "
                + "deleted_at TIMESTAMP, archived BOOLEAN DEFAULT FALSE, parent_id BIGINT)");
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        var sessions = factory.getObject();
        sessions.getConfiguration().addMapper(ProgressMapper.class);
        var mapper = new SqlSessionTemplate(sessions).getMapper(ProgressMapper.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ProgressController(mapper)).build();
        mvc.perform(get("/api/todos/progress"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overall.percentage").value(0))
                .andExpect(jsonPath("$.categories").isEmpty());
        jdbc.execute("INSERT INTO todos(category, completed) VALUES ('A', TRUE), ('A', FALSE), "
                + "('B', TRUE), ('', FALSE), (NULL, FALSE)");
        jdbc.execute("INSERT INTO todos(category, completed, archived, parent_id, deleted_at) VALUES "
                + "('excluded', TRUE, TRUE, NULL, NULL), ('excluded', TRUE, FALSE, 1, NULL), "
                + "('excluded', TRUE, FALSE, NULL, CURRENT_TIMESTAMP)");
        mvc.perform(get("/api/todos/progress"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.overall.total").value(5))
                .andExpect(jsonPath("$.overall.completed").value(2))
                .andExpect(jsonPath("$.overall.percentage").value(40))
                .andExpect(jsonPath("$.categories.length()").value(3))
                .andExpect(jsonPath("$.categories[0].percentage").value(50))
                .andExpect(jsonPath("$.categories[1].percentage").value(100))
                .andExpect(jsonPath("$.categories[2].category").value("未分類"));
    }
}
