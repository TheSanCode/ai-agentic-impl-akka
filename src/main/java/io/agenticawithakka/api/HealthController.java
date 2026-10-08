package io.agenticawithakka.api;

import io.agenticawithakka.config.ServiceProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local process health only; does not report readiness of future dependencies. */
@RestController
public class HealthController {
    private final ServiceProperties properties;

    public HealthController(ServiceProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("UP", properties.serviceName());
    }

    public record HealthResponse(String status, String service) {
    }
}
