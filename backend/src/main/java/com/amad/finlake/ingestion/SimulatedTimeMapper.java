package com.amad.finlake.ingestion;

import com.amad.finlake.common.AmadProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Derives explicit simulated UTC timestamps from PaySim hourly `step` ticks. */
@Component
public class SimulatedTimeMapper {

    private final Instant epoch;

    @Autowired
    public SimulatedTimeMapper(AmadProperties props) {
        String raw = (props.simulationEpoch() == null || props.simulationEpoch().isBlank())
                ? "2026-01-01T00:00:00Z"
                : props.simulationEpoch();
        this.epoch = Instant.parse(raw);
    }

    public SimulatedTimeMapper(Instant epoch) {
        this.epoch = epoch;
    }

    public Instant epoch() {
        return epoch;
    }

    public Instant toSimulatedInstant(int step) {
        return epoch.plus(step, ChronoUnit.HOURS);
    }

    public LocalDate toSimulatedDate(int step) {
        return toSimulatedInstant(step).atZone(ZoneOffset.UTC).toLocalDate();
    }
}
