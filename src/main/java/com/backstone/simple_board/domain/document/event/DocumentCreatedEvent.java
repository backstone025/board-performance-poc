package com.backstone.simple_board.domain.document.event;

import java.util.Map;
import java.util.UUID;

public record DocumentCreatedEvent(
        String eventId,
        Long documentId,
        String title,
        String occurredAt
) {
    public static DocumentCreatedEvent of(Long documentId, String title, String occurredAt) {
        return new DocumentCreatedEvent(
                UUID.randomUUID().toString(),
                documentId,
                title,
                occurredAt
        );
    }

    // Redis Streams(MapRecord) 적재를 위한 Map 변환
    public Map<String, String> toMap() {
        return Map.of(
                "eventId", this.eventId,
                "documentId", String.valueOf(this.documentId),
                "title", this.title,
                "occurredAt", this.occurredAt
        );
    }

    // Redis Streams에서 읽어온 Map을 이벤트 객체로 복원
    public static DocumentCreatedEvent fromMap(Map<String, String> map) {
        return new DocumentCreatedEvent(
                map.get("eventId"),
                Long.valueOf(map.get("documentId")),
                map.get("title"),
                map.get("occurredAt")
        );
    }
}
