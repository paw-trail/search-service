package com.pawtrail.search.infrastructure.provider.internal;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.search.domain.exception.SearchErrorCode;
import com.pawtrail.search.domain.model.ReviewStats;
import com.pawtrail.search.domain.provider.ReviewStatsProvider;
import com.pawtrail.search.infrastructure.provider.internal.dto.ReviewStatResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * review 의 평점 조회를 부릅니다.
 *
 * user 의 ReviewProviderImpl 과 같은 경로 · 같은 모양이지만 실패 처리가 다릅니다.
 * user 는 못 받으면 빈 결과로 넘어갑니다. 카드의 별점 한 칸이라 없어도 화면이 서기 때문입니다.
 * 여기는 받은 값으로 색인을 덮어쓰므로, 못 받은 것을 빈 결과로 읽으면 평점이 전부 지워집니다.
 * 그래서 REVIEW_UNAVAILABLE 을 던지고 재색인이 평점을 그대로 둡니다.
 */
@Slf4j
@Component
public class ReviewStatsProviderImpl implements ReviewStatsProvider {

    private static final String BASE_URL = "lb://review-service";

    private final RestClient restClient;

    public ReviewStatsProviderImpl(@Qualifier("internalRestClientBuilder") RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    @Override
    public Map<UUID, ReviewStats> findByPlaceIds(List<UUID> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }

        CommonApiResponse<List<ReviewStatResponse>> response;
        try {
            // 식별자를 placeIds=a&placeIds=b 로 되풀이해 실음 — user 가 부르는 꼴과 같음
            response = restClient.get()
                    .uri(builder -> builder.path("/internal/reviews/stats")
                            .queryParam("placeIds", placeIds)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("후기 평점을 받아오지 못했습니다: 장소 {}곳, reason={}", placeIds.size(), e.getMessage());
            throw new CustomException(SearchErrorCode.REVIEW_UNAVAILABLE, e);
        }

        if (response == null || response.getData() == null) {
            log.warn("후기 평점 응답이 비어 있습니다: 장소 {}곳", placeIds.size());
            throw new CustomException(SearchErrorCode.REVIEW_UNAVAILABLE);
        }

        Map<UUID, ReviewStats> stats = new HashMap<>();
        for (ReviewStatResponse stat : response.getData()) {
            if (stat != null && stat.placeId() != null) {
                stats.put(stat.placeId(), stat.toReviewStats());
            }
        }
        return stats;
    }
}
