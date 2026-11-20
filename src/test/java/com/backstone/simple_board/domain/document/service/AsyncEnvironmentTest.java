package com.backstone.simple_board.domain.document.service;

import com.backstone.simple_board.global.config.AsyncConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StopWatch;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class AsyncEnvironmentTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AsyncConfig asyncConfig;

    @Test
    @DisplayName("[Test A - 대조군] 동기 실행")
    void verifySyncExecution() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        String callerThread = Thread.currentThread().getName();
        log.info("[호출부 메인] Thread: {}", callerThread);

        // 동기 메서드 호출
        notificationService.sendSync(1L);

        stopWatch.stop();
        long elapsedTime = stopWatch.getTotalTimeMillis();
        log.info("[호출부 반환 시간 (동기)] {} ms", elapsedTime);

        // 검증: 외부 지연 시간(500ms) 이상 블로킹되어야 한다.
        assertThat(elapsedTime).isGreaterThanOrEqualTo(500L);
    }

    @Test
    @DisplayName("[Test A - 비교군] 비동기 실행")
    void verifyAsyncExecution() throws InterruptedException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        String callerThread = Thread.currentThread().getName();
        log.info("[호출부 메인] Thread: {}", callerThread);

        // 비동기 메서드 호출
        notificationService.sendAsync(1L, false);

        stopWatch.stop();
        long elapsedTime = stopWatch.getTotalTimeMillis();
        log.info("[호출부 반환 시간 (비동기)] {} ms", elapsedTime);

        // 검증: 외부 지연 시간(500ms) 이상 블로킹되어야 한다.
        assertThat(elapsedTime).isLessThan(100L);

        // 워커 스레드(async-notification-1) 작업 완료 확인을 위한 대기
        Thread.sleep(700);
    }

    @Test
    @DisplayName("[Test B] 비동기 작업 내 예외 발생 시 호출부 try-catch 무효해지는 현상 확인")
    void verifyTryCatchIneffectiveInAsyncExecution() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean caughtInCaller = new AtomicBoolean(false);
        AtomicReference<Throwable> caughtInGlobalHandler = new AtomicReference<>();

        asyncConfig.setTestExceptionCallback(exception -> {
            caughtInGlobalHandler.set(exception);
            latch.countDown();
        });

        log.info("[호출부 메인] try-catch 블록 진입: Thread: {}", Thread.currentThread().getName());

        try {
            notificationService.sendAsync(2L, true);
        }catch (Exception ex){
            caughtInCaller.set(true);
            log.info("[호출부 Catch] 예외 포착 성공: {}", ex.getMessage());
        }

        log.info("[호출부 메인] try-catch 블록 통과 완료 (예외 미포착 상태)");

        boolean completed = latch.await(2, TimeUnit.SECONDS);

        // 검증 1: 호출부의 try-catch는 비동기 예외를 잡지 못함
        assertThat(caughtInCaller.get()).isFalse();

        // 검증 2: 예외는 스레드 풀 바깥의 전역 핸들러로 전달됨
        assertThat(completed).isTrue();
        assertThat(caughtInGlobalHandler.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("외부 알림 서버 통신 장애 발생");
    }
}