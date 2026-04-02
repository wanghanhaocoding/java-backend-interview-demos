package com.example.jvmstabilitydemo.thread;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadTroubleshootingDemoTest {

    @Test
    void previewSnapshotShouldExposeProblemThreadsAndStates() throws Exception {
        ThreadTroubleshootingDemo demo = new ThreadTroubleshootingDemo();

        ThreadTroubleshootingDemo.ThreadTroubleshootingSnapshot snapshot = demo.previewSnapshot();

        assertThat(snapshot.processId()).isPositive();
        assertThat(snapshot.threads())
                .extracting(ThreadTroubleshootingDemo.ThreadSignal::threadName)
                .contains(
                        "receipt-callback-busy-thread",
                        "receipt-lock-holder-thread",
                        "receipt-lock-blocked-thread",
                        "callback-queue-waiting-thread",
                        "schedule-poller-sleeping-thread"
                );

        assertThat(findSignal(snapshot.threads(), "receipt-lock-blocked-thread").state()).isEqualTo(Thread.State.BLOCKED);
        assertThat(findSignal(snapshot.threads(), "callback-queue-waiting-thread").state())
                .isIn(Thread.State.WAITING, Thread.State.TIMED_WAITING);
        assertThat(findSignal(snapshot.threads(), "schedule-poller-sleeping-thread").state()).isEqualTo(Thread.State.TIMED_WAITING);
        assertThat(findSignal(snapshot.threads(), "receipt-callback-busy-thread").state()).isEqualTo(Thread.State.RUNNABLE);
    }

    private ThreadTroubleshootingDemo.ThreadSignal findSignal(List<ThreadTroubleshootingDemo.ThreadSignal> signals,
                                                              String threadName) {
        return signals.stream()
                .filter(signal -> signal.threadName().equals(threadName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("thread not found: " + threadName));
    }
}
