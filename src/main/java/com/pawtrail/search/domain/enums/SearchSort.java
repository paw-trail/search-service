package com.pawtrail.search.domain.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * 검색 결과를 줄 세우는 기준입니다.
 *
 * 화면이 보내는 값은 distance · rating · popular 셋입니다.
 * NAME 은 화면이 보내지 않고, 정렬을 안 줬는데 위치도 없을 때 평점이 같은 곳끼리의 차례로만 씁니다.
 */
public enum SearchSort {

    // 가까운 곳부터 — 위도 · 경도가 있어야 함
    DISTANCE,

    // 평점이 높은 곳부터 — 평점이 없는 곳은 뒤로
    RATING,

    // 누적 조회수가 많은 곳부터 — 조회수가 Redis 에만 있어 후보를 다 뽑아 메모리에서 줄 세움
    POPULAR,

    // 이름 가나다순
    NAME;

    /**
     * 화면이 보낸 값을 읽습니다. 대소문자는 가리지 않습니다.
     *
     * @return 모르는 값이면 비어 있습니다. 부르는 쪽이 400 으로 답합니다.
     */
    public static Optional<SearchSort> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "distance" -> Optional.of(DISTANCE);
            case "rating" -> Optional.of(RATING);
            case "popular" -> Optional.of(POPULAR);
            default -> Optional.empty();
        };
    }
}
