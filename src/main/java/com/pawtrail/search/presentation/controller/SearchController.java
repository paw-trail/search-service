package com.pawtrail.search.presentation.controller;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.search.application.dto.input.SearchCommand;
import com.pawtrail.search.application.dto.output.SearchCardOutput;
import com.pawtrail.search.application.dto.output.SearchSummaryOutput;
import com.pawtrail.search.application.service.SearchService;
import com.pawtrail.search.domain.enums.SearchSort;
import com.pawtrail.search.domain.enums.Verdict;
import com.pawtrail.search.domain.model.SearchFilter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색 API 입니다.
 *
 * 목록 파라미터는 같은 이름을 되풀이해 보냅니다. placeType=PARK&placeType=CAFE
 * 명세의 placeType[] 에서 [] 는 "여러 개" 라는 표기이며 이름에 붙이지 않습니다.
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    /** 위치를 보냈는데 반경을 안 보냈을 때의 반경(미터)입니다. 명세의 기본값입니다. */
    private static final int DEFAULT_RADIUS_M = 20_000;

    private final SearchService searchService;

    /**
     * 장소를 검색합니다. 카드마다 반려동물별 판정이 붙습니다.
     *
     * 정렬을 안 주면 위치가 있을 때 거리순, 없으면 평점순입니다(search ㉻).
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<PageResponse<SearchCardOutput>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sidoCode,
            @RequestParam(required = false) String sigunguName,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) Integer radius,
            @RequestParam(name = "placeType", required = false) List<String> placeTypes,
            @RequestParam(name = "facility", required = false) List<String> facilities,
            @RequestParam(name = "verdict", required = false) List<Verdict> verdicts,
            @RequestParam(required = false) List<UUID> petIds,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SearchCommand command = new SearchCommand(
                filterOf(q, sidoCode, sigunguName, lat, lon, radius, placeTypes, facilities),
                verdicts, petIds, sortOf(sort), page, size);
        return ResponseEntity.ok(CommonApiResponse.success(searchService.search(command)));
    }

    /**
     * 탐색 카운트 — 같은 조건에서 판정별 장소 수입니다. 반려동물이 없으면 400 입니다.
     */
    @GetMapping("/summary")
    public ResponseEntity<CommonApiResponse<SearchSummaryOutput>> summary(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sidoCode,
            @RequestParam(required = false) String sigunguName,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) Integer radius,
            @RequestParam(name = "placeType", required = false) List<String> placeTypes,
            @RequestParam(name = "facility", required = false) List<String> facilities,
            @RequestParam(required = false) List<UUID> petIds) {
        SearchCommand command = new SearchCommand(
                filterOf(q, sidoCode, sigunguName, lat, lon, radius, placeTypes, facilities),
                List.of(), petIds, null, 0, 1);
        return ResponseEntity.ok(CommonApiResponse.success(searchService.summarize(command)));
    }

    // 검색어는 띄어쓰기로 나눔, 빈 값은 조건이 없는 것으로 봄
    private static SearchFilter filterOf(String q, String sidoCode, String sigunguName, Double lat, Double lon,
                                         Integer radius, List<String> placeTypes, List<String> facilities) {
        List<String> words = q == null ? List.of() : Arrays.stream(q.trim().split("\\s+"))
                .filter(word -> !word.isBlank())
                .toList();
        // 삼항 연산자로 쓰지 않음 — int 상수와 Integer 가 섞이면 결과가 int 가 되어
        // 반경을 안 보낸 요청(null)을 풀다가 NullPointerException 이 남
        Integer radiusM = radius;
        if (radiusM == null && lat != null && lon != null) {
            radiusM = DEFAULT_RADIUS_M;
        }
        return new SearchFilter(words, blankToNull(sidoCode), blankToNull(sigunguName), lat, lon, radiusM,
                placeTypes, facilities);
    }

    private static SearchSort sortOf(String sort) {
        if (sort == null || sort.isBlank()) {
            return null;
        }
        return SearchSort.parse(sort).orElseThrow(() -> new CustomException(CommonErrorCode.VALIDATION_FAILED));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
