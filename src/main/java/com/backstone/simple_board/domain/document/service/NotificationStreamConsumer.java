package com.backstone.simple_board.domain.document.service;

import com.backstone.simple_board.domain.document.event.DocumentCreatedEvent;
import com.backstone.simple_board.global.config.RedisStreamConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:event:";  // 멱등성 키 접두사

    @Getter
    private final AtomicBoolean simulateFailure = new AtomicBoolean(false);

    @Getter
    private final AtomicInteger processedCounter = new AtomicInteger(0);
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        DocumentCreatedEvent event = DocumentCreatedEvent.fromMap(message.getValue());
        log.info("[Stream Consumer] 메시지 수신 Message: {}, RecordId: {}, EventId: {}, DocumentId: {}",
                Thread.currentThread().getName(), message.getId(), event.eventId(), event.documentId());

        // 컨슈머 장애 시뮬레이션: 예외 발생 시 XACK 호출하지 않아 PEL에 잔류
        if (simulateFailure.get()) {
            log.error("[Stream Consumer 장애 발생] XACK 미처리 상태로 작업 중단. RecordId: {}, EventId: {}",
                    message.getId(), event.eventId());
            throw new IllegalStateException("컨슈머 알림 처리 중 일시 장애 발생");
        }

        // 멱등성 검증: SETNX(sendIfAbsent)로 이미 처리된 eventId인지 확인
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + event.eventId();
        Boolean isFirstProcess = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "PROCESSED", Duration.ofHours(24));
        if (Boolean.FALSE.equals(isFirstProcess)) {
            log.warn("[Stream Consumer 중복 감지] 이미 처리된 이벤트이므로 실제 발송 생략 후 XACK. EventId: {}", event.eventId());
            acknowledge(message);
            return;
        }

        // 실제 알림 발송
        processedCounter.incrementAndGet();
        log.info("[Stream Consumer 알림 발송 완료] DocumentId: {}, Title: {}, 누적 처리 건수: {}",
                event.documentId(), event.title(), processedCounter.get());

        acknowledge(message);
    }

    // 수동 XACK 반환(PEL 제거)
    private void acknowledge(MapRecord<String, String, String> message) {
        redisTemplate.opsForStream().acknowledge(
                RedisStreamConfig.STREAM_KEY,
                RedisStreamConfig.CONSUMER_GROUP,
                message.getId()
        );
        log.info("[Stream Consumer XACK 완료] RecordId: {}", message.getId());
    }

    public void resetTestState() {
        simulateFailure.set(false);
        processedCounter.set(0);
    }
}
