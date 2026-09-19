package com.pawtrail.search.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 장소 한 곳의 후기 평점입니다. review 에서 받아 재색인 때 색인에 담습니다.
 *
 * @param placeId     장소 식별자입니다.
 * @param ratingAvg   평점 평균입니다. 소수 한 자리로 맞춘 값이며 후기가 없으면 null 입니다.
 * @param reviewCount 후기 수입니다.
 */
public record ReviewStats(UUID placeId, BigDecimal ratingAvg, int reviewCount) {

    /**
     * 후기가 없는 장소입니다.
     *
     * review 응답에 없는 장소가 이것입니다.
     * user 의 즐겨찾기 카드도 응답에 없는 장소를 "평점 없음" 으로 읽습니다.
     */
    public static ReviewStats none(UUID placeId) {
        return new ReviewStats(placeId, null, 0);
    }
}
