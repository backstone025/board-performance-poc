package com.backstone.simple_board.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Slf4j
@Configuration
@EnableAsync    // @Async 활성화
public class AsyncConfig implements AsyncConfigurer {

    // 전역 예외 핸들러 동작 감지를 위한 콜백
    private Consumer<Throwable> testExceptionCallback;

    public void  setTestExceptionCallback(Consumer<Throwable> callback) {
        this.testExceptionCallback = callback;
    }

    // 스레드 풀 관리
    @Bean(name = "notificationExecutor")    // 오버라이드 하지 않고 별도 빈으로 등록했기에 반드시 이름 명시
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);                            // 기본 유지 스레드 수
        executor.setMaxPoolSize(5);                             // 최대 확장 스레드 수
        executor.setQueueCapacity(10);                          // 대기 큐 크기
        executor.setThreadNamePrefix("async-notification-");    // 스레드 분리 포착 용도 접두사
        executor.initialize();
        return executor;
    }

    // 전역 비동기 예외 처리
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("[전역 비동기 예외 핸들러] method: {}, params: {}, exception: {}",
                    method.getName(), Arrays.toString(params), throwable.getMessage());
            if(testExceptionCallback != null) {
                testExceptionCallback.accept(throwable);
            }
        };
    }
}
