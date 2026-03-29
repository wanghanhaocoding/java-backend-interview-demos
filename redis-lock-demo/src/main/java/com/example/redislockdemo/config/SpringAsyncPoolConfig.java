package com.example.redislockdemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class SpringAsyncPoolConfig {

    @Bean(name = "schedulerPool")
    public Executor schedulerPool() {
        return buildExecutor(2, 4, 2, "scheduler-async-");
    }

    @Bean(name = "workerPool")
    public Executor workerPool() {
        return buildExecutor(2, 4, 4, "worker-async-");
    }

    private Executor buildExecutor(int corePoolSize, int maxPoolSize, int queueCapacity, String prefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        // 线程池满载后，让调用方线程自己执行，和 xtimer 项目里一致。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
