package com.example.redislockdemo.concurrency;

import com.example.redislockdemo.config.SpringAsyncPoolConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAsyncPoolDemoTest {

    @Test
    void schedulerPoolDispatchesToWorkerPool() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(SpringAsyncPoolConfig.class, SpringAsyncPoolDemoService.class, SpringAsyncSchedulerDemoService.class, SpringAsyncWorkerDemoService.class);
            context.refresh();

            SpringAsyncPoolDemoService springAsyncPoolDemoService = context.getBean(SpringAsyncPoolDemoService.class);
            SpringAsyncPoolDemoService.AsyncDispatchResult result = springAsyncPoolDemoService.demonstrateSchedulerToWorkerFlow(2, 2);

            assertThat(result.sliceCount()).isEqualTo(2);
            assertThat(result.workersPerSlice()).isEqualTo(2);
            assertThat(result.slices()).hasSize(2);
            assertThat(result.slices())
                    .extracting(SpringAsyncPoolDemoService.SliceDispatchResult::schedulerThread)
                    .allMatch(name -> name.startsWith("scheduler-async-"));
            assertThat(result.slices())
                    .flatExtracting(SpringAsyncPoolDemoService.SliceDispatchResult::workerSteps)
                    .extracting(SpringAsyncPoolDemoService.AsyncStep::threadName)
                    .allMatch(name -> name.startsWith("worker-async-"));
        }
    }
}
