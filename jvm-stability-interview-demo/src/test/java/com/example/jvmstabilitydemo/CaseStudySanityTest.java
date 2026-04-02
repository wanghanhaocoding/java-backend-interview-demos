package com.example.jvmstabilitydemo;

import com.example.jvmstabilitydemo.deadlock.DeadlockDemo;
import com.example.jvmstabilitydemo.fullgc.FullGcPressureDemo;
import com.example.jvmstabilitydemo.oom.OomLeakDemo;
import com.example.jvmstabilitydemo.support.CaseStoryLibrary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseStudySanityTest {

    @Test
    void shouldProvideCaseSummaries() {
        assertTrue(CaseStoryLibrary.oomSummary().contains("ScheduleCenter"));
        assertTrue(CaseStoryLibrary.fullGcSummary().contains("ScheduleCenter"));
        assertTrue(CaseStoryLibrary.deadlockSummary().contains("死锁"));
        assertTrue(CaseStoryLibrary.threadTroubleshootingSummary().contains("线程 dump"));
    }

    @Test
    void shouldPreviewOomRound() {
        assertNotNull(OomLeakDemo.previewRound());
    }

    @Test
    void shouldPreloadQueueForFullGcDemo() {
        FullGcPressureDemo.preloadWindow(1, 10);
        assertTrue(FullGcPressureDemo.queueSize() >= 10);
    }

    @Test
    void shouldDetectDeadlock() throws Exception {
        DeadlockDemo demo = new DeadlockDemo();
        demo.runDemo();
        long[] threadIds = demo.detectDeadlockedThreads();
        assertNotNull(threadIds);
        assertTrue(threadIds.length >= 2);
    }
}
