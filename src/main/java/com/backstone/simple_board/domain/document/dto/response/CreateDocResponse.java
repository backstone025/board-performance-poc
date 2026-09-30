package com.backstone.simple_board.domain.document.dto.response;

import com.backstone.simple_board.domain.document.entity.Document;

public record CreateDocResponse(
        Long id,
        String title,
        String content
) {
    public static CreateDocResponse from(Document document) {
        return new CreateDocResponse(
                document.getId(),
                document.getTitle(),
                document.getContent()
        );
    }
}
