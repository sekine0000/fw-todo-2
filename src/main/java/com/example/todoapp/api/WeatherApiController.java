package com.example.todoapp.api;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WeatherApiController {
    private final WeatherClient weatherClient;

    public WeatherApiController(WeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    @GetMapping("/api/weather")
    public ResponseEntity<Map<String, String>> weather(
            @RequestParam(name = "latitude") double latitude,
            @RequestParam(name = "longitude") double longitude,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || (from != null && to != null && from.isAfter(to))) {
            return ResponseEntity.badRequest().body(Map.of());
        }
        WeatherClient.Result result = weatherClient.fetchWeather(latitude, longitude, from, to);
        if (result.unavailable()) {
            return ResponseEntity.ok().header("X-Weather-Unavailable", "true").body(result.weather());
        }
        return ResponseEntity.ok(result.weather());
    }
}
