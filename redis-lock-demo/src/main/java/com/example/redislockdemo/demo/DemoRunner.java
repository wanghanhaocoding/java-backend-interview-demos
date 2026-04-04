package com.example.redislockdemo.demo;

import com.example.redislockdemo.api.RedissonApiDemoService;
import com.example.redislockdemo.concurrency.CacheConcurrencyDemoService;
import com.example.redislockdemo.concurrency.CounterConcurrencyDemoService;
import com.example.redislockdemo.concurrency.OrderedThreadExecutionDemoService;
import com.example.redislockdemo.concurrency.AsyncExceptionHandlingDemoService;
import com.example.redislockdemo.concurrency.SpringAsyncPoolDemoService;
import com.example.redislockdemo.concurrency.ThreadPoolTeachingDemoService;
import com.example.redislockdemo.failover.MasterReplicaFailoverDemoService;
import com.example.redislockdemo.nativeapi.NativeRedisLockService;
import com.example.redislockdemo.orderidempotency.OrderSubmitIdempotencyDemoService;
import com.example.redislockdemo.redisson.RedissonLockService;
import com.example.redislockdemo.support.ExecutionTracker;
import com.example.redislockdemo.watchdog.WatchdogDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final NativeRedisLockService nativeRedisLockService;
    private final RedissonLockService redissonLockService;
    private final WatchdogDemoService watchdogDemoService;
    private final RedissonApiDemoService redissonApiDemoService;
    private final CounterConcurrencyDemoService counterConcurrencyDemoService;
    private final CacheConcurrencyDemoService cacheConcurrencyDemoService;
    private final OrderedThreadExecutionDemoService orderedThreadExecutionDemoService;
    private final AsyncExceptionHandlingDemoService asyncExceptionHandlingDemoService;
    private final OrderSubmitIdempotencyDemoService orderSubmitIdempotencyDemoService;
    private final ThreadPoolTeachingDemoService threadPoolTeachingDemoService;
    private final SpringAsyncPoolDemoService springAsyncPoolDemoService;
    private final MasterReplicaFailoverDemoService masterReplicaFailoverDemoService;
    private final ExecutionTracker executionTracker;

    public DemoRunner(NativeRedisLockService nativeRedisLockService,
                      RedissonLockService redissonLockService,
                      WatchdogDemoService watchdogDemoService,
                      RedissonApiDemoService redissonApiDemoService,
                      CounterConcurrencyDemoService counterConcurrencyDemoService,
                      CacheConcurrencyDemoService cacheConcurrencyDemoService,
                      OrderedThreadExecutionDemoService orderedThreadExecutionDemoService,
                      AsyncExceptionHandlingDemoService asyncExceptionHandlingDemoService,
                      OrderSubmitIdempotencyDemoService orderSubmitIdempotencyDemoService,
                      ThreadPoolTeachingDemoService threadPoolTeachingDemoService,
                      SpringAsyncPoolDemoService springAsyncPoolDemoService,
                      MasterReplicaFailoverDemoService masterReplicaFailoverDemoService,
                      ExecutionTracker executionTracker) {
        this.nativeRedisLockService = nativeRedisLockService;
        this.redissonLockService = redissonLockService;
        this.watchdogDemoService = watchdogDemoService;
        this.redissonApiDemoService = redissonApiDemoService;
        this.counterConcurrencyDemoService = counterConcurrencyDemoService;
        this.cacheConcurrencyDemoService = cacheConcurrencyDemoService;
        this.orderedThreadExecutionDemoService = orderedThreadExecutionDemoService;
        this.asyncExceptionHandlingDemoService = asyncExceptionHandlingDemoService;
        this.orderSubmitIdempotencyDemoService = orderSubmitIdempotencyDemoService;
        this.threadPoolTeachingDemoService = threadPoolTeachingDemoService;
        this.springAsyncPoolDemoService = springAsyncPoolDemoService;
        this.masterReplicaFailoverDemoService = masterReplicaFailoverDemoService;
        this.executionTracker = executionTracker;
    }

    @Override
    public void run(String... args) throws Exception {
        executionTracker.reset();
        printTitle("1. Redis 原生锁：SET NX PX + 唯一 token");
        String token = nativeRedisLockService.tryAcquireOrderLock("A100", Duration.ofSeconds(10));
        System.out.println("拿锁成功 = " + (token != null));
        System.out.println("当前 token = " + token);
        System.out.println("释放结果 = " + nativeRedisLockService.releaseOrderLock("A100", token));

        printTitle("2. Redis 原生锁：只有持有者才能安全释放");
        String realToken = nativeRedisLockService.tryAcquireOrderLock("A101", Duration.ofSeconds(10));
        System.out.println("错误 token 释放 = " + nativeRedisLockService.releaseOrderLock("A101", "wrong-token"));
        System.out.println("正确 token 释放 = " + nativeRedisLockService.releaseOrderLock("A101", realToken));

        printTitle("3. Redisson tryLock：拿到锁才进入临界区");
        executionTracker.reset();
        System.out.println("执行次数 = " + redissonLockService.executeWithTryLock("A102", 0, 5, TimeUnit.SECONDS));

        printTitle("4. Redisson 可重入锁：同一线程重复进入不会把自己锁死");
        executionTracker.reset();
        System.out.println("重入后执行次数 = " + redissonLockService.reentrantDemo("A103"));

        printTitle("5. watchdog：不传 leaseTime 时自动续期");
        System.out.println("sleep 后剩余 TTL(ms) = " + watchdogDemoService.lockWithoutLeaseAndCheckTtl("job-1", 2000));

        printTitle("6. 固定租约：显式传 leaseTime 时不靠 watchdog");
        System.out.println("sleep 后剩余 TTL(ms) = " + watchdogDemoService.lockWithLeaseAndCheckTtl("job-2", 5, 2000));

        printTitle("7. RedissonClient / RLock 常见 API");
        Map<String, Object> apiResult = redissonApiDemoService.demonstrateApis("api-demo");
        apiResult.forEach((key, value) -> System.out.println(key + " = " + value));

        printTitle("8. JVM 并发计数器：count++ vs AtomicInteger vs LongAdder");
        int threadCount = 12;
        int incrementsPerThread = 100_000;
        System.out.println("线程数 = " + threadCount);
        System.out.println("每线程自增次数 = " + incrementsPerThread);
        printCounterResult(counterConcurrencyDemoService.unsafeCountPlusPlus(threadCount, incrementsPerThread));
        printCounterResult(counterConcurrencyDemoService.atomicInteger(threadCount, incrementsPerThread));
        printCounterResult(counterConcurrencyDemoService.longAdder(threadCount, incrementsPerThread));
        System.out.println("结论 = count++ 不是原子操作，并发下会丢失更新；AtomicInteger 和 LongAdder 能保证结果正确");

        printTitle("9. JVM 本地缓存初始化：check-then-put vs putIfAbsent vs computeIfAbsent");
        int cacheThreads = 12;
        System.out.println("并发请求线程数 = " + cacheThreads);
        printCacheResult(cacheConcurrencyDemoService.unsafeCheckThenPut("user:42", cacheThreads));
        printCacheResult(cacheConcurrencyDemoService.putIfAbsent("user:42", cacheThreads));
        printCacheResult(cacheConcurrencyDemoService.computeIfAbsent("user:42", cacheThreads));
        System.out.println("结论 = putIfAbsent 能避免覆盖，但可能仍然重复创建；computeIfAbsent 更适合“只初始化一次”");

        printTitle("10. 线程顺序控制：让 T1 -> T2 -> T3 按顺序执行");
        printOrderedResult(orderedThreadExecutionDemoService.joinChain());
        printOrderedResult(orderedThreadExecutionDemoService.countDownLatchChain());
        printOrderedResult(orderedThreadExecutionDemoService.semaphoreChain());
        printOrderedResult(orderedThreadExecutionDemoService.conditionChain());
        System.out.println("结论 = join 适合外层线程串行编排；CountDownLatch、Semaphore、Condition 更适合线程之间自己传递执行资格");

        printTitle("11. 订单防重复提交：先查再创建 vs 先抢占再创建");
        int submitThreads = 12;
        String requestNo = "REQ-20260329-001";
        System.out.println("同一个 requestNo 并发提交线程数 = " + submitThreads);
        printSubmitResult(orderSubmitIdempotencyDemoService.unsafeCheckThenCreate(requestNo, 10001L, submitThreads));
        printSubmitResult(orderSubmitIdempotencyDemoService.claimFirstWithRedis(requestNo, 10001L, submitThreads));
        System.out.println("结论 = 创建订单不能先查库再插入，要先用原子操作抢占 requestNo 的处理资格，再创建订单");

        printTitle("12. 线程池：项目里常见的线程池选择");
        threadPoolTeachingDemoService.commonPoolTypesOverview()
                .forEach(note -> System.out.println(note.poolName() + " -> useCase=" + note.useCase()
                        + ", creation=" + note.creationStyle()
                        + ", note=" + note.note()));

        printTitle("13. 线程池任务流转：core -> queue -> max -> reject");
        ThreadPoolTeachingDemoService.TaskFlowDemoResult taskFlow = threadPoolTeachingDemoService.taskFlowAndAbortPolicyDemo();
        taskFlow.submissionFlow().forEach(System.out::println);
        System.out.println("summary -> core=" + taskFlow.corePoolSize()
                + ", max=" + taskFlow.maxPoolSize()
                + ", queueCapacity=" + taskFlow.queueCapacity()
                + ", submitted=" + taskFlow.submittedTasks()
                + ", started=" + taskFlow.startedTasks()
                + ", largestPoolSize=" + taskFlow.largestPoolSize()
                + ", queuedPeak=" + taskFlow.queuedPeak()
                + ", rejected=" + taskFlow.rejectedTasks());
        System.out.println("结论 = 线程池会先用 core 线程，再进队列，再扩到 max，最后触发拒绝策略");

        printTitle("14. 拒绝策略：CallerRunsPolicy 如何回推压力");
        ThreadPoolTeachingDemoService.CallerRunsDemoResult callerRuns = threadPoolTeachingDemoService.callerRunsPolicyDemo();
        System.out.println("callerRunsCount = " + callerRuns.callerRunsCount());
        System.out.println("executionThreads = " + callerRuns.executionThreads());
        System.out.println("结论 = CallerRunsPolicy 不会丢任务，而是让提交方线程自己干活，起到限流和回压作用");

        printTitle("15. 线程池关闭：shutdown / awaitTermination / shutdownNow");
        ThreadPoolTeachingDemoService.ShutdownDemoResult shutdown = threadPoolTeachingDemoService.shutdownLifecycleDemo();
        System.out.println("shutdownCalled = " + shutdown.shutdownCalled());
        System.out.println("rejectedAfterShutdown = " + shutdown.rejectedAfterShutdown());
        System.out.println("terminatedGracefully = " + shutdown.terminatedGracefully());
        System.out.println("neverStartedTasks = " + shutdown.neverStartedTasks());
        System.out.println("interruptedTasks = " + shutdown.interruptedTasks());
        System.out.println("terminatedFinally = " + shutdown.terminatedFinally());
        System.out.println("结论 = 生产里优先 shutdown + awaitTermination，兜底才 shutdownNow");

        printTitle("16. Spring 版线程池：schedulerPool -> workerPool");
        SpringAsyncPoolDemoService.AsyncDispatchResult asyncResult = springAsyncPoolDemoService.demonstrateSchedulerToWorkerFlow(2, 2);
        asyncResult.slices().forEach(slice -> {
            System.out.println(slice.sliceNo() + " -> schedulerThread=" + slice.schedulerThread());
            slice.workerSteps().forEach(step -> System.out.println("  worker-" + step.workerNo() + " -> thread=" + step.threadName()));
        });
        System.out.println("结论 = 这类写法更接近项目里 @Async + ThreadPoolTaskExecutor 的分层调度方式");

        printTitle("17. Redis 主从切换：主从同步失败为什么会让锁失效");
        MasterReplicaFailoverDemoService.ReplicationGapDemoResult failover =
                masterReplicaFailoverDemoService.replicationLagCausesLockLoss("order-2001");
        failover.steps().forEach(System.out::println);
        System.out.println("lockKey = " + failover.lockKey());
        System.out.println("oldMaster = " + failover.oldMasterNode());
        System.out.println("promotedMaster = " + failover.promotedMasterNode());
        System.out.println("oldOwner = " + failover.oldOwner().ownerId() + ", token=" + failover.oldOwner().ownerToken());
        System.out.println("newOwner = " + failover.newOwner().ownerId() + ", token=" + failover.newOwner().ownerToken());
        System.out.println("mutualExclusionBroken = " + failover.mutualExclusionBroken());
        System.out.println(failover.conclusion());

        printTitle("18. 最终兜底：fencing token + 下游资源校验");
        MasterReplicaFailoverDemoService.FencingTokenDemoResult fencing =
                masterReplicaFailoverDemoService.fencingTokenProtectsDownstream("inventory-2001");
        fencing.steps().forEach(System.out::println);
        System.out.println("acceptedWrite = owner=" + fencing.acceptedWrite().ownerId()
                + ", token=" + fencing.acceptedWrite().fenceToken()
                + ", accepted=" + fencing.acceptedWrite().accepted());
        System.out.println("rejectedWrite = owner=" + fencing.rejectedWrite().ownerId()
                + ", token=" + fencing.rejectedWrite().fenceToken()
                + ", accepted=" + fencing.rejectedWrite().accepted()
                + ", reason=" + fencing.rejectedWrite().reason());
        System.out.println("latestAcceptedFenceToken = " + fencing.latestAcceptedFenceToken());
        fencing.guardrails().forEach(guardrail -> System.out.println("guardrail = " + guardrail));

        printTitle("19. 多个异步子线程：怎么隔离异常，不让单个子线程拖垮父流程");
        AsyncExceptionHandlingDemoService.ChildTaskIsolationDemoResult isolation =
                asyncExceptionHandlingDemoService.childTaskIsolationDemo();
        isolation.outcomes().forEach(outcome -> System.out.println(outcome.taskName()
                + " -> success=" + outcome.success()
                + ", thread=" + outcome.threadName()
                + ", result=" + outcome.result()
                + ", errorType=" + outcome.errorType()
                + ", errorMessage=" + outcome.errorMessage()));
        System.out.println("parentThread = " + isolation.parentThreadName());
        System.out.println("parentContinued = " + isolation.parentContinued());
        System.out.println("successCount = " + isolation.successCount());
        System.out.println("failureCount = " + isolation.failureCount());
        isolation.boundaryNotes().forEach(note -> System.out.println("boundary = " + note));

        printTitle("20. 子线程异常怎么拿：Future.get / UncaughtExceptionHandler");
        AsyncExceptionHandlingDemoService.FutureGetCaptureDemoResult futureCapture =
                asyncExceptionHandlingDemoService.futureGetCaptureDemo();
        System.out.println("Future.get -> captured=" + futureCapture.captured()
                + ", errorType=" + futureCapture.errorType()
                + ", errorMessage=" + futureCapture.errorMessage());
        AsyncExceptionHandlingDemoService.UncaughtExceptionHandlerDemoResult uncaughtCapture =
                asyncExceptionHandlingDemoService.uncaughtExceptionHandlerDemo();
        System.out.println("UncaughtExceptionHandler -> captured=" + uncaughtCapture.captured()
                + ", thread=" + uncaughtCapture.threadName()
                + ", errorType=" + uncaughtCapture.errorType()
                + ", errorMessage=" + uncaughtCapture.errorMessage());
        System.out.println("结论 = submit/CompletableFuture 要在父线程保留句柄并统一收敛；fire-and-forget 任务至少要配 UncaughtExceptionHandler");
    }

    private void printCounterResult(CounterConcurrencyDemoService.CounterDemoResult result) {
        System.out.println(result.strategy() + " -> expected=" + result.expected()
                + ", actual=" + result.actual()
                + ", lostUpdates=" + result.lostUpdates());
    }

    private void printCacheResult(CacheConcurrencyDemoService.CacheDemoResult result) {
        System.out.println(result.strategy() + " -> key=" + result.key()
                + ", loaderCalls=" + result.loaderCalls()
                + ", distinctCreatedValues=" + result.distinctCreatedValues()
                + ", finalValue=" + result.finalValue());
    }

    private void printSubmitResult(OrderSubmitIdempotencyDemoService.SubmitDemoResult result) {
        System.out.println(result.strategy() + " -> requestNo=" + result.requestNo()
                + ", ordersCreated=" + result.ordersCreated()
                + ", createdResponses=" + result.createdResponses()
                + ", replayedResponses=" + result.replayedResponses()
                + ", processingResponses=" + result.processingResponses()
                + ", observedOrderNos=" + result.observedOrderNos());
    }

    private void printOrderedResult(OrderedThreadExecutionDemoService.OrderedExecutionDemoResult result) {
        System.out.println(result.strategy() + " -> executionOrder=" + result.executionOrder()
                + ", ordered=" + result.ordered()
                + ", note=" + result.note());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
