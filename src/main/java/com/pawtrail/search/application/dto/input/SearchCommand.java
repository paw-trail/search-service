package com.pawtrail.search.application.dto.input;

import com.pawtrail.search.domain.enums.SearchSort;
import com.pawtrail.search.domain.enums.Verdict;
import com.pawtrail.search.domain.model.SearchFilter;
import java.util.List;
import java.util.UUID;

/**
 * 검색 한 번의 요청입니다.
 *
 * @param filter         색인에서 후보를 좁히는 조건입니다.
 * @param verdictFilter  남길 판정입니다. 비면 판정으로 거르지 않습니다.
 * @param petIds         데려가는 반려동물입니다. 비면 판정 없이 장소 칸만 싣습니다(search ㉢).
 * @param sort           정렬입니다. null 이면 위치가 있을 때 거리순, 없으면 평점순입니다(search ㉻).
 * @param page           0 부터 세는 쪽 번호입니다.
 * @param size           한 쪽의 크기입니다.
 */
public record SearchCommand(SearchFilter filter,
                            List<Verdict> verdictFilter,
                            List<UUID> petIds,
                            SearchSort sort,
                            int page,
                            int size) {

    public SearchCommand {
        verdictFilter = verdictFilter == null ? List.of() : List.copyOf(verdictFilter);
        petIds = petIds == null ? List.of() : petIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    /**
     * 실제로 쓸 정렬입니다. 정렬을 안 줬으면 위치가 있을 때 거리순, 없으면 평점순입니다.
     */
    public SearchSort effectiveSort() {
        if (sort != null) {
            return sort;
        }
        return filter.hasLocation() ? SearchSort.DISTANCE : SearchSort.RATING;
    }
}
