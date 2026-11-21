package com.backstone.simple_board.domain.document.service;

import com.backstone.simple_board.domain.document.event.DocumentCreatedEvent;
import com.backstone.simple_board.domain.document.repository.DocumentRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired
    private DocumentDualWriteExperimentService experimentService;
    @Autowired
    private DocumentRepository repository;
    @Autowired
    private DocumentAfterCommitListener afterCommitListener;

    @BeforeEach
    void setUp() {
        streamConsumer.resetTestState();
        afterCommitListener.resetTestState();

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

        // 3. 이전 테스트에서 커밋된 DB 레코드 초기화
        repository.deleteAll();
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

    @Test
    @DisplayName("[Test B-1] 단일 트랜잭션 이중 쓰기 시 DB 롤백에도 유령 이벤트가 발행되는 불일치 재현")
    void reproducePhantomEventOnDbRollback() throws InterruptedException {
        // DB 저장 -> Redis 발행 후 예외로 DB 롤백 유도
        assertThatThrownBy(() -> experimentService.createWithPhantomEvent("롤백될 유령 게시글", "글 내용"))
                .isInstanceOf(IllegalStateException.class);

        Thread.sleep(300);  // 백그라운드 컨슈머 폴링 및 처리 대기

        // 검증: DB에는 게시글이 롤백되어 0건이나, Redis에는 발행되어 컨슈머가 알림을 발송함
        long dbCount = repository.count();
        Long streamSize = redisTemplate.opsForStream().size(RedisStreamConfig.STREAM_KEY);
        int notifiedCount = streamConsumer.getProcessedCounter().get();

        log.warn("[Test B-1 불일치 확인] DB 게시글 수: {}, Redis 스트림 적재 수: {}, 컨슈머 알림 발송 수: {}",
                dbCount, streamSize, notifiedCount);

        assertThat(dbCount).isEqualTo(0L);
        assertThat(streamSize).isEqualTo(1L);
        assertThat(notifiedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("[Test B-2] DB 커밋 성공 후 브로커 발행 장애 시 이벤트가 영구 누락되는 불일치 재현")
    void reproduceLostEventOnBrokerFailure() throws InterruptedException {
        // DB 저장은 성공했으나 브로커 타임아웃으로 이벤트 발행 실패
        Long savedDocumentId = experimentService.createWithLostEvent("정상 등록된 게시글", "글 내용");

        Thread.sleep(300);  // 백그라운드 컨슈머 폴링 및 처리 대기

        // 검증: DB에는 게시글이 정상 커밋(1건)되었으나, 브로커 및 컨슈머에는 이벤트가 0건으로 영구 유실됨
        long dbCount = repository.count();
        Long streamSize = redisTemplate.opsForStream().size(RedisStreamConfig.STREAM_KEY);
        int notifiedCount = streamConsumer.getProcessedCounter().get();

        log.warn("[Test B-2 불일치 확인] 저장된 DocumentId: {}, DB 게시글 수: {}, Redis 스트림 적재 수: {}, 컨슈머 알림 발송 수: {}",
                savedDocumentId, dbCount, streamSize, notifiedCount);

        assertThat(dbCount).isEqualTo(1L);
        assertThat(streamSize).isEqualTo(0L);
        assertThat(notifiedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("[Test C-1] AFTER_COMMIT 적용 시 DB 롤백에 따른 유령 이벤트(Case B-1) 100% 차단 검증")
    void verifyAfterCommitPreventsPhantomEventOnDbRollback() throws InterruptedException {
        // DB 저장 후 스프링 내부 이벤트 발생 -> 직후 예외로 DB 롤백
        assertThatThrownBy(() ->
                experimentService.createWithAfterCommitAndRollback("롤백될 게시글", "글 내용"))
                .isInstanceOf(IllegalStateException.class);

        Thread.sleep(300);  // 백그라운드 컨슈머 폴링 및 처리 대기

        // 검증: 트랜잭션이 커밋되지 않아 AFTER_COMMIT 리스너가 실행되지 않았으므로 유령 이벤트 0건
        long dbCount = repository.count();
        Long streamSize = redisTemplate.opsForStream().size(RedisStreamConfig.STREAM_KEY);
        int notifiedCount = streamConsumer.getProcessedCounter().get();

        log.info("[Test C-1 검증 완료] 유령 이벤트 차단 성공 - DB 게시글 수: {}, Redis 스트림 적재 수: {}, 알림 발송 수: {}",
                dbCount, streamSize, notifiedCount);

        assertThat(dbCount).isEqualTo(0L);
        assertThat(streamSize).isEqualTo(0L);
        assertThat(notifiedCount).isEqualTo(0);
    }

    @Test
    @DisplayName("[Test C-2] AFTER_COMMIT 이후 브로커 장애 시 DB 커밋은 유지되나 이벤트 유실 한계(Case B-2) 잔존 확인")
    void verifyAfterCommitStillLosesEventOnBrokerFailure() throws InterruptedException {
        // DB 커밋 직후 실행되는 AFTER_COMMIT 리스너에서 브로커 장애가 발생하도록 설정
        afterCommitListener.getSimulateBrokerFailure().set(true);

        // DB 트랜잭션은 정상 커밋되지만, 커밋 이후 AFTER_COMMIT 리스너에서 브로커 예외 발생
        Long savedDocumentId = experimentService.createWithAfterCommitAndBrokerFailure("커밋 후 알림 유실 게시글", "내용입니다");

        Thread.sleep(300);

        // 검증: DB에는 이미 커밋(1건)되어 롤백 불가하지만, 브로커 적재 및 알림 발송은 0건으로 영구 유실됨!
        long dbCount = repository.count();
        Long streamSize = redisTemplate.opsForStream().size(RedisStreamConfig.STREAM_KEY);
        int notifiedCount = streamConsumer.getProcessedCounter().get();

        log.warn("[Test C-2 한계 포착] 저장된 DocumentId: {}, DB 게시글 수: {}, Redis 스트림 적재 수: {}, 알림 발송 수: {}",
                savedDocumentId, dbCount, streamSize, notifiedCount);

        assertThat(dbCount).isEqualTo(1L);
        assertThat(streamSize).isEqualTo(0L);
        assertThat(notifiedCount).isEqualTo(0);
    }
}
