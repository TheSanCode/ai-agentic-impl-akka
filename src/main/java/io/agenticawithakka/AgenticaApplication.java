package io.agenticawithakka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ConfigurationPropertiesScan(basePackageClasses = io.agenticawithakka.config.ServiceProperties.class)
public class AgenticaApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgenticaApplication.class, args);
    }
}
