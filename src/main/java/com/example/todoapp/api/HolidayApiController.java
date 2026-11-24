package com.example.todoapp.api;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HolidayApiController {

    private final HolidayClient holidayClient;

    public HolidayApiController(HolidayClient holidayClient) {
        this.holidayClient = holidayClient;
    }

    @GetMapping("/api/holidays")
    public ResponseEntity<Map<String, String>> holidays(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        HolidayClient.Result result = holidayClient.fetchHolidays();
        if (result.unavailable()) {
            return ResponseEntity.ok().header("X-Holidays-Unavailable", "true").body(result.holidays());
        }
        Map<String, String> holidays = result.holidays();
        if (from == null && to == null) {
            return ResponseEntity.ok(holidays);
        }

        Map<String, String> filtered = new LinkedHashMap<>();
        holidays.forEach((date, name) -> {
            LocalDate holidayDate = LocalDate.parse(date);
            if ((from == null || !holidayDate.isBefore(from))
                    && (to == null || !holidayDate.isAfter(to))) {
                filtered.put(date, name);
            }
        });
        return ResponseEntity.ok(filtered);
    }
}
