package com.example.cpuhightroubleshootingdemo.cpu;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpuHotSpotPreviewTest {

    @Test
    void shouldPreviewBusySpinScenario() {
        BusySpinScheduleScannerDemo.ScanPreview preview = BusySpinScheduleScannerDemo.previewScenario();

        assertThat(preview.hotThreadName()).isEqualTo(BusySpinScheduleScannerDemo.HOT_THREAD_NAME);
        assertThat(preview.bucketCount()).isEqualTo(96);
        assertThat(preview.emptyScanIterations()).isGreaterThan(10_000);
        assertThat(preview.payloadChecks()).isGreaterThan(1_000);
    }

    @Test
    void shouldPreviewRetryStormScenario() {
        AsyncRetryStormCpuDemo.RetryStormPreview preview = AsyncRetryStormCpuDemo.previewScenario();

        assertThat(preview.hotThreadName()).isEqualTo(AsyncRetryStormCpuDemo.HOT_THREAD_NAME);
        assertThat(preview.totalAttempts()).isEqualTo(9_000);
        assertThat(preview.hottestJobAttempts()).isGreaterThan(2_500);
        assertThat(preview.jobAttempts()).hasSize(3);
    }
}
