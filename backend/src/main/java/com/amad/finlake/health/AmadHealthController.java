package com.amad.finlake.health;

import com.amad.finlake.common.AmadProperties;
import com.amad.finlake.sources.SourceRegistryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AmadHealthController {

    private final SourceRegistryService sources;
    private final AmadProperties props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public AmadHealthController(SourceRegistryService sources, AmadProperties props) {
        this.sources = sources;
        this.props = props;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("amad-mssql", checkSource("amad-mssql"));
        components.put("amad-postgres", checkSource("amad-postgres"));
        components.put("amad-minio", checkHttp(props.minio().endpoint() + "/minio/health/live"));
        components.put("amad-trino", checkHttp(props.trino().endpoint() + "/v1/info"));
        boolean allUp = components.values().stream().allMatch("UP"::equals);
        return Map.of(
                "status", allUp ? "UP" : "DEGRADED",
                "service", "amad-control-plane",
                "timestamp", Instant.now().toString(),
                "components", components
        );
    }

    private String checkSource(String id) {
        try {
            return sources.testSource(id).connected() ? "UP" : "DOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }

    private String checkHttp(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build();
            int code = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            return (code >= 200 && code < 300) ? "UP" : "DOWN (HTTP " + code + ")";
        } catch (Exception ex) {
            return "DOWN";
        }
    }
}
