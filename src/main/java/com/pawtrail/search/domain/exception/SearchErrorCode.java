package com.pawtrail.search.domain.exception;

import com.pawtrail.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SearchErrorCode implements ErrorCode {

    // place 를 부르지 못함
    // 이벤트 처리에서는 이 예외로 묶음을 다시 시도하고, 끝내 안 되면 .dlq 로 보냄
    PLACE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "장소 정보를 불러오지 못했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    SearchErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }

    @Override
    public String getCode() {
        return this.name();
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
