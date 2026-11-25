package com.amad.finlake.ingestion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionRow(
        long ingestRowId,
        int step,
        String type,
        BigDecimal amount,
        String nameOrig,
        BigDecimal oldbalanceOrg,
        BigDecimal newbalanceOrig,
        String nameDest,
        BigDecimal oldbalanceDest,
        BigDecimal newbalanceDest,
        boolean isFraud,
        boolean isFlaggedFraud,
        Instant eventTimeSimulated,
        LocalDate eventDate
) {}
