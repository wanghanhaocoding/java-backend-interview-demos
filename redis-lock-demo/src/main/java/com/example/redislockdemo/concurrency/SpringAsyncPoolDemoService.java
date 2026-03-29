package com.example.redislockdemo.concurrency;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class SpringAsyncPoolDemoService {

    private final SpringAsyncSchedulerDemoService springAsyncSchedulerDemoService;

    public SpringAsyncPoolDemoService(SpringAsyncSchedulerDemoService springAsyncSchedulerDemoService) {
        this.springAsyncSchedulerDemoService = springAsyncSchedulerDemoService;
    }

    public AsyncDispatchResult demonstrateSchedulerToWorkerFlow(int sliceCount, int workersPerSlice) throws InterruptedException {
        List<CompletableFuture<SliceDispatchResult>> sliceFutures = new ArrayList<>();
        for (int slice = 1; slice <= sliceCount; slice++) {
            sliceFutures.add(springAsyncSchedulerDemoService.dispatchSlice("slice-" + slice, workersPerSlice));
        }

        CompletableFuture.allOf(sliceFutures.toArray(new CompletableFuture[0])).join();
        List<SliceDispatchResult> slices = sliceFutures.stream()
                .map(CompletableFuture::join)
                .toList();

        return new AsyncDispatchResult(sliceCount, workersPerSlice, slices);
    }

    public record AsyncDispatchResult(int sliceCount, int workersPerSlice, List<SliceDispatchResult> slices) {
    }

    public record SliceDispatchResult(String sliceNo, String schedulerThread, List<AsyncStep> workerSteps) {
    }

    public record AsyncStep(String role, String sliceNo, int workerNo, String threadName) {
    }
}
