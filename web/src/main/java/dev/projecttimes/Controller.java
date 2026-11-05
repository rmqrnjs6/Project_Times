package dev.projecttimes;
import com.google.gson.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@WebServlet(urlPatterns={"/ready", "/api/checks", "/api/evaluate"})
public final class Controller extends HttpServlet {
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private Database db() { return (Database)getServletContext().getAttribute("database"); }
    private AnalysisService analysis() { return (AnalysisService)getServletContext().getAttribute("analysis"); }
    private void json(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(gson.toJson(body));
    }
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            if (request.getServletPath().equals("/ready")) {
                db().ready(); analysis().ready();
                json(response, 200, Map.of("status", "ok", "database", "ok", "analysis", "ok"));
            } else if (request.getServletPath().equals("/api/checks")) json(response, 200, db().checks());
            else json(response, 405, Map.of("error", "POST required"));
        } catch (Exception error) {
            getServletContext().log("Dependency failure", error);
            json(response, 503, Map.of("error", "dependency_unavailable"));
        }
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getContentType() == null || !request.getContentType().split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            json(response, 415, Map.of("error", "application/json required")); return;
        }
        var origin = request.getHeader("Origin");
        String ownOrigin = request.getScheme() + "://" + request.getHeader("Host");
        if (origin != null && !origin.equals(ownOrigin)) {
            json(response, 403, Map.of("error", "cross_origin_rejected")); return;
        }
        byte[] bytes = request.getInputStream().readNBytes(32769);
        if (bytes.length > 32768) { json(response, 413, Map.of("error", "body_too_large")); return; }
        String input = new String(bytes, StandardCharsets.UTF_8);
        try {
            var payload = JsonParser.parseString(input).getAsJsonObject();
            if (request.getServletPath().equals("/api/checks")) {
                if (!payload.has("question") || !payload.get("question").isJsonPrimitive() || !payload.getAsJsonPrimitive("question").isString()) throw new IllegalArgumentException();
                String question = payload.get("question").getAsString().strip();
                if (question.isEmpty() || question.length() > 2000) throw new IllegalArgumentException();
                analysis().ready();
                var id = db().saveCheck(question);
                json(response, 201, Map.of("id", id.toString(), "kind", "connectivity_only", "analysis", "ok"));
            } else if (request.getServletPath().equals("/api/evaluate")) {
                var result = analysis().call("/evaluate", input);
                if (result.statusCode() == 422) { json(response, 422, Map.of("error", "invalid_evaluation")); return; }
                if (result.statusCode() != 200) throw new IOException("analysis failed");
                var output = JsonParser.parseString(result.body()).getAsJsonObject();
                var id = db().saveCalculation(output.get("policy_version").getAsString(), input, result.body());
                output.addProperty("run_id", id.toString());
                json(response, 201, output);
            } else json(response, 405, Map.of("error", "GET required"));
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException error) {
            json(response, 400, Map.of("error", "invalid_input_or_analysis_response"));
        } catch (Exception error) {
            getServletContext().log("Operation failed", error);
            json(response, 503, Map.of("error", "dependency_unavailable"));
        }
    }
}
