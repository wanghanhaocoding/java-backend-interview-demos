package com.example.mysqllockmvccdemo.demo;

import com.example.mysqllockmvccdemo.isolation.IsolationLevelDemoService;
import com.example.mysqllockmvccdemo.lock.LockingDemoService;
import com.example.mysqllockmvccdemo.mvcc.MvccDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final IsolationLevelDemoService isolationLevelDemoService;
    private final MvccDemoService mvccDemoService;
    private final LockingDemoService lockingDemoService;

    public DemoRunner(IsolationLevelDemoService isolationLevelDemoService,
                      MvccDemoService mvccDemoService,
                      LockingDemoService lockingDemoService) {
        this.isolationLevelDemoService = isolationLevelDemoService;
        this.mvccDemoService = mvccDemoService;
        this.lockingDemoService = lockingDemoService;
    }

    @Override
    public void run(String... args) {
        printTitle("1. 隔离级别：RC vs RR");
        IsolationLevelDemoService.NonRepeatableReadResult isolationResult =
                isolationLevelDemoService.nonRepeatableReadDemo();
        isolationResult.steps().forEach(System.out::println);

        printTitle("2. MVCC：版本链与 read view");
        MvccDemoService.MvccSnapshotResult mvccResult = mvccDemoService.versionChainDemo();
        mvccResult.steps().forEach(System.out::println);

        printTitle("3. 锁：gap lock 与死锁");
        LockingDemoService.GapLockResult gapLockResult = lockingDemoService.gapLockDemo();
        gapLockResult.steps().forEach(System.out::println);

        LockingDemoService.DeadlockResult deadlockResult = lockingDemoService.deadlockDemo();
        deadlockResult.steps().forEach(System.out::println);
        System.out.println("deadlockDetected = " + deadlockResult.deadlockDetected());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
