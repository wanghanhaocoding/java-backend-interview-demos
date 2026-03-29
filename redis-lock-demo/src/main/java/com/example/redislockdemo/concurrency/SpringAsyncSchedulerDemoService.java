package com.example.redislockdemo.concurrency;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class SpringAsyncSchedulerDemoService {

    private final SpringAsyncWorkerDemoService springAsyncWorkerDemoService;

    public SpringAsyncSchedulerDemoService(SpringAsyncWorkerDemoService springAsyncWorkerDemoService) {
        this.springAsyncWorkerDemoService = springAsyncWorkerDemoService;
    }

    @Async("schedulerPool")
    public CompletableFuture<SpringAsyncPoolDemoService.SliceDispatchResult> dispatchSlice(String sliceNo, int workersPerSlice) throws InterruptedException {
        String schedulerThread = Thread.currentThread().getName();
        List<CompletableFuture<SpringAsyncPoolDemoService.AsyncStep>> workerFutures = new ArrayList<>();
        for (int workerNo = 1; workerNo <= workersPerSlice; workerNo++) {
            workerFutures.add(springAsyncWorkerDemoService.executeWorkerTask(sliceNo, workerNo));
        }

        CompletableFuture.allOf(workerFutures.toArray(new CompletableFuture[0])).join();
        List<SpringAsyncPoolDemoService.AsyncStep> workerSteps = workerFutures.stream()
                .map(CompletableFuture::join)
                .toList();

        return CompletableFuture.completedFuture(new SpringAsyncPoolDemoService.SliceDispatchResult(sliceNo, schedulerThread, workerSteps));
    }
}
