package com.backstone.simple_board.domain.document.dto.request;

public record CreateDocRequest(
        String title,
        String content
) {
}
