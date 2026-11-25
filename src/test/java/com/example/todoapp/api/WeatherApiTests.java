package com.example.todoapp.api;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WeatherApiTests {
    private static final String URL = "https://api.open-meteo.com/v1/forecast?latitude=35.0&longitude=139.0"
            + "&daily=weather_code&timezone=auto&forecast_days=16";

    @Test
    void mapsWeatherAndFiltersDates() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"daily":{"time":["2026-11-25","2026-11-26","2026-11-27"],"weather_code":[0,61,null]}}
                """, APPLICATION_JSON));
        mvc(new WeatherClient(builder.build())).perform(get("/api/weather")
                .param("latitude", "35.0").param("longitude", "139.0").param("from", "2026-11-26"))
                .andExpect(status().isOk()).andExpect(content().json("{\"2026-11-26\":\"雨\"}"))
                .andExpect(header().doesNotExist("X-Weather-Unavailable"));
        server.verify();
    }

    @Test
    void timeoutReturnsEmptyWeather() throws Exception {
        ClientHttpRequestFactory factory = mock(ClientHttpRequestFactory.class);
        when(factory.createRequest(any(), any(HttpMethod.class))).thenThrow(new SocketTimeoutException());
        mvc(new WeatherClient(RestClient.builder().requestFactory(factory).build()))
                .perform(get("/api/weather").param("latitude", "35").param("longitude", "139"))
                .andExpect(status().isOk()).andExpect(content().json("{}"))
                .andExpect(header().string("X-Weather-Unavailable", "true"));
    }

    @Test
    void malformedResponseReturnsEmptyWeather() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"daily\":{\"time\":[],\"weather_code\":[0]}}", APPLICATION_JSON));
        mvc(new WeatherClient(builder.build())).perform(get("/api/weather")
                .param("latitude", "35.0").param("longitude", "139.0"))
                .andExpect(status().isOk()).andExpect(content().json("{}"))
                .andExpect(header().string("X-Weather-Unavailable", "true"));
        server.verify();
    }

    @Test
    void invalidCoordinatesDoNotCallProvider() throws Exception {
        WeatherClient client = mock(WeatherClient.class);
        mvc(client).perform(get("/api/weather").param("latitude", "NaN").param("longitude", "139"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(client);
    }

    private MockMvc mvc(WeatherClient client) {
        return MockMvcBuilders.standaloneSetup(new WeatherApiController(client)).build();
    }
}
