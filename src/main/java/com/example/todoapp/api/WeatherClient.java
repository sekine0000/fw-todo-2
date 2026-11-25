package com.example.todoapp.api;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WeatherClient {
    private final RestClient restClient;

    public WeatherClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        restClient = RestClient.builder().requestFactory(factory).build();
    }

    WeatherClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public record Forecast(Daily daily) {}
    public record Daily(List<String> time, List<Integer> weather_code) {}
    public record Result(Map<String, String> weather, boolean unavailable) {}

    public Result fetchWeather(double latitude, double longitude, LocalDate from, LocalDate to) {
        try {
            Forecast forecast = restClient.get()
                    .uri("https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
                            + "&daily=weather_code&timezone=auto&forecast_days=16", latitude, longitude)
                    .retrieve().body(Forecast.class);
            if (forecast == null || forecast.daily() == null) return new Result(Map.of(), true);
            Daily daily = forecast.daily();
            if (daily.time() == null || daily.weather_code() == null
                    || daily.time().size() != daily.weather_code().size()) return new Result(Map.of(), true);
            Map<String, String> weather = new LinkedHashMap<>();
            for (int i = 0; i < daily.time().size(); i++) {
                LocalDate date = LocalDate.parse(daily.time().get(i));
                String label = label(daily.weather_code().get(i));
                if (label != null && (from == null || !date.isBefore(from))
                        && (to == null || !date.isAfter(to))) weather.put(date.toString(), label);
            }
            return new Result(weather, false);
        } catch (org.springframework.web.client.RestClientException | IllegalArgumentException
                | java.time.DateTimeException ex) {
            return new Result(Map.of(), true);
        }
    }

    private static String label(Integer code) {
        if (code == null) return null;
        return switch (code) {
            case 0 -> "晴れ";
            case 1, 2 -> "晴れ時々曇り";
            case 3 -> "曇り";
            case 45, 48 -> "霧";
            case 51, 53, 55 -> "霧雨";
            case 56, 57, 66, 67 -> "凍る雨";
            case 61, 63, 65, 80, 81, 82 -> "雨";
            case 71, 73, 75, 77, 85, 86 -> "雪";
            case 95, 96, 99 -> "雷雨";
            default -> null;
        };
    }
}
