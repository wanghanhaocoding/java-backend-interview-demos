package com.example.redislockdemo.concurrency;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class SpringAsyncWorkerDemoService {

    @Async("workerPool")
    public CompletableFuture<SpringAsyncPoolDemoService.AsyncStep> executeWorkerTask(String sliceNo, int workerNo) throws InterruptedException {
        String threadName = Thread.currentThread().getName();
        Thread.sleep(80);
        return CompletableFuture.completedFuture(
                new SpringAsyncPoolDemoService.AsyncStep("worker", sliceNo, workerNo, threadName)
        );
    }
}
