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
        return toPlaces(call(uri -> uri.path(PATH).queryParam("ids", placeIds).build(),
                "장소 " + placeIds.size() + "곳"));
    }

    @Override
    public List<IndexedPlace> findPageAfter(UUID after, int size) {
        String what = "이어받기 after=" + after + " size=" + size;
        return toPage(call(uri -> {
            UriBuilder builder = uri.path(PATH).queryParam("size", size);
            if (after != null) {
                builder.queryParam("after", after);
            }
            return builder.build();
        }, what), what);
    }

    /**
     * 몇 곳을 다시 읽은 결과입니다. 깨진 원소는 없는 장소처럼 거릅니다.
     *
     * 받은 수로 무엇을 판단하지 않는 자리라 걸러도 해가 없습니다.
     */
    static List<IndexedPlace> toPlaces(List<PlaceIndexingResponse> items) {
        return items.stream()
                .filter(Objects::nonNull)
                .map(PlaceIndexingResponse::toIndexedPlace)
                .toList();
    }

    /**
     * 이어받기 한 쪽입니다. 원소를 거르지 않고, 깨진 원소가 있으면 PLACE_UNAVAILABLE 로 실패합니다.
     *
     * 재색인은 받은 수가 쪽 크기보다 적으면 끝으로 봅니다.
     * 깨진 원소를 걸러 500 이 499 가 되면 마지막 쪽으로 오판해, 뒤를 안 읽은 채 재색인이 성공으로 끝납니다.
     * 식별자가 없는 원소는 다음 쪽을 물을 기준도 못 되므로 같은 실패로 봅니다.
     */
    static List<IndexedPlace> toPage(List<PlaceIndexingResponse> items, String what) {
        boolean broken = items.stream().anyMatch(item -> item == null || item.placeId() == null);
        if (broken) {
            log.warn("색인용 이어받기 응답에 비어 있는 원소가 있습니다: {} · {}개 중", what, items.size());
            throw new CustomException(SearchErrorCode.PLACE_UNAVAILABLE);
        }
        return items.stream().map(PlaceIndexingResponse::toIndexedPlace).toList();
    }

    private List<PlaceIndexingResponse> call(Function<UriBuilder, URI> uri, String what) {
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

        return response.getData();
    }
}
