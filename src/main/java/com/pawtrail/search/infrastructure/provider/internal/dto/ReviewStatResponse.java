package com.pawtrail.search.infrastructure.provider.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pawtrail.search.domain.model.ReviewStats;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * review 의 GET /internal/reviews/stats?placeIds= 가 돌려주는 원소입니다.
 *
 * user 의 즐겨찾기 카드가 먼저 정한 모양을 그대로 받습니다.
 * @JsonIgnoreProperties(ignoreUnknown = true) 가 필수입니다. review 가 칸을 더해도 여기서 깨지지 않게 하려는 것입니다.
 *
 * @param placeId     장소 식별자입니다.
 * @param ratingAvg   평점 평균입니다. 후기가 없으면 null 입니다.
 * @param reviewCount 후기 수입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewStatResponse(UUID placeId, Double ratingAvg, Integer reviewCount) {

    /**
     * 색인에 담을 모양으로 바꿉니다.
     *
     * 평점은 소수 한 자리로 반올림합니다. 색인 칸이 numeric(2,1) 입니다.
     */
    public ReviewStats toReviewStats() {
        BigDecimal rating = ratingAvg == null ? null : BigDecimal.valueOf(ratingAvg).setScale(1, RoundingMode.HALF_UP);
        return new ReviewStats(placeId, rating, reviewCount == null ? 0 : reviewCount);
    }
}
