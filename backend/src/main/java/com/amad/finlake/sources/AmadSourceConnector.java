package com.amad.finlake.sources;

import com.amad.finlake.sources.ProbeModels.ColumnSchema;
import com.amad.finlake.sources.ProbeModels.SourceProbeResponse;
import com.amad.finlake.sources.ProbeModels.TableSummary;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** SOLID contract implemented once per relational engine (SQL Server, PostgreSQL, ...). */
public interface AmadSourceConnector {

    SourceEngine engine();

    String defaultSchema();

    String buildJdbcUrl(SourceConfig config);

    String quoteIdentifier(String validatedIdentifier);

    Connection openJdbcConnection(SourceConfig config) throws SQLException;

    SourceProbeResponse probe(SourceConfig config);

    List<TableSummary> listTables(SourceConfig config);

    List<ColumnSchema> describeTable(SourceConfig config, String schema, String table);
}
