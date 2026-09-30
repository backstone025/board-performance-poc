package com.backstone.simple_board.domain.document.dto.response;

public record DeleteDocResponse(
        boolean deleted,
        Long id
) {
}
