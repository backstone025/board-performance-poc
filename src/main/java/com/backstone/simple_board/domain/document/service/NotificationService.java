package com.backstone.simple_board.domain.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

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

    // Test C 솔루션 1: 비동기 메소드 내부에 Try-Catch 처리
    @Async("notificationExecutor")
    public void sendAsyncWithInternalTryCatch(Long documentId, boolean shouldFail) {
        log.info("[내부 Try-Catch 비동기 시작] Thread: {}, DocumentId: {}",
                Thread.currentThread().getName(), documentId);
        try {
            simulateExternalApiDelay(500);
            if (shouldFail) {
                throw new IllegalStateException("외부 알림 서버 통신 장애 발생 (DocumentId: " + documentId + ")");
            }
            log.info("[내부 Try-Catch 비동기 완료] (DocumentId: " + documentId + ")");
        } catch (Exception e) {
            log.warn("[내부 Try-Catch 포착] DocumentId: {}, log: {}", documentId, e.getMessage());
        }
    }

    // Test C 솔루션 2: CompletableFuture 기반 반환 및 예외 제어
    @Async("notificationExecutor")
    public CompletableFuture<String> sendAsyncWithFuture(Long documentId, boolean shouldFail) {
        log.info("[Future 비동기 시작] Thread: {}, DocumentId: {}",
                Thread.currentThread().getName(), documentId);

        simulateExternalApiDelay(500);

        if (shouldFail) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Future 알림 발송 실패(DocumentId: " + documentId + ")")
            );
        }

        return CompletableFuture.completedFuture("Future 알림 발송 성공(DocumentId: " + documentId + ")");
    }
}
