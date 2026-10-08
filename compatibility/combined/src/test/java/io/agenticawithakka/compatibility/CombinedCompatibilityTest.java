package io.agenticawithakka.compatibility;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot context hosting both a typed ActorSystem and the Spring AI Ollama adapter.
 * An actor delegates one model call to a bounded executor and replies; the model is a local
 * HTTP fixture. No agent workflow is implemented here.
 */
@SpringBootTest(classes = CombinedCompatibilityTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.ai.ollama.init.pull-model-strategy=never",
                "spring.ai.ollama.chat.options.model=compatibility-fixture"})
class CombinedCompatibilityTest {
    private static final AtomicInteger MODEL_CALLS = new AtomicInteger();
    private static final HttpServer SERVER = startFixture();

    sealed interface Command permits Ask, Completed {
    }

    record Ask(String prompt, ActorRef<String> replyTo) implements Command {
    }

    record Completed(String result, ActorRef<String> replyTo) implements Command {
    }

    @Autowired
    private ActorSystem<Command> runtime;

    @DynamicPropertySource
    static void endpoint(DynamicPropertyRegistry properties) {
        properties.add("spring.ai.ollama.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
    }

    @Test
    @Timeout(20)
    void actorDelegatesModelCallThroughSpringAiOnJava25() throws Exception {
        assertThat(Runtime.version().feature()).isEqualTo(25);
        var reply = AskPattern.<Command, String>ask(runtime,
                replyTo -> new Ask("Return the fixture marker.", replyTo),
                Duration.ofSeconds(10), runtime.scheduler());
        assertThat(reply.toCompletableFuture().get(12, TimeUnit.SECONDS)).isEqualTo("agent:fixture-ok");
        assertThat(MODEL_CALLS.get()).isEqualTo(1);
    }

    @AfterAll
    static void stopFixture() {
        SERVER.stop(0);
    }

    static Behavior<Command> modelCallingActor(ChatClient chatClient, ExecutorService modelExecutor) {
        return Behaviors.setup(context -> Behaviors.receive(Command.class)
                .onMessage(Ask.class, ask -> {
                    // Blocking model I/O stays off the actor dispatcher.
                    var call = CompletableFuture.supplyAsync(
                            () -> chatClient.prompt().user(ask.prompt()).call().content(), modelExecutor);
                    context.pipeToSelf(call, (result, failure) -> new Completed(
                            failure == null ? "agent:" + result : "error:" + failure.getClass().getSimpleName(),
                            ask.replyTo()));
                    return Behaviors.same();
                })
                .onMessage(Completed.class, done -> {
                    done.replyTo().tell(done.result());
                    return Behaviors.same();
                })
                .build());
    }

    private static HttpServer startFixture() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/chat", exchange -> {
                try (exchange) {
                    exchange.getRequestBody().readAllBytes();
                    MODEL_CALLS.incrementAndGet();
                    var response = """
                            {"model":"compatibility-fixture","created_at":"2026-10-08T00:00:00Z",
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
        @Bean(destroyMethod = "shutdown")
        ExecutorService modelExecutor() {
            return Executors.newFixedThreadPool(2);
        }

        @Bean(destroyMethod = "terminate")
        ActorSystem<Command> combinedRuntime(ChatClient.Builder builder, ExecutorService modelExecutor) {
            return ActorSystem.create(modelCallingActor(builder.build(), modelExecutor), "combined-probe");
        }
    }
}
