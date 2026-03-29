package com.example.jvmstabilitydemo.oom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OomSimulationTest {

    @Test
    void shouldSimulateBusinessLikeFailureRound() {
        AsyncJobFailureStormSimulator simulator = new AsyncJobFailureStormSimulator(new LeakyLocalRetrySnapshotBuffer());
        AsyncJobFailureStormSimulator.SimulationRoundResult result = simulator.previewOneRound();

        assertNotNull(result);
        assertTrue(result.failedTasks() > 0);
        assertTrue(result.fallbackBufferSize() > 0);
        assertTrue(result.toLogLine().contains("AsyncJobCallbackWorker"));
    }
}
