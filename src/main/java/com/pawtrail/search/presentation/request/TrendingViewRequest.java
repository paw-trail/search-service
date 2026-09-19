package com.pawtrail.search.presentation.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 장소 상세를 열었다는 알림입니다(search ㉲).
 *
 * @param placeId 연 장소입니다.
 */
public record TrendingViewRequest(
        @NotNull(message = "장소 식별자는 필수입니다.")
        UUID placeId
) {
}
