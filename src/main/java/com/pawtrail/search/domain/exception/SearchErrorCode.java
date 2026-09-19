package com.pawtrail.search.domain.exception;

import com.pawtrail.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SearchErrorCode implements ErrorCode {

    // place 를 부르지 못함
    // 이벤트 처리에서는 이 예외로 묶음을 다시 시도하고, 끝내 안 되면 .dlq 로 보냄
    PLACE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "장소 정보를 불러오지 못했습니다."),

    // review 를 부르지 못함
    // 재색인은 이 예외를 받으면 남은 장소의 평점을 그대로 둠 — 빈 결과로 읽어 지우면 안 됨
    REVIEW_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "후기 평점을 불러오지 못했습니다."),

    // 재색인이 이미 돌고 있음
    // 두 대 어느 쪽에서 돌고 있든 잠금이 하나라 같은 답이 나감
    REINDEX_ALREADY_RUNNING(HttpStatus.CONFLICT, "이미 실행 중인 재색인이 있습니다.");

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
