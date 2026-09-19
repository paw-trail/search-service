package com.pawtrail.search.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.search.application.dto.output.ReindexTriggerOutput;
import com.pawtrail.search.application.service.ReindexTriggerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 검색 API 입니다.
 *
 * ADMIN 만 부를 수 있습니다. common 의 보안 설정이 /api/v1/admin/** 을 막아 두어 여기서 따로 확인하지 않습니다.
 */
@RestController
@RequestMapping("/api/v1/admin/search")
@RequiredArgsConstructor
public class AdminSearchController {

    private final ReindexTriggerService reindexTriggerService;

    /**
     * 전량 재색인을 겁니다. 매일 스케줄과 같은 작업입니다.
     *
     * 202 로 바로 답하고 뒤에서 돕니다. 결과는 끝 요약 로그 한 줄로 남습니다.
     * 어느 대에서든 이미 돌고 있으면 409 REINDEX_ALREADY_RUNNING 입니다.
     */
    @PostMapping("/reindex")
    public ResponseEntity<CommonApiResponse<ReindexTriggerOutput>> reindex() {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CommonApiResponse.success(reindexTriggerService.start()));
    }
}
