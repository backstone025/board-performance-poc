package com.backstone.simple_board.domain.document.dto.request;

public record UpdateDocRequest(
        String title,
        String content
) {
}
