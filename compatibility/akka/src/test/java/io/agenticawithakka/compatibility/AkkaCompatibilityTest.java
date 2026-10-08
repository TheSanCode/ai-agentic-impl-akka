package io.agenticawithakka.compatibility;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Minimal Spring-managed typed runtime probe; no agent or workflow implementation. */
class AkkaCompatibilityTest {
    record Ping(String value, ActorRef<String> replyTo) {
    }

    @Test
    @Timeout(20)
    void springManagedTypedRequestReplyAndTerminationWorkOnJava25() throws Exception {
        assertThat(Runtime.version().feature()).isEqualTo(25);
        ActorSystem<Ping> system;
        try (var context = new AnnotationConfigApplicationContext(RuntimeConfiguration.class)) {
            @SuppressWarnings("unchecked")
            var runtime = (ActorSystem<Ping>) context.getBean("probeRuntime", ActorSystem.class);
            system = runtime;
            var reply = AskPattern.<Ping, String>ask(system,
                    replyTo -> new Ping("fixture", replyTo), Duration.ofSeconds(5), system.scheduler());
            assertThat(reply.toCompletableFuture().get(6, TimeUnit.SECONDS)).isEqualTo("pong:fixture");
        }
        system.getWhenTerminated().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Configuration(proxyBeanMethods = false)
    static class RuntimeConfiguration {
        @Bean(destroyMethod = "terminate")
        ActorSystem<Ping> probeRuntime() {
            return ActorSystem.create(Behaviors.receiveMessage((Ping ping) -> {
                ping.replyTo().tell("pong:" + ping.value());
                return Behaviors.same();
            }), "compatibility-probe");
        }
    }
}
