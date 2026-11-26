package com.example.todoapp.api;

import com.example.todoapp.SmartDateParser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DateParseControllerTests {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DateParseController(
            new SmartDateParser(Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"),
                    ZoneId.of("Asia/Tokyo"))))).build();

    @Test void returnsIsoDateAsJson() throws Exception {
        mvc.perform(get("/api/date/parse").param("input", "来週金曜"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value("2026-12-04"));
    }

    @Test void returnsJsonErrorsForInvalidOrMissingInput() throws Exception {
        mvc.perform(get("/api/date/parse").param("input", "2月30日"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
        mvc.perform(get("/api/date/parse"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
    }
}
