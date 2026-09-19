package com.pawtrail.search.infrastructure.provider.internal;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.search.domain.exception.SearchErrorCode;
import com.pawtrail.search.domain.model.PlaceVerdict;
import com.pawtrail.search.domain.provider.VerdictProvider;
import com.pawtrail.search.infrastructure.provider.internal.dto.VerdictBatchResponse;
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
 * verdict 의 목록 판정을 부릅니다.
 *
 * verdict 는 부르는 사람의 X-User-Id · X-User-Role 이 있어야 판정합니다. 반려동물의 주인을 확인하기 때문입니다.
 * 공통 모듈의 내부 호출 빌더가 지금 요청의 두 헤더를 그대로 실어 보냅니다.
 * 그래서 이 호출은 사용자 요청 안에서만 됩니다 — 스케줄이나 재색인에서는 부르지 않습니다.
 *
 * 못 부르면 VERDICT_UNAVAILABLE(502) 입니다. 빈 판정으로 넘어가지 않습니다.
 */
@Slf4j
@Component
public class VerdictProviderImpl implements VerdictProvider {

    private static final String BASE_URL = "lb://verdict-service";

    private final RestClient restClient;

    public VerdictProviderImpl(@Qualifier("internalRestClientBuilder") RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    @Override
    public Map<UUID, PlaceVerdict> findByPlaceIds(List<UUID> placeIds, List<UUID> petIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }

        CommonApiResponse<VerdictBatchResponse> response;
        try {
            response = restClient.post()
                    .uri("/internal/verdicts/batch")
                    .body(Map.of("placeIds", placeIds, "petIds", petIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("동반 판정을 받아오지 못했습니다: 장소 {}곳 · 반려동물 {}마리, reason={}",
                    placeIds.size(), petIds.size(), e.getMessage());
            throw new CustomException(SearchErrorCode.VERDICT_UNAVAILABLE, e);
        }

        if (response == null || response.getData() == null || response.getData().results() == null) {
            log.warn("동반 판정 응답이 비어 있습니다: 장소 {}곳", placeIds.size());
            throw new CustomException(SearchErrorCode.VERDICT_UNAVAILABLE);
        }

        Map<UUID, PlaceVerdict> verdicts = new HashMap<>();
        for (VerdictBatchResponse.Result result : response.getData().results()) {
            if (result != null && result.placeId() != null) {
                verdicts.put(result.placeId(), result.toPlaceVerdict());
            }
        }
        return verdicts;
    }
}
