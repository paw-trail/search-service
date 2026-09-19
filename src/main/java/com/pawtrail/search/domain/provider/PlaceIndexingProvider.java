package com.pawtrail.search.domain.provider;

import com.pawtrail.search.domain.model.IndexedPlace;
import java.util.List;
import java.util.UUID;

/**
 * place 에서 색인할 장소를 받아 오는 약속입니다. place 의 색인용 조회를 부릅니다.
 *
 * 부르지 못하면 PLACE_UNAVAILABLE 을 던집니다.
 * 빈 목록과 섞지 않습니다. 못 받은 것을 "장소가 없다" 로 읽으면 색인이 비어 있는 채로 넘어갑니다.
 */
public interface PlaceIndexingProvider {

    /** 식별자로 한 번에 받을 수 있는 수입니다. place 가 이보다 많으면 400 으로 거절합니다. */
    int MAX_IDS = 100;

    /** 이어받기 한 쪽의 최대 크기입니다. place 가 이 범위로 맞춥니다. */
    int MAX_PAGE_SIZE = 500;

    /**
     * 그 장소들을 받습니다. place 에 없는 식별자는 빠질 뿐입니다.
     *
     * @param placeIds 100개까지입니다.
     */
    List<IndexedPlace> findByIds(List<UUID> placeIds);

    /**
     * 장소를 id 순으로 이어서 받습니다. 전량 재색인이 씁니다.
     *
     * @param after 이 id 다음부터입니다. null 이면 처음부터입니다.
     * @param size  한 쪽의 크기입니다. 받은 수가 이보다 적으면 끝입니다.
     *              그래서 원소를 거르지 않습니다 — 깨진 원소가 있으면 PLACE_UNAVAILABLE 입니다.
     */
    List<IndexedPlace> findPageAfter(UUID after, int size);
}
