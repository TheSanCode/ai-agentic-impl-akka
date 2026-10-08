package io.agenticawithakka.compatibility;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the real Ollama adapter against a synthetic local HTTP fixture, not a model. */
@SpringBootTest(classes = SpringAiCompatibilityTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.ai.ollama.init.pull-model-strategy=never",
                "spring.ai.ollama.chat.options.model=compatibility-fixture"})
class SpringAiCompatibilityTest {
    private static final AtomicReference<String> REQUEST = new AtomicReference<>();
    private static final HttpServer SERVER = startFixture();

    @Autowired
    private ChatClient.Builder clientBuilder;

    @DynamicPropertySource
    static void endpoint(DynamicPropertyRegistry properties) {
        properties.add("spring.ai.ollama.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
    }

    @Test
    @Timeout(15)
    void bootAutoConfigurationAndOllamaRoundTripWorkOnJava25() throws Exception {
        assertThat(Runtime.version().feature()).isEqualTo(25);
        var answer = clientBuilder.build().prompt().user("Return the fixture marker.").call().content();
        assertThat(answer).isEqualTo("fixture-ok");
        var request = JsonMapper.builder().build().readTree(REQUEST.get());
        assertThat(request.get("model").asString()).isEqualTo("compatibility-fixture");
        assertThat(request.get("stream").asBoolean()).isFalse();
        assertThat(request.get("messages").get(0).get("content").asString())
                .isEqualTo("Return the fixture marker.");
    }

    @AfterAll
    static void stopFixture() {
        SERVER.stop(0);
    }

    private static HttpServer startFixture() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/chat", exchange -> {
                try (exchange) {
                    REQUEST.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    var response = """
                            {"model":"compatibility-fixture","created_at":"2026-10-07T00:00:00Z",
                             "message":{"role":"assistant","content":"fixture-ok"},"done":true,
                             "done_reason":"stop","total_duration":1,"prompt_eval_count":1,"eval_count":1}
                            """.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
