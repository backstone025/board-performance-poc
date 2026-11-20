package com.backstone.simple_board.domain.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {
    // 지연 발생
    private void simulateExternalApiDelay(long milliseconds){
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 동기적 알림 전송
    public void sendSync(Long documentId){
        simulateExternalApiDelay(500);
        log.info("[동기 실행 완료] Thread: {}, DocumentId: {}",
                Thread.currentThread().getName(), documentId);
    }
}
