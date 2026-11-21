package com.backstone.simple_board.domain.document.service;

import com.backstone.simple_board.domain.document.event.DocumentCreatedEvent;
import com.backstone.simple_board.global.config.RedisStreamConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class RedisStreamPocTest {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private DocumentStreamProducer streamProducer;
    @Autowired
    private NotificationStreamConsumer streamConsumer;

    @BeforeEach
    void setUp() {
        streamConsumer.resetTestState();

        // 1. 기존 PEL(XPENDING)에 남아있는 미처리 메시지가 있다면 모두 XACK 처리해 비움
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(RedisStreamConfig.STREAM_KEY, RedisStreamConfig.CONSUMER_GROUP, Range.unbounded(), 100);
        for (PendingMessage message : pending) {
            redisTemplate.opsForStream().acknowledge(
                    RedisStreamConfig.STREAM_KEY, RedisStreamConfig.CONSUMER_GROUP, message.getId()
            );
        }

        // 2. 스트림 내 엔트리 비우기 (컨슈머 그룹과 구독 상태는 그대로 유지)
        redisTemplate.opsForStream().trim(RedisStreamConfig.STREAM_KEY, 0);
    }

    @Test
    @DisplayName("[Test A] 컨슈머 장애 시 XPENDING 잔류 보존, 복구 재처리 및 eventId 멱등성 검증")
    void verifyConsumerResilienceAndIdempotency() throws InterruptedException {
        // 컨슈머 장애 모드 활성화(XACK 호출 전 예외 발생)
        streamConsumer.getSimulateFailure().set(true);
        DocumentCreatedEvent event = DocumentCreatedEvent.of(101L, "장애 복원력 테스트 계시글", LocalDateTime.now().toString());
        streamProducer.publish(event);
        Thread.sleep(300);

        // 검증 1: 컨슈머 중단으로 실제 처리 거수는 0이며, Redis PEL(XPENDING)에 1건이 유실 없이 보존됨
        assertThat(streamConsumer.getProcessedCounter().get()).isEqualTo(0);
        PendingMessagesSummary pendingMessagesSummary = redisTemplate.opsForStream()
                .pending(RedisStreamConfig.STREAM_KEY, RedisStreamConfig.CONSUMER_GROUP);
        assertThat(pendingMessagesSummary.getTotalPendingMessages()).isEqualTo(1L);
        log.info("[Test A 검증 1] 컨슈머 장애 후 PEL(XPENDING) 잔류 메시지 수: {}", pendingMessagesSummary.getTotalPendingMessages());

        // 컨슈머 복구 후 PEL 미처리 메시지 재조회 및 재처리
        streamConsumer.getSimulateFailure().set(false);
        List<MapRecord<String, Object, Object>> pendingRecords = redisTemplate.opsForStream().read(
                Consumer.from(RedisStreamConfig.CONSUMER_GROUP, RedisStreamConfig.CONSUMER_NAME),
                StreamOffset.create(RedisStreamConfig.STREAM_KEY, ReadOffset.from("0")) // 미처리 메시지 재조회
        );

        assertThat(pendingRecords).hasSize(1);
        MapRecord<String, String, String> recoveredRecord = StreamRecords.newRecord()
                .in(RedisStreamConfig.STREAM_KEY)
                .withId(pendingRecords.get(0).getId())
                .ofStrings(event.toMap());
        streamConsumer.onMessage(recoveredRecord);

        // 검증 2: 복구 후 정상 처리(1건) 및 XACK 완료로 PEL 잔류 0건 확인
        assertThat(streamConsumer.getProcessedCounter().get()).isEqualTo(1);
        PendingMessagesSummary afterRecovery = redisTemplate.opsForStream()
                .pending(RedisStreamConfig.STREAM_KEY, RedisStreamConfig.CONSUMER_GROUP);
        assertThat(afterRecovery.getTotalPendingMessages()).isEqualTo(0L);
        log.info("[Test A 검증 2] 컨슈머 복구 및 XACK 후 PEL 잔류 메시지 수: {}", afterRecovery.getTotalPendingMessages());

        // 동일 eventId가 재수신 될 경우 멱등성 방어 검증
        streamConsumer.onMessage(recoveredRecord);
        assertThat(streamConsumer.getProcessedCounter().get()).isEqualTo(1);    // 중복 무시
        log.info("[Test A 검증 3] 중복 수신 후 최종 처리 건수(멱등성 보장): {}", streamConsumer.getProcessedCounter().get());
    }
}
