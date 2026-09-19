package com.pawtrail.search.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 장소 조회수입니다. 「인기 급상승」 과 인기순 정렬이 읽습니다.
 *
 * 데이터베이스가 아니라 Redis 정렬 집합에만 둡니다.
 * 기간을 자르지 않은 누적 조회수입니다(명세 2026.9.3). 실사용자가 없어 순위가 거의 안 움직이므로
 * 날짜별 열쇠를 합산하는 복잡도를 사지 않았습니다.
 *
 * 열쇠는 전국 하나와 시도마다 하나입니다(search ㉧). 조회 한 번에 둘 다 하나씩 올립니다.
 */
public interface TrendingStore {

    /**
     * 조회수를 하나 올립니다. 장소 상세를 열 때 화면이 알려 옵니다.
     *
     * @param sidoCode 비어 있으면 전국 열쇠만 올립니다.
     */
    void increment(UUID placeId, String sidoCode);

    /**
     * 조회수가 많은 차례로 장소를 돌려줍니다.
     *
     * @param sidoCode 비어 있으면 전국입니다.
     */
    List<UUID> top(String sidoCode, int size);

    /**
     * 장소들의 전국 조회수입니다. 인기순 정렬이 씁니다.
     *
     * @return 한 번도 조회되지 않은 장소는 키가 없습니다.
     */
    Map<UUID, Double> scoresOf(Collection<UUID> placeIds);
}
