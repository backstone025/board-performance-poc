package com.backstone.simple_board.domain.document.service;

import com.backstone.simple_board.domain.document.event.DocumentCreatedEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAfterCommitListener {
    private final DocumentStreamProducer streamProducer;

    /**
     * [Case C-2] 커밋 후 리스너 실행 시점에 브로커 장애 발생하는 시물레이션 플래그
     */
    @Getter
    private final AtomicBoolean simulateBrokerFailure = new AtomicBoolean(false);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDocumentAfterCommit(DocumentCreatedEvent event) {
        log.info("[AfterCommit Listener] DB 커밋 확정 후 이벤트 수신 - EventId: {}, DocumentId: {}",
                event.eventId(), event.documentId());

        if (simulateBrokerFailure.get()) {
            log.error("[AfterCommit Listener] DB는 이미 커밋되었으나 브로커 발행 실패(메시지 영구 유실) EventId: {}",
                    event.eventId());
            throw new IllegalStateException("RedisConnectionFailureException: AFTER_COMMIT 시점 브로커 타임아웃");
        }

        streamProducer.publish(event);
    }

    public void resetTestState() {
        simulateBrokerFailure.set(false);
    }
}
