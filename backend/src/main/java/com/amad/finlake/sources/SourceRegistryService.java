package com.amad.finlake.sources;

import com.amad.finlake.common.AmadProperties;
import com.amad.finlake.sources.ProbeModels.ColumnSchema;
import com.amad.finlake.sources.ProbeModels.CreateSourceRequest;
import com.amad.finlake.sources.ProbeModels.SourceProbeRequest;
import com.amad.finlake.sources.ProbeModels.SourceProbeResponse;
import com.amad.finlake.sources.ProbeModels.SourceSummary;
import com.amad.finlake.sources.ProbeModels.TableSchemaResponse;
import com.amad.finlake.sources.ProbeModels.TableSummary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SourceRegistryService {

    private final Map<SourceEngine, AmadSourceConnector> connectors;
    private final Map<String, RegisteredSource> registry = new ConcurrentHashMap<>();

    public SourceRegistryService(List<AmadSourceConnector> connectorList, AmadProperties props) {
        this.connectors = connectorList.stream()
                .collect(Collectors.toMap(AmadSourceConnector::engine, Function.identity()));
        if (props.mssql() != null) {
            var m = props.mssql();
            putInitial(new SourceConfig("amad-mssql", "FinLakeAmad SQL Server", SourceEngine.MSSQL,
                    m.host(), m.port(), m.database(), m.username(), m.password(), false));
        }
        if (props.postgres() != null) {
            var p = props.postgres();
            putInitial(new SourceConfig("amad-postgres", "FinLakeAmad PostgreSQL", SourceEngine.POSTGRES,
                    p.host(), p.port(), p.database(), p.username(), p.password(), false));
        }
    }

    public List<SourceSummary> listSources() {
        return registry.values().stream().map(RegisteredSource::toSummary).toList();
    }

    public SourceSummary register(CreateSourceRequest req) {
        SourceConfig cfg = new SourceConfig(req.id(), req.name(), req.engine(), req.host(),
                req.port(), req.database(), req.username(), req.password(), req.tlsEnabled());
        RegisteredSource entry = new RegisteredSource(cfg, "UNTESTED", null, null, null);
        registry.put(cfg.id(), entry);
        return entry.toSummary();
    }

    public SourceProbeResponse probe(SourceProbeRequest req) {
        SourceConfig cfg = new SourceConfig("ad-hoc", "Ad-hoc probe", req.engine(), req.host(),
                req.port(), req.database(), req.username(), req.password(), req.tlsEnabled());
        return connectorFor(cfg.engine()).probe(cfg);
    }

    public SourceProbeResponse testSource(String id) {
        RegisteredSource entry = find(id);
        SourceProbeResponse res = connectorFor(entry.config.engine()).probe(entry.config);
        registry.put(id, new RegisteredSource(
                entry.config,
                res.connected() ? "CONNECTED" : "FAILED",
                res.serverVersion(),
                Instant.now(),
                res.errorHint()
        ));
        return res;
    }

    public List<TableSummary> listTables(String id) {
        SourceConfig cfg = find(id).config;
        return connectorFor(cfg.engine()).listTables(cfg);
    }

    public TableSchemaResponse describeTable(String id, String schema, String table) {
        SourceConfig cfg = find(id).config;
        AmadSourceConnector conn = connectorFor(cfg.engine());
        String effectiveSchema = (schema == null || schema.isBlank()) ? conn.defaultSchema() : schema;
        List<ColumnSchema> cols = conn.describeTable(cfg, effectiveSchema, table);
        return new TableSchemaResponse(id, effectiveSchema, table, cols);
    }

    private RegisteredSource find(String id) {
        RegisteredSource src = registry.get(id);
        if (src == null) {
            throw new NoSuchElementException("Unknown source: " + id);
        }
        return src;
    }

    private AmadSourceConnector connectorFor(SourceEngine engine) {
        AmadSourceConnector c = connectors.get(engine);
        if (c == null) {
            throw new IllegalArgumentException("Unsupported engine: " + engine);
        }
        return c;
    }

    private void putInitial(SourceConfig cfg) {
        registry.put(cfg.id(), new RegisteredSource(cfg, "UNTESTED", null, null, null));
    }

    private record RegisteredSource(
            SourceConfig config,
            String status,
            String serverVersion,
            Instant lastTestedAt,
            String lastError
    ) {
        SourceSummary toSummary() {
            return new SourceSummary(config.id(), config.name(), config.engine(), config.host(),
                    config.port(), config.database(), status, serverVersion, lastTestedAt, lastError);
        }
    }
}
