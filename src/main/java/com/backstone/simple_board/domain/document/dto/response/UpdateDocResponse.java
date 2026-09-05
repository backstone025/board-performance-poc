package com.backstone.simple_board.domain.document.dto.response;

import com.backstone.simple_board.domain.document.entity.Document;

public record UpdateDocResponse(
        Long id,
        String title,
        String content
) {
    public static UpdateDocResponse from(Document document) {
        return new UpdateDocResponse(
                document.getId(),
                document.getTitle(),
                document.getContent()
        );
    }
}
