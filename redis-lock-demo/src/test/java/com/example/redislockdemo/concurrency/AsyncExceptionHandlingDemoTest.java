package com.example.redislockdemo.concurrency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncExceptionHandlingDemoTest {

    private final AsyncExceptionHandlingDemoService asyncExceptionHandlingDemoService = new AsyncExceptionHandlingDemoService();

    @Test
    void childTaskIsolationKeepsParentFlowAlive() {
        AsyncExceptionHandlingDemoService.ChildTaskIsolationDemoResult result =
                asyncExceptionHandlingDemoService.childTaskIsolationDemo();

        assertThat(result.parentContinued()).isTrue();
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.outcomes())
                .extracting(AsyncExceptionHandlingDemoService.ChildTaskOutcome::taskName)
                .containsExactly("load-ledger-snapshot", "push-bank-callback", "write-audit-log");
        assertThat(result.outcomes())
                .filteredOn(outcome -> !outcome.success())
                .singleElement()
                .satisfies(outcome -> assertThat(outcome.errorMessage()).contains("push-bank-callback failed"));
    }

    @Test
    void futureGetCanCaptureChildThreadException() {
        AsyncExceptionHandlingDemoService.FutureGetCaptureDemoResult result =
                asyncExceptionHandlingDemoService.futureGetCaptureDemo();

        assertThat(result.captured()).isTrue();
        assertThat(result.captureApi()).isEqualTo("Future.get");
        assertThat(result.errorType()).isEqualTo("IllegalStateException");
        assertThat(result.errorMessage()).contains("bank callback worker crashed");
    }

    @Test
    void uncaughtExceptionHandlerCapturesFireAndForgetCrash() throws Exception {
        AsyncExceptionHandlingDemoService.UncaughtExceptionHandlerDemoResult result =
                asyncExceptionHandlingDemoService.uncaughtExceptionHandlerDemo();

        assertThat(result.captured()).isTrue();
        assertThat(result.threadName()).isEqualTo("raw-child-1");
        assertThat(result.errorType()).isEqualTo("IllegalArgumentException");
        assertThat(result.errorMessage()).contains("fire-and-forget child crashed");
    }
}
