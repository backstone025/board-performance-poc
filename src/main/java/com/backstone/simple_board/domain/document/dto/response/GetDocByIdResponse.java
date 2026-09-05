package com.backstone.simple_board.domain.document.dto.response;

import com.backstone.simple_board.domain.document.entity.Document;

public record GetDocByIdResponse(
        Long id,
        String title,
        String content
) {
    public static GetDocByIdResponse from(Document document) {
        return new GetDocByIdResponse(
                document.getId(),
                document.getTitle(),
                document.getContent()
        );
    }
}
