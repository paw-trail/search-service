package com.pawtrail.search.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.search.application.dto.output.SearchCardOutput;
import com.pawtrail.search.application.service.TrendingService;
import com.pawtrail.search.presentation.request.TrendingViewRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인기 급상승 API 입니다.
 */
@RestController
@RequestMapping("/api/v1/search/trending")
@RequiredArgsConstructor
public class TrendingController {

    private final TrendingService trendingService;

    /**
     * 조회수가 많은 장소입니다. 카드 모양이 검색과 같습니다.
     *
     * sidoCode 가 없으면 전국, size 는 기본 10 · 최대 50 입니다(search ㉼).
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<List<SearchCardOutput>>> trending(
            @RequestParam(required = false) String sidoCode,
            @RequestParam(defaultValue = "" + TrendingService.DEFAULT_SIZE) int size,
            @RequestParam(required = false) List<UUID> petIds) {
        return ResponseEntity.ok(CommonApiResponse.success(trendingService.trending(sidoCode, size, petIds)));
    }

    /**
     * 장소 상세를 열었다고 알립니다. 늘 204 입니다(search ㉲).
     */
    @PostMapping("/views")
    public ResponseEntity<Void> view(@Valid @RequestBody TrendingViewRequest request) {
        trendingService.recordView(request.placeId());
        return ResponseEntity.noContent().build();
    }
}
