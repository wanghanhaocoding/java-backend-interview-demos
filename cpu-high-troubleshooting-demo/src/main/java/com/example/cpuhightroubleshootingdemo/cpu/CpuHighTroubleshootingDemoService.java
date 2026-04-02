package com.example.cpuhightroubleshootingdemo.cpu;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CpuHighTroubleshootingDemoService {

    public XtimerEmptyScanCaseResult xtimerEmptyScanCase() {
        XtimerEmptyScanCpuDemo.ScanPreview preview = XtimerEmptyScanCpuDemo.previewScenario();

        List<String> steps = Arrays.asList(
                "1. 在 bitstorm-svr-xtimer 里，SchedulerWorker 每秒都会为 5 个 bucket 提交“上一分钟补偿 + 当前分钟实时”两轮分片，抢到锁后的 TriggerWorker 会启动 Timer 持续扫 minuteBucketKey。",
                "2. 如果很多 minuteBucketKey 实际上没有到期任务，但 TriggerTimerTask 仍然每秒执行 rangeByScore 和空结果判断，就会形成高频空扫；预览里 " + preview.sliceKeyCount()
                        + " 个 minuteBucketKey 被空扫了 " + preview.emptyScanCalls() + " 次。",
                "3. 这种热点线程通常不是业务 callback 线程，而是 TriggerWorker 内部的 Timer 线程；预览里热点线程名就是 " + preview.hotThreadName() + "。",
                "4. 如果空扫路径还顺带做 fallback guard、日志拼装或 task key 解析，CPU 会先被空转打高，真正执行量却上不来。"
        );

        Map<String, String> signals = new LinkedHashMap<String, String>();
        signals.put("hotThread", preview.hotThreadName());
        signals.put("symptom", "Timer 线程接近满核，但真实 callback 量没有同步上涨，triggerPool 也未必打满");
        signals.put("metric", "emptyScanCalls=" + preview.emptyScanCalls() + ", timerTaskWakeUps=" + preview.timerTaskWakeUps()
                + ", fallbackChecks=" + preview.fallbackChecks());
        signals.put("risk", "空 minuteBucketKey 扫描会放大 Redis 查询、空日志和 fallback guard，CPU 先被 Timer 线程拖高");

        List<String> commands = Arrays.asList(
                "top -Hp <pid>",
                "printf '%x\\n' <tid>",
                "jstack <pid> | grep -A 20 <nid>",
                "async-profiler -e cpu -d 15 -f cpu.html <pid>"
        );

        List<String> fixes = Arrays.asList(
                "减少对空 minuteBucketKey 的无效扫描，按下一次最早触发点或本地水位做等待",
                "把 TriggerWorker 的长生命周期扫描拆短，避免单个 Timer 线程长期黏在 CPU 上",
                "对 empty scan、rangeByScore miss、fallback guard 次数补指标和告警",
                "让 schedulerPool 和 triggerPool 的饱和状态反向影响抢锁和扫描，而不是继续空转"
        );

        return new XtimerEmptyScanCaseResult(steps, signals, commands, fixes);
    }

    public XtimerFallbackStormCaseResult xtimerFallbackStormCase() {
        XtimerFallbackStormCpuDemo.FallbackStormPreview preview = XtimerFallbackStormCpuDemo.previewScenario();

        List<String> steps = Arrays.asList(
                "1. 在 xtimer 里，TriggerTimerTask 先从 Redis rangeByScore 拉 minuteBucketKey 对应的任务；如果 Redis 抖动或抛异常，就会走 taskMapper.getTasksByTimeRange 做 DB fallback。",
                "2. 如果问题 minuteBucketKey 一直存在，而异常路径没有退避或限流，就会在少量 minuteBucketKey 上反复执行 fallback 查询和回调上下文重建；预览里总共发生了 "
                        + preview.totalAttempts() + " 次 fallback 尝试。",
                "3. 热点线程通常会落在 fallback 查询构造、TaskModel 结果处理、notify_http_param 反序列化和回调 body 组装这一类方法上。",
                "4. 这类问题的危险点不只是 CPU 高，还会把 DB、日志和下游 callback 一起放大。"
        );

        List<String> commands = Arrays.asList(
                "top -Hp <pid>",
                "jstack <pid> | grep -A 20 getTasksByTimeRange",
                "async-profiler -e cpu -d 15 -f xtimer-fallback-storm.html <pid>",
                "grep 'minuteBucketKey' application.log | sort | uniq -c | sort -nr | head"
        );

        List<String> fixes = Arrays.asList(
                "给 taskMapper.getTasksByTimeRange 的 fallback 路径加限频和退避，不允许 minuteBucketKey 连续秒级重试",
                "限制单个 minuteBucketKey 的 DB fallback 结果集规模，避免异常路径把对象做大",
                "把 fallback 查询和真正 callback 执行拆线程池，并让 triggerPool 水位反向约束 fallback 调度",
                "对同一 minuteBucketKey 的连续 fallback 次数、DB RT 和 callback RT 补监控"
        );

        return new XtimerFallbackStormCaseResult(steps, preview.bucketAttempts(), commands, fixes);
    }

    public DiagnosticPlaybook diagnosisPlaybook() {
        List<String> steps = Arrays.asList(
                "1. 先看机器或容器层：确认是不是 xtimer 这个 Java 进程本身在高，是单核高还是多核同时高。",
                "2. 用 top -Hp <pid> 找到最热线程，优先区分是 schedulerPool、TriggerWorker 的 Timer 线程，还是 triggerPool callback 线程。",
                "3. 把热点 tid 转成 16 进制 nid，再用 jstack 或 jcmd Thread.print 把热点线程映射到 TriggerWorker、TriggerTimerTask、TaskCache 或 TaskMapper 这一层方法。",
                "4. 如果栈还不够清楚，用 async-profiler 抓 15 到 30 秒 CPU 火焰图，确认热点是在 empty scan、DB fallback、回调构造还是锁竞争上。",
                "5. 把热点线程、日志、queueSize、rangeByScore RT、taskMapper.getTasksByTimeRange RT 和 callback RT 放在一起看，区分现象和根因。",
                "6. 先做止血，再做治理。止血是限流、降批次、摘异常 minuteBucketKey、扩容；治理才是改扫描模型、退避策略、队列上限和 fallback 限制。"
        );

        List<String> evidenceChecklist = Arrays.asList(
                "热点线程名和线程状态",
                "热点线程对应的 Java 栈",
                "火焰图里最宽的方法栈",
                "schedulerPool / triggerPool 的 active、queue、reject 指标",
                "同一 minuteBucketKey 的重复命中或 fallback 证据"
        );

        return new DiagnosticPlaybook(steps, evidenceChecklist);
    }

    public static final class XtimerEmptyScanCaseResult {
        private final List<String> steps;
        private final Map<String, String> signals;
        private final List<String> commands;
        private final List<String> fixes;

        public XtimerEmptyScanCaseResult(List<String> steps, Map<String, String> signals, List<String> commands, List<String> fixes) {
            this.steps = steps;
            this.signals = signals;
            this.commands = commands;
            this.fixes = fixes;
        }

        public List<String> steps() {
            return steps;
        }

        public Map<String, String> signals() {
            return signals;
        }

        public List<String> commands() {
            return commands;
        }

        public List<String> fixes() {
            return fixes;
        }
    }

    public static final class XtimerFallbackStormCaseResult {
        private final List<String> steps;
        private final Map<String, Integer> bucketAttempts;
        private final List<String> commands;
        private final List<String> fixes;

        public XtimerFallbackStormCaseResult(List<String> steps, Map<String, Integer> bucketAttempts, List<String> commands, List<String> fixes) {
            this.steps = steps;
            this.bucketAttempts = bucketAttempts;
            this.commands = commands;
            this.fixes = fixes;
        }

        public List<String> steps() {
            return steps;
        }

        public Map<String, Integer> bucketAttempts() {
            return bucketAttempts;
        }

        public List<String> commands() {
            return commands;
        }

        public List<String> fixes() {
            return fixes;
        }
    }

    public static final class DiagnosticPlaybook {
        private final List<String> steps;
        private final List<String> evidenceChecklist;

        public DiagnosticPlaybook(List<String> steps, List<String> evidenceChecklist) {
            this.steps = steps;
            this.evidenceChecklist = evidenceChecklist;
        }

        public List<String> steps() {
            return steps;
        }

        public List<String> evidenceChecklist() {
            return evidenceChecklist;
        }
    }
}
