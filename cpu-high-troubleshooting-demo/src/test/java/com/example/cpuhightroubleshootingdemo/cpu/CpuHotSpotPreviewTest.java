package com.example.cpuhightroubleshootingdemo.cpu;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpuHotSpotPreviewTest {

    @Test
    void shouldPreviewXtimerEmptyScanScenario() {
        XtimerEmptyScanCpuDemo.ScanPreview preview = XtimerEmptyScanCpuDemo.previewScenario();

        assertThat(preview.hotThreadName()).isEqualTo(XtimerEmptyScanCpuDemo.HOT_THREAD_NAME);
        assertThat(preview.sliceKeyCount()).isEqualTo(10);
        assertThat(preview.emptyScanCalls()).isGreaterThan(20_000);
        assertThat(preview.fallbackChecks()).isGreaterThan(1_000);
    }

    @Test
    void shouldPreviewXtimerFallbackStormScenario() {
        XtimerFallbackStormCpuDemo.FallbackStormPreview preview = XtimerFallbackStormCpuDemo.previewScenario();

        assertThat(preview.hotThreadName()).isEqualTo(XtimerFallbackStormCpuDemo.HOT_THREAD_NAME);
        assertThat(preview.totalAttempts()).isEqualTo(9_000);
        assertThat(preview.hottestBucketAttempts()).isGreaterThan(2_500);
        assertThat(preview.bucketAttempts()).hasSize(3);
    }
}
