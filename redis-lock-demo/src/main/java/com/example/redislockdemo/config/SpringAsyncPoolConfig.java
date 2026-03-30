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
        // 调度线程更关注扫描、分片、投递，队列可以更短，让积压更早暴露出来。
        return buildExecutor(2, 4, 2, "scheduler-async-");
    }

    @Bean(name = "workerPool")
    public Executor workerPool() {
        // 执行线程真正承接业务逻辑，队列通常会比 schedulerPool 略大一些。
        return buildExecutor(2, 4, 4, "worker-async-");
    }

    private Executor buildExecutor(int corePoolSize, int maxPoolSize, int queueCapacity, String prefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 这里和原生 ThreadPoolExecutor 是同一套容量模型，只是换成了 Spring 的包装类。
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
