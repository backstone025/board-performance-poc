package com.backstone.simple_board.domain.document.service;

import com.backstone.simple_board.domain.document.entity.Document;
import com.backstone.simple_board.domain.document.event.DocumentCreatedEvent;
import com.backstone.simple_board.domain.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentDualWriteExperimentService {
    private final DocumentRepository documentRepository;
    private final DocumentStreamProducer streamProducer;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * [Case B-1] 유령 이벤트 발생(Phantom Event)
     * </br>
     * - 단일 트랜잭션 내에서 DB 저장 -> 브로커 발행 수, 커밋 직전 런타임 예외로 DB만 롤백
     */
    @Transactional
    public void createWithPhantomEvent(String title, String content) {
        Document saved = documentRepository.save(Document.create(title, content));
        log.info("[Case B-1] DB 임시 저장 완료(커밋 안된 상태) DocumentId: {}", saved.getId());

        // DB 커밋 전 외부 브로커(Redis Streams)로 먼저 메시지 발행
        DocumentCreatedEvent event = DocumentCreatedEvent.of(saved.getId(), saved.getTitle(), LocalDateTime.now().toString());
        streamProducer.publish(event);

        // 트랜잭션 종료 직전 예외 발생
        throw new IllegalStateException("[Case B-1] DB 커밋 직전 비즈니스 예외 발생 (트랜잭션 롤백 유도)");
    }

    /**
     * [Case B-2] 이벤트 영구 누락 (Lost Event)
     * <ol>
     *     <li>DB 저장 후 브로커 시점에 일시적인 네트워크 장애 발생</li>
     *     <li>서비스 로직에서 브로커 장애가 게시글 작성 자체를 막지 않도록 예외를 삼킴(catch)</li>
     * </ol>
     */
    @Transactional
    public Long createWithLostEvent(String title, String content) {
        Document saved = documentRepository.save(Document.create(title, content));
        log.info("[Case B-2] DB 저장 완료 - DocumentId: {}", saved.getId());

        try {
            // 브로커 발행 시도 중 네트워크 타임 이웃 장애 발생
            simulateBrokerTimeout();
        } catch (Exception e) {
            // 알림 기능 장애로 핵심 로직(게시글 등록)까지 실패시키지 않도록, 예외를 로깅만 하고 넘긴다.
            log.error("[Case B-2] Redis 브로커 장애로 이벤트 발행 실패 (게시글 정상 커밋) - DocumentId: {}, 사유: {}",
                    saved.getId(), e.getMessage());
        }

        return saved.getId();
    }

    private void simulateBrokerTimeout() {
        throw new IllegalStateException("RedisConnectionFailureException: 브로커 네트워크 타임아웃");
    }

    /**
     * [Case C-1] AFTER_COMMIT 적용 후 트랜잭션 롤백 (Case B-1 유령 이벤트 차단 검증)
     */
    @Transactional
    public void createWithAfterCommitAndRollback(String title, String content) {
        Document saved = documentRepository.save(Document.create(title, content));

        log.info("[Case B-1] DB 임시 저장 완료(커밋 안된 상태) DocumentId: {}", saved.getId());

        // 외부 Redis Streams로 직접 알림 보내지 않고 스프링 내부 이벤트만 등록
        DocumentCreatedEvent event = DocumentCreatedEvent.of(saved.getId(), saved.getTitle(), LocalDateTime.now().toString());
        eventPublisher.publishEvent(event);

        // 트랜잭션 롤백 유도
        throw new IllegalStateException("[Case C-1] DB 커밋 직전 비즈니스 예외 발생 (트랜잭션 롤백 유도)");
    }

    /**
     * [Case C-2] AFTER_COMMIT 적용 후 정상 커밋되었으나 리스너에서 브로커 장애 발생(Case B-2 한계 잔존)
     */
    @Transactional
    public Long createWithAfterCommitAndBrokerFailure(String title, String content) {
        Document saved = documentRepository.save(Document.create(title, content));
        log.info("[Case C-2] DB 저장 완료 - DocumentId: {}", saved.getId());

        DocumentCreatedEvent event = DocumentCreatedEvent.of(saved.getId(), saved.getTitle(), LocalDateTime.now().toString());
        eventPublisher.publishEvent(event);

        return saved.getId();
    }
}
