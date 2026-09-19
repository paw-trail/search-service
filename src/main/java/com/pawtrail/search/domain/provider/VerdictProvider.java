package com.pawtrail.search.domain.provider;

import com.pawtrail.search.domain.model.PlaceVerdict;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * verdict 의 목록 판정을 부르는 약속입니다.
 *
 * 반려동물이 없어도 부릅니다(search ㉢).
 * 그러면 verdict 가 반려동물 판정 없이 장소 칸(충돌 여부 · 준비물)만 채워 돌려줍니다.
 *
 * 부르지 못하면 VERDICT_UNAVAILABLE 을 던집니다. 검색은 502 로 답합니다.
 * 판정 없이 카드만 내면 「동반 가능만」 필터가 조용히 틀린 결과를 냅니다.
 */
public interface VerdictProvider {

    /** 한 번에 판정할 수 있는 장소 수입니다. verdict 가 이보다 많으면 400 으로 거절합니다. */
    int MAX_PLACES = 500;

    /**
     * 장소들의 판정을 받습니다.
     *
     * @param placeIds 500곳까지입니다.
     * @param petIds   100마리까지이며 비어 있어도 됩니다.
     * @return 장소 식별자로 찾는 판정입니다.
     */
    Map<UUID, PlaceVerdict> findByPlaceIds(List<UUID> placeIds, List<UUID> petIds);
}
