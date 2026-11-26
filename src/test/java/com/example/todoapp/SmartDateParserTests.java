package com.example.todoapp;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmartDateParserTests {
    private SmartDateParser at(String instant) {
        return new SmartDateParser(Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Tokyo")));
    }

    @Test void parsesRequestedExpressions() {
        var parser = at("2026-11-26T00:00:00Z");
        assertEquals(LocalDate.of(2026, 11, 27), parser.parse("明日"));
        assertEquals(LocalDate.of(2026, 11, 29), parser.parse("3日後"));
        assertEquals(LocalDate.of(2026, 12, 4), parser.parse("来週金曜"));
        assertEquals(LocalDate.of(2026, 12, 25), parser.parse("12月25日"));
        assertEquals(LocalDate.of(2026, 12, 25), parser.parse("　１２月２５日　"));
        assertEquals(LocalDate.of(2028, 2, 29), parser.parse("2028年2月29日"));
        assertEquals(LocalDate.of(2028, 2, 29), parser.parse("2028-02-29"));
    }

    @Test void handlesYearAndWeekBoundariesInJapanTime() {
        var parser = at("2026-12-31T14:59:00Z");
        assertEquals(LocalDate.of(2027, 1, 1), parser.parse("明日"));
        assertEquals(LocalDate.of(2027, 1, 8), parser.parse("来週金曜日"));
        assertEquals(LocalDate.of(2026, 1, 1), parser.parse("1月1日"));
        assertEquals(LocalDate.of(2027, 1, 1), at("2026-12-31T15:00:00Z").parse("今日"));
        assertEquals(LocalDate.of(2026, 11, 30), at("2026-11-29T00:00:00Z").parse("来週月曜"));
    }

    @Test void rejectsInvalidAndOutOfRangeInputs() {
        var parser = at("2026-11-26T00:00:00Z");
        for (String input : new String[]{null, "", " ", "2月29日", "4月31日", "13月1日",
                "2026-02-30", "来週", "明日まで", "99999999999999999999日後", "x".repeat(101)}) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(input), input);
        }
    }
}
