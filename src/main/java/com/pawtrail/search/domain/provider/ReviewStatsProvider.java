package com.pawtrail.search.domain.provider;

import com.pawtrail.search.domain.model.ReviewStats;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * review 에서 장소들의 평점을 받아 오는 약속입니다.
 *
 * 부르지 못하면 REVIEW_UNAVAILABLE 을 던집니다.
 * 빈 결과와 섞지 않습니다. 못 받은 것을 "후기 없음" 으로 읽으면
 * 멀쩡한 평점이 재색인 한 번에 전부 지워집니다.
 * 그래서 user 의 평점 호출(실패하면 빈 결과로 넘어감)과 여기서 갈립니다.
 */
public interface ReviewStatsProvider {

    /** 한 번에 물을 수 있는 수입니다. 식별자가 주소에 실려 place 와 같은 까닭으로 100 입니다. */
    int MAX_IDS = 100;

    /**
     * 장소들의 평점을 받습니다.
     *
     * 응답에 없는 장소는 결과에 키가 없습니다. 후기가 없는 것으로 봅니다.
     *
     * @param placeIds 100개까지입니다.
     */
    Map<UUID, ReviewStats> findByPlaceIds(List<UUID> placeIds);
}
