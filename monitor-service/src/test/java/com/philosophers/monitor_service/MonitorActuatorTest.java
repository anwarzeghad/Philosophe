package com.philosophers.monitor_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/** Supervision : sondes Kubernetes (liveness/readiness) et métriques Prometheus. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MonitorActuatorTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> call(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (body != null) {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void livenessAndReadinessProbes_areUp() throws Exception {
        assertTrue(call("GET", "/actuator/health/liveness", null).body().contains("UP"));
        assertTrue(call("GET", "/actuator/health/readiness", null).body().contains("UP"));
    }

    @Test
    void eventsAreExposedAsPrometheusMetric() throws Exception {
        assertEquals(200, call("POST", "/events", "{\"philosopherId\":1,\"state\":\"EATING\",\"logicalClock\":5}").statusCode());
        String metrics = call("GET", "/actuator/prometheus", null).body();
        assertTrue(metrics.contains("philosophers_events_total{application=\"monitor-service\",state=\"EATING\"}"),
                "métrique par état absente");
    }
}
