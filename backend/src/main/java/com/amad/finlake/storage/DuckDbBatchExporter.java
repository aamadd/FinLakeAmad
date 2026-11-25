package com.amad.finlake.storage;

import com.amad.finlake.ingestion.TransactionRow;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Date;
import java.util.List;

/** DRY DuckDB-backed exporter used by both ParquetFileWriter and CsvFileWriter. */
@Component
public class DuckDbBatchExporter {

    private static final String CREATE_STAGING = """
            CREATE TEMP TABLE amad_batch (
                ingest_row_id BIGINT,
                step INTEGER,
                type VARCHAR,
                amount DECIMAL(19,4),
                nameOrig VARCHAR,
                oldbalanceOrg DECIMAL(19,4),
                newbalanceOrig DECIMAL(19,4),
                nameDest VARCHAR,
                oldbalanceDest DECIMAL(19,4),
                newbalanceDest DECIMAL(19,4),
                isFraud BOOLEAN,
                isFlaggedFraud BOOLEAN,
                event_time_simulated TIMESTAMP,
                event_date DATE
            )
            """;

    private static final String INSERT_ROW = """
            INSERT INTO amad_batch VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    public void export(List<TransactionRow> rows, Path outputFile, String copyOptionsSql) {
        String safePath = outputFile.toAbsolutePath().toString().replace("\\", "/").replace("'", "''");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             Statement st = conn.createStatement()) {
            st.execute(CREATE_STAGING);
            try (PreparedStatement ps = conn.prepareStatement(INSERT_ROW)) {
                for (TransactionRow r : rows) {
                    ps.setLong(1, r.ingestRowId());
                    ps.setInt(2, r.step());
                    ps.setString(3, r.type());
                    ps.setBigDecimal(4, r.amount());
                    ps.setString(5, r.nameOrig());
                    ps.setBigDecimal(6, r.oldbalanceOrg());
                    ps.setBigDecimal(7, r.newbalanceOrig());
                    ps.setString(8, r.nameDest());
                    ps.setBigDecimal(9, r.oldbalanceDest());
                    ps.setBigDecimal(10, r.newbalanceDest());
                    ps.setBoolean(11, r.isFraud());
                    ps.setBoolean(12, r.isFlaggedFraud());
                    ps.setTimestamp(13, Timestamp.from(r.eventTimeSimulated()));
                    ps.setDate(14, Date.valueOf(r.eventDate()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            st.execute("COPY amad_batch TO '" + safePath + "' (" + copyOptionsSql + ")");
        } catch (Exception ex) {
            throw new IllegalStateException("DuckDB export failed for " + outputFile.getFileName(), ex);
        }
    }
}
