package com.example.todoapp.api;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HolidayApiTests {
    private static final String URL = "https://holidays-jp.github.invalid/api/v1/date.json";

    @Test
    void connectionFailuresReturnEmptyObjectWithHeader() throws Exception {
        assertUnavailable(new UnknownHostException("unavailable"));
        assertUnavailable(new ConnectException("connection refused"));
    }

    @Test
    void connectionAndReadTimeoutsReturnEmptyObjectWithHeader() throws Exception {
        assertUnavailable(new SocketTimeoutException("connect timed out"));
        assertUnavailable(new SocketTimeoutException("read timed out"));
    }

    private void assertUnavailable(IOException failure) throws Exception {
        ClientHttpRequestFactory factory = mock(ClientHttpRequestFactory.class);
        when(factory.createRequest(any(), any(HttpMethod.class))).thenThrow(failure);
        HolidayClient client = new HolidayClient(RestClient.builder().requestFactory(factory).build());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HolidayApiController(client)).build();
        mvc.perform(get("/api/holidays"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"))
                .andExpect(header().string("X-Holidays-Unavailable", "true"));
        mvc.perform(get("/api/holidays").param("from", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"))
                .andExpect(header().string("X-Holidays-Unavailable", "true"));
    }

    @Test
    void successPreservesBodyAndDateFilteringWithoutUnavailableHeader() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String body = "{\"2026-01-01\":\"元日\",\"2026-02-11\":\"建国記念の日\"}";
        server.expect(requestTo(URL)).andRespond(withSuccess(body, APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(body, APPLICATION_JSON));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new HolidayApiController(new HolidayClient(builder.build()))).build();
        mvc.perform(get("/api/holidays"))
                .andExpect(status().isOk()).andExpect(content().json(body))
                .andExpect(header().doesNotExist("X-Holidays-Unavailable"));
        mvc.perform(get("/api/holidays").param("from", "2026-02-01").param("to", "2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"2026-02-11\":\"建国記念の日\"}"))
                .andExpect(header().doesNotExist("X-Holidays-Unavailable"));
        server.verify();
    }
}
