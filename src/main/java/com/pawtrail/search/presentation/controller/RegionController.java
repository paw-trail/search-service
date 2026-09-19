package com.pawtrail.search.presentation.controller;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.search.application.dto.output.RegionOutput;
import com.pawtrail.search.application.service.RegionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지역 목록 API 입니다.
 */
@RestController
@RequestMapping("/api/v1/search/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    /**
     * 장소가 있는 시도와 그 시군구를 장소 수와 함께 돌려줍니다(search ㉱).
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<List<RegionOutput>>> regions() {
        return ResponseEntity.ok(CommonApiResponse.success(regionService.regions()));
    }
}
