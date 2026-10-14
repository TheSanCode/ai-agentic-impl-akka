package io.agenticawithakka.api;

import io.agenticawithakka.domain.contracts.ExecutionStatus;
import io.agenticawithakka.domain.contracts.ProjectId;
import io.agenticawithakka.workflow.ProcessLocalInvestigationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP surface for process-local, read-only investigations. */
@RestController
@RequestMapping("/api")
public final class InvestigationController {
    private final ProcessLocalInvestigationService investigations;

    public InvestigationController(ProcessLocalInvestigationService investigations) {
        this.investigations = investigations;
    }

    @PostMapping("/projects/{projectId}/investigations")
    public ResponseEntity<AcceptedExecution> start(
            @PathVariable String projectId,
            @Valid @RequestBody InvestigationRequest request,
            Authentication authentication) {
        var execution = investigations.start(authentication, new ProjectId(projectId), request.instruction());
        var status = URI.create("/api/executions/" + execution.executionId());
        return ResponseEntity.accepted()
                .location(status)
                .body(new AcceptedExecution(execution.executionId(), execution.status(), status.toString(),
                        status + "/result"));
    }

    @GetMapping("/executions/{executionId}")
    public ProcessLocalInvestigationService.ExecutionView status(
            @PathVariable UUID executionId, Authentication authentication) {
        return investigations.status(authentication, executionId);
    }

    @GetMapping("/executions/{executionId}/result")
    public ProcessLocalInvestigationService.StoredResult result(
            @PathVariable UUID executionId, Authentication authentication) {
        return investigations.result(authentication, executionId);
    }

    @PostMapping("/executions/{executionId}/cancel")
    public ProcessLocalInvestigationService.ExecutionView cancel(
            @PathVariable UUID executionId, Authentication authentication) {
        return investigations.cancel(authentication, executionId);
    }

    @PostMapping("/executions/{executionId}/resume")
    public ProcessLocalInvestigationService.ExecutionView resume(
            @PathVariable UUID executionId, Authentication authentication) {
        return investigations.resume(authentication, executionId);
    }

    public record InvestigationRequest(
            @NotBlank @Size(max = 8_000) String instruction) {}

    public record AcceptedExecution(
            String executionId, ExecutionStatus status, String statusUrl, String resultUrl) {}
}
