package io.agenticawithakka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgenticaApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgenticaApplication.class, args);
    }
}
