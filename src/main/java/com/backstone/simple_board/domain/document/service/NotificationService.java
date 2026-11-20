package com.backstone.simple_board.domain.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {
    // 지연 발생
    private void simulateExternalApiDelay(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 동기적 알림 전송
    public void sendSync(Long documentId) {
        simulateExternalApiDelay(500);
        log.info("[동기 실행 완료] Thread: {}, DocumentId: {}",
                Thread.currentThread().getName(), documentId);
    }

    // 비동기 알림 전송
    @Async("notificationExecutor")
    public void sendAsync(Long documentId, boolean shouldFail) {
        log.info("[비동기 작업 시작] Thread: {}, DocumentId: {}",
                Thread.currentThread().getName(), documentId);

        simulateExternalApiDelay(500);

        // 인위적인 실패 처리
        if (shouldFail) {
            throw new IllegalStateException("외부 알림 서버 통신 장애 발생 (DocumentId: " + documentId + ")");
        }

        log.info("[비동기 작업 완료] Thread: {}, DocumentId: {}",
                Thread.currentThread().getName(), documentId);
    }
}
