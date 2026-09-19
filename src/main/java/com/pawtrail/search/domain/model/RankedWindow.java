package com.pawtrail.search.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * 조회수 순위의 한 구간입니다.
 *
 * 순위의 끝은 장소 식별자로 읽은 수가 아니라 저장소가 준 원래 원소 수로 판단합니다.
 * 식별자로 읽지 못한 원소를 건너뛰면 목록이 짧아지는데, 그 길이로 끝을 판단하면
 * 아래에 멀쩡한 장소가 남아 있어도 순위가 끝난 것으로 잘못 봅니다.
 *
 * @param placeIds 구간의 장소 식별자입니다. 식별자로 읽지 못한 원소는 빠져 있습니다.
 * @param last     이 구간에서 순위가 끝났는지입니다. 저장소가 준 원소가 요청한 수보다 적으면 true 입니다.
 */
public record RankedWindow(List<UUID> placeIds, boolean last) {

    public RankedWindow {
        placeIds = placeIds == null ? List.of() : List.copyOf(placeIds);
    }
}
