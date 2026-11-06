package dev.projecttimes;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService tasks;
    public TaskController(TaskService tasks) { this.tasks=tasks; }
    public record NewTask(String question,String evidenceRequirement) {}
    public record CancelTask(Long version) {}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(Principal owner,@RequestBody NewTask input) throws Exception {
        var id=tasks.create(owner.getName(),input.question(),input.evidenceRequirement());
        return ResponseEntity.created(URI.create("/api/tasks/"+id)).body(Map.of("id",id,"status","QUEUED","version",0));
    }
    @GetMapping public Object list(Principal owner) throws Exception { return tasks.list(owner.getName()); }
    @GetMapping("/{id}") public Object detail(Principal owner,@PathVariable String id) throws Exception { return tasks.detail(owner.getName(),id); }
    @PostMapping(value="/{id}/cancel",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cancel(Principal owner,@PathVariable String id,@RequestBody CancelTask input) throws Exception {
        tasks.cancel(owner.getName(),id,input.version()); return ResponseEntity.noContent().build();
    }
}
