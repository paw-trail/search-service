package com.pawtrail.search.infrastructure.provider.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.search.domain.exception.SearchErrorCode;
import com.pawtrail.search.domain.model.IndexedPlace;
import com.pawtrail.search.infrastructure.provider.internal.dto.PlaceIndexingResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * place 색인용 조회의 응답을 두 자리에서 어떻게 다루는지 검사합니다.
 *
 * 이어받기는 받은 수로 끝을 판단하므로 원소를 거르면 안 되고,
 * 몇 곳을 다시 읽는 자리는 거를 수 있습니다. 호출 없이 응답을 다루는 두 메서드를 직접 부릅니다.
 */
class PlaceIndexingProviderImplTest {

    private static final UUID PLACE_A = UUID.fromString("01a09015-85fa-7f60-b85c-030b6d0f3f54");

    @Test
    @DisplayName("이어받기 쪽에 빈 원소가 있으면 거르지 않고 PLACE_UNAVAILABLE 로 실패한다")
    void 이어받기는_거르지_않는다() {
        List<PlaceIndexingResponse> withNull = Arrays.asList(item(PLACE_A), null);
        List<PlaceIndexingResponse> withoutId = List.of(item(PLACE_A), item(null));

        // 걸러서 쪽이 줄면 마지막 쪽으로 오판해 뒤를 안 읽은 채 재색인이 성공으로 끝남
        assertThatThrownBy(() -> PlaceIndexingProviderImpl.toPage(withNull, "시험"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SearchErrorCode.PLACE_UNAVAILABLE);
        assertThatThrownBy(() -> PlaceIndexingProviderImpl.toPage(withoutId, "시험"))
                .isInstanceOf(CustomException.class);
        assertThat(PlaceIndexingProviderImpl.toPage(List.of(item(PLACE_A)), "시험"))
                .extracting(IndexedPlace::placeId)
                .containsExactly(PLACE_A);
    }

    @Test
    @DisplayName("몇 곳을 다시 읽은 결과는 빈 원소를 걸러 없는 장소처럼 다룬다")
    void 다시_읽기는_거른다() {
        assertThat(PlaceIndexingProviderImpl.toPlaces(Arrays.asList(item(PLACE_A), null)))
                .extracting(IndexedPlace::placeId)
                .containsExactly(PLACE_A);
    }

    private static PlaceIndexingResponse item(UUID placeId) {
        return new PlaceIndexingResponse(placeId, "여의도한강공원", List.of(), "PARK", "서울특별시 영등포구 여의동로 330",
                null, "11", "영등포구", new BigDecimal("37.5285"), new BigDecimal("126.9327"), List.of(), "ACTIVE",
                null, null, null, LocalDateTime.of(2026, 9, 11, 19, 48, 47));
    }
}
