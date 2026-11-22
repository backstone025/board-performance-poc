package com.backstone.simple_board.domain.document.service;

import com.backstone.simple_board.domain.document.event.DocumentCreatedEvent;
import com.backstone.simple_board.global.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentStreamProducer {

    private final StringRedisTemplate redisTemplate;

    // 이벤트 발행
    public RecordId publish(DocumentCreatedEvent event) {
        RecordId recordId = redisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .ofStrings(event.toMap())
                        .withStreamKey(RedisStreamConfig.STREAM_KEY)
        );
        log.info("[Stream Producer] 이벤트 발행 완료 Stream: {}, RecordId: {}, EventId: {}, DocumentId: {}",
                RedisStreamConfig.STREAM_KEY, recordId, event.eventId(), event.documentId());
        return recordId;
    }
}
