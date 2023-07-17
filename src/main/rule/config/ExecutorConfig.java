package org.fordes.adg.rule.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置
 *
 * @author fordes123 on 2023/2/20
 */
@Component
public class ExecutorConfig {

    private final static int N = Runtime.getRuntime().availableProcessors();

    @Primary
    @Bean("ruleExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(2 * N);
        taskExecutor.setMaxPoolSize(2 * N);
        taskExecutor.setThreadNamePrefix("ruleTask-");
        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.setAwaitTerminationSeconds(60);
        taskExecutor.setKeepAliveSeconds(10);
        taskExecutor.setAllowCoreThreadTimeOut(true);
        taskExecutor.initialize();
        return taskExecutor;
    }
}

    
    