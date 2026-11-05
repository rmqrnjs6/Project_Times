package dev.projecttimes;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
public class WorkbenchController {
    private final WorkbenchService workbench;
    private final org.springframework.boot.availability.ApplicationAvailability availability;
    public WorkbenchController(WorkbenchService workbench,org.springframework.boot.availability.ApplicationAvailability availability) { this.workbench=workbench; this.availability=availability; }
    @GetMapping("/ready") public Object ready() throws Exception {
        if (availability.getReadinessState()!=org.springframework.boot.availability.ReadinessState.ACCEPTING_TRAFFIC) throw new RequestFailure(503,"initializing");
        workbench.ready(); return Map.of("status","ok");
    }
    @GetMapping("/api/csrf") public Object csrf(CsrfToken token) { return Map.of("token",token.getToken(),"header",token.getHeaderName(),"parameter",token.getParameterName()); }
    @GetMapping("/api/checks") public Object checks(Principal owner) throws Exception { return workbench.checks(owner.getName()); }
    public record Question(String question) {}
    @PostMapping(value="/api/checks",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(Principal owner,@RequestBody Question input) throws Exception { return ResponseEntity.status(201).body(workbench.saveCheck(owner.getName(),input.question())); }
    @PostMapping(value="/api/evaluate",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> evaluate(Principal owner,@RequestBody JsonNode input) throws Exception { return ResponseEntity.status(201).body(workbench.evaluate(owner.getName(),input)); }
    public record Material(String sourceUrl,String content,String mediaType) {}
    @PostMapping(value="/api/materials",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> material(Principal owner,@RequestBody Material input) throws Exception { return ResponseEntity.status(201).body(workbench.material(owner.getName(),input.sourceUrl(),input.content(),input.mediaType())); }
    @GetMapping("/api/admin/security") public Object security() { return Map.of("authentication","session","external_materials","quarantine_only"); }
}
