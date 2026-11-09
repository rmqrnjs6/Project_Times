package dev.projecttimes;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisServiceTest {
    @Test void springPropertyControlsReadinessDestination() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var path=new AtomicReference<String>();
        server.createContext("/ready", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200,-1); exchange.close();
        });
        server.start();
        try {
            new ApplicationContextRunner().withUserConfiguration(AnalysisService.class)
                .withPropertyValues("app.analysis.base-url=http://127.0.0.1:"+server.getAddress().getPort())
                .run(context -> { context.getBean(AnalysisService.class).ready(); assertEquals("/ready",path.get()); });
        } finally { server.stop(0); }
    }
    @Test void jsonPostAndNonSuccessfulReadinessKeepExistingContract() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var received=new AtomicReference<String>();
        server.createContext("/evaluate", exchange -> {
            received.set(exchange.getRequestMethod()+"|"+exchange.getRequestHeaders().getFirst("Content-Type")+"|"+new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] body="{\"policy_version\":\"synthetic\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/ready", exchange -> { exchange.sendResponseHeaders(503,-1); exchange.close(); });
        server.start();
        try {
            new ApplicationContextRunner().withUserConfiguration(AnalysisService.class)
                .withPropertyValues("app.analysis.base-url=http://127.0.0.1:"+server.getAddress().getPort())
                .run(context -> {
                    var service=context.getBean(AnalysisService.class);
                    assertEquals(200,service.call("/evaluate","{}").statusCode());
                    assertEquals("POST|application/json|{}",received.get());
                    assertThrows(IOException.class,service::ready);
                });
        } finally { server.stop(0); }
    }
}
