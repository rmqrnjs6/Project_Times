package dev.projecttimes;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;
@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(RequestFailure.class) public ResponseEntity<?> request(RequestFailure e) { return ResponseEntity.status(e.status).body(Map.of("error",e.getMessage())); }
    @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<?> invalid() { return ResponseEntity.badRequest().body(Map.of("error","invalid_json")); }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class) public ResponseEntity<?> media() { return ResponseEntity.status(415).body(Map.of("error","application/json_required")); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class) public ResponseEntity<?> method() { return ResponseEntity.status(405).body(Map.of("error","method_not_allowed")); }
    @ExceptionHandler(Exception.class) public ResponseEntity<?> dependency(Exception e) {
        org.slf4j.LoggerFactory.getLogger(ApiErrors.class).warn("Request failed: {}",e.getClass().getSimpleName());
        return ResponseEntity.status(503).body(Map.of("error","dependency_unavailable"));
    }
}
