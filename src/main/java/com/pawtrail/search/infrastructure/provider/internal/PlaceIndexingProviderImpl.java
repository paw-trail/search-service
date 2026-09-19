package com.pawtrail.search.infrastructure.provider.internal;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.search.domain.exception.SearchErrorCode;
import com.pawtrail.search.domain.model.IndexedPlace;
import com.pawtrail.search.domain.provider.PlaceIndexingProvider;
import com.pawtrail.search.infrastructure.provider.internal.dto.PlaceIndexingResponse;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * place 의 색인용 조회를 부릅니다.
 *
 * 부르지 못하거나 응답이 비면 PLACE_UNAVAILABLE 을 던집니다.
 * 빈 목록으로 바꾸지 않습니다. "장소가 없다" 와 "지금 못 받았다" 가 섞이면
 * 색인이 빈 채로 넘어가는데 아무도 모릅니다.
 */
@Slf4j
@Component
public class PlaceIndexingProviderImpl implements PlaceIndexingProvider {

    private static final String BASE_URL = "lb://place-service";

    private static final String PATH = "/internal/places/indexing";

    private final RestClient restClient;

    public PlaceIndexingProviderImpl(@Qualifier("internalRestClientBuilder") RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    @Override
    public List<IndexedPlace> findByIds(List<UUID> placeIds) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        // 식별자를 ids=a&ids=b 로 되풀이해 실음 — place 의 @RequestParam List<UUID> 가 그 꼴을 받음
        return call(uri -> uri.path(PATH).queryParam("ids", placeIds).build(),
                "장소 " + placeIds.size() + "곳");
    }

    @Override
    public List<IndexedPlace> findPageAfter(UUID after, int size) {
        return call(uri -> {
            UriBuilder builder = uri.path(PATH).queryParam("size", size);
            if (after != null) {
                builder.queryParam("after", after);
            }
            return builder.build();
        }, "이어받기 after=" + after + " size=" + size);
    }

    private List<IndexedPlace> call(Function<UriBuilder, URI> uri, String what) {
        CommonApiResponse<List<PlaceIndexingResponse>> response;
        try {
            response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("색인할 장소를 받아오지 못했습니다: {}, reason={}", what, e.getMessage());
            throw new CustomException(SearchErrorCode.PLACE_UNAVAILABLE, e);
        }

        if (response == null || response.getData() == null) {
            log.warn("색인용 조회 응답이 비어 있습니다: {}", what);
            throw new CustomException(SearchErrorCode.PLACE_UNAVAILABLE);
        }

        return response.getData().stream()
                .filter(Objects::nonNull)
                .map(PlaceIndexingResponse::toIndexedPlace)
                .toList();
    }
}
