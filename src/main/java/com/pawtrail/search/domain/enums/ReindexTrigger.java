package com.pawtrail.search.domain.enums;

/**
 * 전량 재색인을 누가 걸었는지입니다. 끝 요약 로그에 남깁니다.
 */
public enum ReindexTrigger {

    // 매일 한 번 스케줄이 검
    SCHEDULE,

    // 관리자가 POST /api/v1/admin/search/reindex 로 검
    ADMIN
}
