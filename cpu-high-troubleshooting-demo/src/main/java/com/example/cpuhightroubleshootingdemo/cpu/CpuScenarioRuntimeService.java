package com.example.cpuhightroubleshootingdemo.cpu;

import com.example.cpuhightroubleshootingdemo.config.CpuHighDemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

@Service
public class CpuScenarioRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(CpuScenarioRuntimeService.class);

    private final CpuHighDemoProperties properties;

    private final CpuHighTroubleshootingDemoService.XtimerEmptyScanCaseResult emptyScanCase;

    private final CpuHighTroubleshootingDemoService.XtimerFallbackStormCaseResult fallbackStormCase;

    private final CpuHighTroubleshootingDemoService.DiagnosticPlaybook diagnosticPlaybook;

    private final Object lifecycleMonitor = new Object();

    private volatile ScenarioWorker activeWorker;

    private volatile ScenarioStatus lastStatus;

    public CpuScenarioRuntimeService(CpuHighDemoProperties properties,
                                     CpuHighTroubleshootingDemoService cpuHighTroubleshootingDemoService) {
        this.properties = properties;
        this.emptyScanCase = cpuHighTroubleshootingDemoService.xtimerEmptyScanCase();
        this.fallbackStormCase = cpuHighTroubleshootingDemoService.xtimerFallbackStormCase();
        this.diagnosticPlaybook = cpuHighTroubleshootingDemoService.diagnosisPlaybook();
        this.lastStatus = ScenarioStatus.idle(properties.getNodeId(), XtimerEmptyScanCpuDemo.resolvePid(), availableScenarios());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoStartConfiguredScenario() {
        String autoStart = properties.getScenario().getAutoStart();
        if (ScenarioType.NONE.matches(autoStart)) {
            return;
        }
        try {
            startScenario(autoStart, Integer.valueOf(properties.getScenario().getDurationSeconds()));
        } catch (IllegalArgumentException ex) {
            log.warn("[{}] ignore invalid auto-start scenario={}: {}", properties.getNodeId(), autoStart, ex.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        synchronized (lifecycleMonitor) {
            stopActiveWorkerLocked("application shutdown");
        }
    }

    public ScenarioStatus currentStatus() {
        synchronized (lifecycleMonitor) {
            refreshFinishedWorkerLocked();
            if (activeWorker != null) {
                lastStatus = activeWorker.snapshot();
            }
            return lastStatus;
        }
    }

    public ScenarioStatus startScenario(String scenarioValue, Integer durationSeconds) {
        synchronized (lifecycleMonitor) {
            ScenarioType scenarioType = ScenarioType.fromValue(scenarioValue);
            stopActiveWorkerLocked("replaced by " + scenarioType.value());

            int resolvedDurationSeconds = durationSeconds == null
                    ? properties.getScenario().getDurationSeconds()
                    : Math.max(0, durationSeconds.intValue());
            int logIntervalSeconds = Math.max(1, properties.getScenario().getLogIntervalSeconds());

            ScenarioWorker worker = createWorker(scenarioType, resolvedDurationSeconds, logIntervalSeconds);
            worker.start();
            activeWorker = worker;
            lastStatus = worker.snapshot();
            return lastStatus;
        }
    }

    public ScenarioStatus stopScenario() {
        synchronized (lifecycleMonitor) {
            if (activeWorker == null) {
                return lastStatus;
            }
            stopActiveWorkerLocked("manual stop");
            return lastStatus;
        }
    }

    public Map<String, Object> scenarioCatalog() {
        Map<String, Object> catalog = new LinkedHashMap<String, Object>();
        catalog.put("nodeId", properties.getNodeId());
        catalog.put("availableScenarios", availableScenarios());
        catalog.put("defaultLogIntervalSeconds", properties.getScenario().getLogIntervalSeconds());
        catalog.put("emptyScan", scenarioGuide(emptyScanCase.steps(), emptyScanCase.commands(), emptyScanCase.fixes(),
                emptyScanCase.signals(), XtimerEmptyScanCpuDemo.HOT_THREAD_NAME));
        catalog.put("fallbackStorm", scenarioGuide(fallbackStormCase.steps(), fallbackStormCase.commands(),
                fallbackStormCase.fixes(), fallbackStormCase.bucketAttempts(), XtimerFallbackStormCpuDemo.HOT_THREAD_NAME));
        return catalog;
    }

    public CpuHighTroubleshootingDemoService.DiagnosticPlaybook diagnosisPlaybook() {
        return diagnosticPlaybook;
    }

    private Map<String, Object> scenarioGuide(List<String> steps,
                                              List<String> commands,
                                              List<String> fixes,
                                              Object signals,
                                              String hotThreadName) {
        Map<String, Object> guide = new LinkedHashMap<String, Object>();
        guide.put("hotThreadName", hotThreadName);
        guide.put("steps", steps);
        guide.put("commands", commands);
        guide.put("fixes", fixes);
        guide.put("signals", signals);
        return guide;
    }

    private List<String> availableScenarios() {
        return Arrays.asList(ScenarioType.EMPTY_SCAN.value(), ScenarioType.FALLBACK_STORM.value());
    }

    private ScenarioWorker createWorker(ScenarioType scenarioType, int durationSeconds, int logIntervalSeconds) {
        switch (scenarioType) {
            case EMPTY_SCAN:
                return new EmptyScanWorker(durationSeconds, logIntervalSeconds);
            case FALLBACK_STORM:
                return new FallbackStormWorker(durationSeconds, logIntervalSeconds);
            default:
                throw new IllegalArgumentException("Unsupported scenario: " + scenarioType.value());
        }
    }

    private void refreshFinishedWorkerLocked() {
        if (activeWorker != null && !activeWorker.isRunning()) {
            lastStatus = activeWorker.snapshot();
            activeWorker = null;
        }
    }

    private void stopActiveWorkerLocked(String reason) {
        if (activeWorker == null) {
            return;
        }
        activeWorker.stop(reason);
        lastStatus = activeWorker.snapshot();
        activeWorker = null;
    }

    private List<String> scenarioCommands(ScenarioType type, long pid) {
        List<String> rawCommands = type == ScenarioType.EMPTY_SCAN
                ? emptyScanCase.commands()
                : fallbackStormCase.commands();
        List<String> commands = new ArrayList<String>(rawCommands.size());
        for (String rawCommand : rawCommands) {
            commands.add(rawCommand.replace("<pid>", String.valueOf(pid)));
        }
        return commands;
    }

    private List<String> scenarioFixes(ScenarioType type) {
        return type == ScenarioType.EMPTY_SCAN ? emptyScanCase.fixes() : fallbackStormCase.fixes();
    }

    private abstract class ScenarioWorker {

        private final ScenarioType scenarioType;

        private final int durationSeconds;

        private final int logIntervalSeconds;

        private final AtomicBoolean running = new AtomicBoolean(false);

        private volatile Thread workerThread;

        private volatile LocalDateTime startedAt;

        private volatile LocalDateTime stoppedAt;

        private volatile String stopReason = "idle";

        private volatile String failure;

        private volatile long lastLogEpochMillis;

        ScenarioWorker(ScenarioType scenarioType, int durationSeconds, int logIntervalSeconds) {
            this.scenarioType = scenarioType;
            this.durationSeconds = durationSeconds;
            this.logIntervalSeconds = logIntervalSeconds;
        }

        void start() {
            startedAt = LocalDateTime.now();
            stopReason = durationSeconds > 0 ? "duration=" + durationSeconds + " seconds" : "manual stop required";
            running.set(true);
            workerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    runInternal();
                }
            }, hotThreadName());
            workerThread.setDaemon(true);
            workerThread.start();
            log.info("[{}] started scenario={} pid={} hotThread={} durationSeconds={}",
                    properties.getNodeId(), scenarioType.value(), pid(), hotThreadName(), durationSeconds);
        }

        private void runInternal() {
            lastLogEpochMillis = System.currentTimeMillis();
            try {
                while (running.get()) {
                    workIteration();
                    maybeLogProgress();
                    if (durationSeconds > 0 && elapsedSeconds() >= durationSeconds) {
                        requestStop("duration elapsed");
                    }
                }
            } catch (Throwable ex) {
                failure = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                stopReason = "failed";
                log.error("[{}] scenario={} failed", properties.getNodeId(), scenarioType.value(), ex);
            } finally {
                running.set(false);
                stoppedAt = LocalDateTime.now();
                onStopped();
                log.info("[{}] stopped scenario={} reason={} metrics={}",
                        properties.getNodeId(), scenarioType.value(), stopReason, metricsSnapshot());
            }
        }

        void stop(String reason) {
            requestStop(reason);
            Thread thread = workerThread;
            if (thread != null && thread != Thread.currentThread()) {
                try {
                    thread.join(2000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        boolean isRunning() {
            return running.get();
        }

        long pid() {
            return XtimerEmptyScanCpuDemo.resolvePid();
        }

        ScenarioStatus snapshot() {
            String threadState = workerThread == null ? "NOT_STARTED" : workerThread.getState().name();
            return new ScenarioStatus(
                    properties.getNodeId(),
                    pid(),
                    scenarioType.value(),
                    running.get(),
                    durationSeconds,
                    elapsedSeconds(),
                    hotThreadName(),
                    threadState,
                    startedAt == null ? null : startedAt.toString(),
                    stoppedAt == null ? null : stoppedAt.toString(),
                    stopReason,
                    failure,
                    metricsSnapshot(),
                    scenarioCommands(scenarioType, pid()),
                    scenarioFixes(scenarioType),
                    diagnosticPlaybook.evidenceChecklist(),
                    availableScenarios()
            );
        }

        long elapsedSeconds() {
            if (startedAt == null) {
                return 0L;
            }
            LocalDateTime end = stoppedAt == null ? LocalDateTime.now() : stoppedAt;
            return Math.max(0L, Duration.between(startedAt, end).getSeconds());
        }

        void requestStop(String reason) {
            if (running.getAndSet(false)) {
                stopReason = reason;
            }
        }

        private void maybeLogProgress() {
            long now = System.currentTimeMillis();
            if (now - lastLogEpochMillis < logIntervalSeconds * 1000L) {
                return;
            }
            lastLogEpochMillis = now;
            log.info("[{}] scenario={} elapsedSeconds={} metrics={}",
                    properties.getNodeId(), scenarioType.value(), elapsedSeconds(), metricsSnapshot());
        }

        abstract String hotThreadName();

        abstract void workIteration();

        abstract Map<String, Object> metricsSnapshot();

        void onStopped() {
        }
    }

    private final class EmptyScanWorker extends ScenarioWorker {

        private final List<String> minuteBucketKeys = XtimerEmptyScanCpuDemo.buildMinuteBucketKeys();

        private final LongAdder emptyScans = new LongAdder();

        private final LongAdder timerWakeUps = new LongAdder();

        private final LongAdder fallbackChecks = new LongAdder();

        private volatile long checksum;

        private volatile int round;

        EmptyScanWorker(int durationSeconds, int logIntervalSeconds) {
            super(ScenarioType.EMPTY_SCAN, durationSeconds, logIntervalSeconds);
        }

        @Override
        String hotThreadName() {
            return XtimerEmptyScanCpuDemo.HOT_THREAD_NAME;
        }

        @Override
        void workIteration() {
            for (String minuteBucketKey : minuteBucketKeys) {
                timerWakeUps.increment();
                emptyScans.increment();
                checksum += XtimerEmptyScanCpuDemo.scanMinuteBucket(minuteBucketKey, round);
                if ((emptyScans.sum() & 7L) == 0L) {
                    fallbackChecks.increment();
                    checksum ^= XtimerEmptyScanCpuDemo.simulateFallbackGuard(minuteBucketKey, round);
                }
            }
            round++;
        }

        @Override
        Map<String, Object> metricsSnapshot() {
            Map<String, Object> metrics = new LinkedHashMap<String, Object>();
            metrics.put("emptyScans", emptyScans.sum());
            metrics.put("timerWakeUps", timerWakeUps.sum());
            metrics.put("fallbackChecks", fallbackChecks.sum());
            metrics.put("round", round);
            metrics.put("sliceKeyCount", minuteBucketKeys.size());
            metrics.put("checksum", checksum);
            return metrics;
        }
    }

    private final class FallbackStormWorker extends ScenarioWorker {

        private final ArrayDeque<XtimerFallbackStormCpuDemo.MinuteBucketFallbackTask> queue =
                XtimerFallbackStormCpuDemo.seedQueue();

        private final LongAdder attempts = new LongAdder();

        private final Map<String, LongAdder> bucketAttempts = new LinkedHashMap<String, LongAdder>();

        private volatile long checksum;

        FallbackStormWorker(int durationSeconds, int logIntervalSeconds) {
            super(ScenarioType.FALLBACK_STORM, durationSeconds, logIntervalSeconds);
            for (XtimerFallbackStormCpuDemo.MinuteBucketFallbackTask task : XtimerFallbackStormCpuDemo.seedQueue()) {
                bucketAttempts.put(task.minuteBucketKey(), new LongAdder());
            }
        }

        @Override
        String hotThreadName() {
            return XtimerFallbackStormCpuDemo.HOT_THREAD_NAME;
        }

        @Override
        void workIteration() {
            XtimerFallbackStormCpuDemo.MinuteBucketFallbackTask task = queue.removeFirst();
            attempts.increment();
            bucketAttempts.get(task.minuteBucketKey()).increment();
            checksum += XtimerFallbackStormCpuDemo.simulateFallbackQuery(task);
            queue.addLast(task.nextRound());
        }

        @Override
        Map<String, Object> metricsSnapshot() {
            Map<String, Object> metrics = new LinkedHashMap<String, Object>();
            metrics.put("attempts", attempts.sum());
            metrics.put("hottestBucket", XtimerFallbackStormCpuDemo.hottestBucket(bucketAttempts));
            metrics.put("bucketAttempts", bucketAttemptsSnapshot());
            metrics.put("queueSize", queue.size());
            metrics.put("checksum", checksum);
            return metrics;
        }

        private Map<String, Long> bucketAttemptsSnapshot() {
            Map<String, Long> snapshot = new LinkedHashMap<String, Long>();
            for (Map.Entry<String, LongAdder> entry : bucketAttempts.entrySet()) {
                snapshot.put(entry.getKey(), Long.valueOf(entry.getValue().sum()));
            }
            return snapshot;
        }
    }

    private enum ScenarioType {
        NONE("none"),
        EMPTY_SCAN("empty-scan"),
        FALLBACK_STORM("fallback-storm");

        private final String value;

        ScenarioType(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }

        boolean matches(String rawValue) {
            return value.equalsIgnoreCase(rawValue);
        }

        static ScenarioType fromValue(String rawValue) {
            for (ScenarioType type : values()) {
                if (type.matches(rawValue)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown scenario: " + rawValue
                    + ". Supported values: empty-scan, fallback-storm.");
        }
    }

    public static final class ScenarioStatus {

        private final String nodeId;

        private final long pid;

        private final String activeScenario;

        private final boolean running;

        private final int durationSeconds;

        private final long elapsedSeconds;

        private final String hotThreadName;

        private final String hotThreadState;

        private final String startedAt;

        private final String stoppedAt;

        private final String stopReason;

        private final String failure;

        private final Map<String, Object> metrics;

        private final List<String> commands;

        private final List<String> fixes;

        private final List<String> evidenceChecklist;

        private final List<String> availableScenarios;

        ScenarioStatus(String nodeId,
                       long pid,
                       String activeScenario,
                       boolean running,
                       int durationSeconds,
                       long elapsedSeconds,
                       String hotThreadName,
                       String hotThreadState,
                       String startedAt,
                       String stoppedAt,
                       String stopReason,
                       String failure,
                       Map<String, Object> metrics,
                       List<String> commands,
                       List<String> fixes,
                       List<String> evidenceChecklist,
                       List<String> availableScenarios) {
            this.nodeId = nodeId;
            this.pid = pid;
            this.activeScenario = activeScenario;
            this.running = running;
            this.durationSeconds = durationSeconds;
            this.elapsedSeconds = elapsedSeconds;
            this.hotThreadName = hotThreadName;
            this.hotThreadState = hotThreadState;
            this.startedAt = startedAt;
            this.stoppedAt = stoppedAt;
            this.stopReason = stopReason;
            this.failure = failure;
            this.metrics = metrics;
            this.commands = commands;
            this.fixes = fixes;
            this.evidenceChecklist = evidenceChecklist;
            this.availableScenarios = availableScenarios;
        }

        static ScenarioStatus idle(String nodeId, long pid, List<String> availableScenarios) {
            return new ScenarioStatus(
                    nodeId,
                    pid,
                    "idle",
                    false,
                    0,
                    0,
                    "n/a",
                    "NOT_RUNNING",
                    null,
                    null,
                    "no active scenario",
                    null,
                    Collections.<String, Object>emptyMap(),
                    Collections.<String>emptyList(),
                    Collections.<String>emptyList(),
                    Collections.<String>emptyList(),
                    availableScenarios
            );
        }

        public String getNodeId() {
            return nodeId;
        }

        public long getPid() {
            return pid;
        }

        public String getActiveScenario() {
            return activeScenario;
        }

        public boolean isRunning() {
            return running;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public long getElapsedSeconds() {
            return elapsedSeconds;
        }

        public String getHotThreadName() {
            return hotThreadName;
        }

        public String getHotThreadState() {
            return hotThreadState;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public String getStoppedAt() {
            return stoppedAt;
        }

        public String getStopReason() {
            return stopReason;
        }

        public String getFailure() {
            return failure;
        }

        public Map<String, Object> getMetrics() {
            return metrics;
        }

        public List<String> getCommands() {
            return commands;
        }

        public List<String> getFixes() {
            return fixes;
        }

        public List<String> getEvidenceChecklist() {
            return evidenceChecklist;
        }

        public List<String> getAvailableScenarios() {
            return availableScenarios;
        }
    }
}
