package com.example.cpuhightroubleshootingdemo.cpu;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CpuHighTroubleshootingDemoService {

    public BusySpinCaseResult scheduleCenterBusySpinCase() {
        BusySpinScheduleScannerDemo.ScanPreview preview = BusySpinScheduleScannerDemo.previewScenario();

        List<String> steps = List.of(
                "1. ScheduleCenter 的 scanner 线程为了抢秒级精度，空队列时仍然持续空扫未来 bucket。",
                "2. 预览里 " + preview.bucketCount() + " 个 bucket 被反复空扫 "
                        + preview.emptyScanIterations() + " 次，并且做了 " + preview.payloadChecks() + " 次附带 payload 检查。",
                "3. 业务线程没有真正忙起来，但 " + preview.hotThreadName() + " 会长期黏在 CPU 上。",
                "4. 用 jstack 看这类线程，通常会落在 scanLoop / pollBucket / decodePayload 这一类方法上。"
        );

        Map<String, String> signals = new LinkedHashMap<>();
        signals.put("hotThread", preview.hotThreadName());
        signals.put("symptom", "scanner 线程接近满核，workerPool 活跃线程不高，业务吞吐没有同步上涨");
        signals.put("metric", "emptyScanIterations=" + preview.emptyScanIterations() + ", elapsedMicros=" + preview.elapsedMicros());
        signals.put("risk", "空转扫描会放大 CPU 消耗，还会带出无效日志、无效 Redis/MySQL 查询");

        List<String> commands = List.of(
                "top -Hp <pid>",
                "printf '%x\\n' <tid>",
                "jstack <pid> | grep -A 20 <nid>",
                "async-profiler -e cpu -d 15 -f cpu.html <pid>"
        );

        List<String> fixes = List.of(
                "本地队列为空时用 sleep / parkNanos / condition wait 做短暂退避，不要 busy spin",
                "按下一次最早触发时间阻塞等待，而不是空扫整个未来时间窗",
                "把 scannerPool 和 workerPool 隔离，并给 empty-scan 次数补指标",
                "对 bucket 预取、payload 解析和日志采样做上限控制"
        );

        return new BusySpinCaseResult(steps, signals, commands, fixes);
    }

    public RetryStormCaseResult asyncJobRetryStormCase() {
        AsyncRetryStormCpuDemo.RetryStormPreview preview = AsyncRetryStormCpuDemo.previewScenario();

        List<String> steps = List.of(
                "1. AsyncJobCenter 的 callback worker 遇到银行超时后，没有回推 order_time，而是立刻重试。",
                "2. 预览里总共发生了 " + preview.totalAttempts() + " 次重试尝试，少量 jobId 被重复消费。",
                "3. 热点线程通常会落在 retry dispatcher、callback fallback、序列化重建上下文这一类方法上。",
                "4. 这类问题的危险点不只是 CPU 高，还会把日志、MQ、DB 和下游接口一起放大。"
        );

        List<String> commands = List.of(
                "top -Hp <pid>",
                "jstack <pid> | grep -A 20 retry",
                "async-profiler -e cpu -d 15 -f retry-storm.html <pid>",
                "grep 'retry jobId' application.log | sort | uniq -c | sort -nr | head"
        );

        List<String> fixes = List.of(
                "失败后回推 order_time，用指数退避替代立刻重试",
                "限制单 job 的单位时间重试次数，超阈值后转人工或延后补偿",
                "把重试调度和真正回调执行拆线程池，并对重试队列做背压",
                "对同一 jobId 的重复失败补结构化日志和告警"
        );

        return new RetryStormCaseResult(steps, preview.jobAttempts(), commands, fixes);
    }

    public DiagnosticPlaybook diagnosisPlaybook() {
        List<String> steps = List.of(
                "1. 先看机器或容器层：CPU 是单核打满还是多核同时打满，确认是 Java 进程本身在高。",
                "2. 用 top -Hp <pid> 找到热点线程 tid，确认是 scanner、retry dispatcher 还是业务线程。",
                "3. 把 tid 转成 16 进制 nid，再用 jstack 或 jcmd Thread.print 把热点线程映射到 Java 栈。",
                "4. 如果栈还不够清楚，用 async-profiler 抓 15 到 30 秒 CPU 火焰图，确认热点方法和调用链。",
                "5. 把热点线程、日志、QPS、失败率、线程池队列、重试次数放在一起看，区分现象和根因。",
                "6. 先做止血，再做治理。止血通常是限流、摘流量、降批次、扩容；治理才是改循环、改重试、改线程池模型。"
        );

        List<String> evidenceChecklist = List.of(
                "热点线程名和线程状态",
                "热点线程对应的 Java 栈",
                "火焰图里最宽的方法栈",
                "线程池 active / queue / reject 指标",
                "同一 jobId 或同一 bucket 的重复命中证据"
        );

        return new DiagnosticPlaybook(steps, evidenceChecklist);
    }

    public record BusySpinCaseResult(
            List<String> steps,
            Map<String, String> signals,
            List<String> commands,
            List<String> fixes
    ) {
    }

    public record RetryStormCaseResult(
            List<String> steps,
            Map<String, Integer> jobAttempts,
            List<String> commands,
            List<String> fixes
    ) {
    }

    public record DiagnosticPlaybook(
            List<String> steps,
            List<String> evidenceChecklist
    ) {
    }
}
