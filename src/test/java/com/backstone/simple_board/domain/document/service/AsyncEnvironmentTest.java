package com.backstone.simple_board.domain.document.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StopWatch;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class AsyncEnvironmentTest {

    @Autowired
    private NotificationService notificationService;

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
}