package io.agenticawithakka;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {
    @LocalServerPort
    private int port;

    @Test
    void healthReturnsOnlyPublicProcessStatus() throws Exception {
        var response = get("/api/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
        var body = JsonMapper.builder().build().readTree(response.body());
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.get("status").asString()).isEqualTo("UP");
        assertThat(body.get("service").asString()).isEqualTo("agenticawithakka");
    }

    @Test
    void businessAndManagementRoutesAreDeniedWhenOidcIsNotConfigured() throws Exception {
        assertThat(get("/api/projects").statusCode()).isEqualTo(401);
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
