package com.backstone.simple_board.global.error.code;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode {
    String getCode();

    String getMessage();

    HttpStatus getStatus();
}
