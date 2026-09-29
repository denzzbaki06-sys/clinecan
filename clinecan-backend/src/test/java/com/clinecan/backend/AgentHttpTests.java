package com.clinecan.backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "clinecan.llm.api-key=")
class AgentHttpTests {
    @Autowired private Environment environment;

    private URI endpoint() {
        return URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/api/agent/run");
    }

    private HttpResponse<String> post(String json) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(endpoint()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test void successfulRequestReturnsGeneratedFiles() throws Exception {
        var response = post("{\"prompt\":\"Create a personal expense tracking dashboard\"}");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("expense-tracker"));
        assertTrue(response.body().contains("src/App.tsx"));
        assertTrue(response.body().contains("VALIDATE"));
    }

    @Test void rejectsInvalidPrompts() throws Exception {
        for (String json : new String[]{"{}", "{\"prompt\":null}", "{\"prompt\":\"\"}", "{\"prompt\":\"   \"}",
                "{\"prompt\":\"" + "x".repeat(10001) + "\"}"}) {
            assertEquals(400, post(json).statusCode());
        }
    }

    @Test void rejectsMalformedJson() throws Exception {
        assertEquals(400, post("{bad json").statusCode());
    }

    @Test void allowsConfiguredFrontendOrigin() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(endpoint())
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:5174")
                .header("Access-Control-Request-Method", "POST").build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("http://localhost:5174", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        }
    }
    @Test void rejectsOversizedBodyBeforeDeserialization() throws Exception {
        assertEquals(413, post("{\"prompt\":\"" + "x".repeat(70000) + "\"}").statusCode());
    }
    @Test void reportsDemoModeWithoutCredentials() throws Exception {
        var response = post("{\"prompt\":\"task manager\"}");
        assertTrue(response.body().contains("\"generationMode\":\"DEMO\""));
        assertFalse(response.body().contains("api-key"));
    }
}
