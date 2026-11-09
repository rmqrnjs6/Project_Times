package dev.projecttimes;
import java.net.*;
import java.net.http.*;
import java.time.Duration;

@org.springframework.stereotype.Service
public class AnalysisService {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final String base;
    public AnalysisService(@org.springframework.beans.factory.annotation.Value("${app.analysis.base-url}") String base) {
        this.base = base;
    }
    public HttpResponse<String> call(String path, String json) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(REQUEST_TIMEOUT);
        if (json == null) builder.GET();
        else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    public void ready() throws Exception {
        if (call("/ready", null).statusCode() != 200) throw new java.io.IOException("analysis not ready");
    }
}
