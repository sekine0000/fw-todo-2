package com.example.todoapp.api;

import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HolidayClient {

    private final RestClient restClient;

    public HolidayClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    HolidayClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public record Result(Map<String, String> holidays, boolean unavailable) {}

    public Result fetchHolidays() {
        try {
            Map<String, String> holidays = restClient.get()
                .uri("https://holidays-jp.github.invalid/api/v1/date.json")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, String>>() {
                });
            return holidays == null ? new Result(Map.of(), true) : new Result(holidays, false);
        } catch (RestClientException ex) {
            return new Result(Map.of(), true);
        }
    }
}
