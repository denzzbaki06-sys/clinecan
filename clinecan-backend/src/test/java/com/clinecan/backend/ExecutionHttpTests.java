package com.clinecan.backend;
import com.clinecan.backend.sandbox.*;
import com.clinecan.backend.model.*;
import com.clinecan.backend.llm.StructuredJson;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"clinecan.llm.api-key=", "clinecan.sandbox.enabled=false"})
@Import(ExecutionHttpTests.Fixture.class)
class ExecutionHttpTests {
    @LocalServerPort int port;
    static final AtomicBoolean fail = new AtomicBoolean();
    @TestConfiguration static class Fixture {
        @Bean @Primary SandboxService fixtureSandbox() { return files -> new SandboxService.SandboxOutcome(new BuildResult(!fail.get(),fail.get()?1:0,"","",1,fail.get()?"COMPILATION":null,false),fail.get()?null:new byte[]{1,2,3}); }
    }
    final HttpClient http=HttpClient.newHttpClient();
    URI url(String suffix){return URI.create("http://localhost:"+port+"/api/agent/executions"+suffix);}
    String create() throws Exception {
        var r=http.send(HttpRequest.newBuilder(url("")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"prompt\":\"task manager\"}")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(202,r.statusCode());return StructuredJson.MAPPER.readTree(r.body()).path("id").asText();
    }
    HttpResponse<String> events(String id,String after) throws Exception {
        return http.send(HttpRequest.newBuilder(url("/"+id+"/events")).timeout(Duration.ofSeconds(10)).header("Last-Event-ID",after).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void creationOrderingCompletionReconnectAndArtifactHeaders() throws Exception {
        fail.set(false);String id=create();var first=events(id,"0");assertEquals(200,first.statusCode());
        assertTrue(first.body().contains("event:progress"));assertTrue(first.body().contains("event:complete"));assertTrue(first.body().contains("\"status\":\"COMPLETED\""));
        assertTrue(first.body().indexOf("\"stage\":\"ANALYZE\"")<first.body().indexOf("\"stage\":\"BUILD\""));
        var resumed=events(id,"3");assertFalse(resumed.body().contains("id:1\n"));assertFalse(resumed.body().contains("id:2\n"));assertFalse(resumed.body().contains("id:3\n"));assertTrue(resumed.body().contains("event:complete"));
        var artifact=http.send(HttpRequest.newBuilder(url("/"+id+"/artifact")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200,artifact.statusCode());assertTrue(artifact.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment"));assertEquals("nosniff",artifact.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertFalse(first.body().contains("Authorization"));assertFalse(first.body().contains("CLINECAN_LLM_API_KEY"));
    }
    @Test void failedExecutionTerminatesWithoutArtifact() throws Exception {
        fail.set(true);try {String id=create();var stream=events(id,"0");assertTrue(stream.body().contains("event:complete"));assertTrue(stream.body().contains("\"status\":\"FAILED\""));
            var artifact=http.send(HttpRequest.newBuilder(url("/"+id+"/artifact")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(404,artifact.statusCode());
        }finally{fail.set(false);}
    }
    @Test void invalidCreationAndUnknownExecution() throws Exception {
        var bad=http.send(HttpRequest.newBuilder(url("")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"prompt\":\"\"}")).build(),HttpResponse.BodyHandlers.ofString());assertEquals(400,bad.statusCode());
        var missing=http.send(HttpRequest.newBuilder(url("/missing")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(404,missing.statusCode());
        var large=http.send(HttpRequest.newBuilder(url("")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(" ".repeat(65537))).build(),HttpResponse.BodyHandlers.ofString());assertEquals(413,large.statusCode());
    }
}
