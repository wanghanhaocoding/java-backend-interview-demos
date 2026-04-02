package com.example.redislockdemo.concurrency;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
                .collect(Collectors.toList());

        return new AsyncDispatchResult(sliceCount, workersPerSlice, slices);
    }

    public static final class AsyncDispatchResult {
        private final int sliceCount;
        private final int workersPerSlice;
        private final List<SliceDispatchResult> slices;

        public AsyncDispatchResult(int sliceCount, int workersPerSlice, List<SliceDispatchResult> slices) {
            this.sliceCount = sliceCount;
            this.workersPerSlice = workersPerSlice;
            this.slices = Collections.unmodifiableList(new ArrayList<SliceDispatchResult>(slices));
        }

        public int sliceCount() {
            return sliceCount;
        }

        public int workersPerSlice() {
            return workersPerSlice;
        }

        public List<SliceDispatchResult> slices() {
            return slices;
        }
    }

    public static final class SliceDispatchResult {
        private final String sliceNo;
        private final String schedulerThread;
        private final List<AsyncStep> workerSteps;

        public SliceDispatchResult(String sliceNo, String schedulerThread, List<AsyncStep> workerSteps) {
            this.sliceNo = sliceNo;
            this.schedulerThread = schedulerThread;
            this.workerSteps = Collections.unmodifiableList(new ArrayList<AsyncStep>(workerSteps));
        }

        public String sliceNo() {
            return sliceNo;
        }

        public String schedulerThread() {
            return schedulerThread;
        }

        public List<AsyncStep> workerSteps() {
            return workerSteps;
        }
    }

    public static final class AsyncStep {
        private final String role;
        private final String sliceNo;
        private final int workerNo;
        private final String threadName;

        public AsyncStep(String role, String sliceNo, int workerNo, String threadName) {
            this.role = role;
            this.sliceNo = sliceNo;
            this.workerNo = workerNo;
            this.threadName = threadName;
        }

        public String role() {
            return role;
        }

        public String sliceNo() {
            return sliceNo;
        }

        public int workerNo() {
            return workerNo;
        }

        public String threadName() {
            return threadName;
        }
    }
}
