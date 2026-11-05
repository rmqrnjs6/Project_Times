package dev.projecttimes;
import java.net.*;
import java.net.http.*;
import java.time.Duration;

public final class AnalysisService {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String base = System.getenv().getOrDefault("ANALYSIS_URL", "http://analysis:8000");
    public HttpResponse<String> call(String path, String json) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5));
        if (json == null) builder.GET();
        else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    public void ready() throws Exception {
        if (call("/ready", null).statusCode() != 200) throw new java.io.IOException("analysis not ready");
    }
}
