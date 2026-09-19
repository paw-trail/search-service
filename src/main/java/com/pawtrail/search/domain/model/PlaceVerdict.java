package com.pawtrail.search.domain.model;

import com.pawtrail.search.domain.enums.Verdict;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 장소 한 곳의 목록 판정입니다. verdict 의 목록 판정 원소를 그대로 옮긴 것입니다.
 *
 * @param placeId         장소 식별자입니다.
 * @param hasConflict     소스끼리 조건이 엇갈리는지입니다.
 * @param verdicts        반려동물별 판정입니다. 반려동물 없이 불렀으면 빈 목록입니다.
 * @param evidenceSummary 카드 한 줄 근거입니다. 반려동물 없이 불렀으면 null 입니다.
 * @param requiredItems   준비물입니다.
 */
public record PlaceVerdict(UUID placeId,
                           boolean hasConflict,
                           List<PetVerdict> verdicts,
                           String evidenceSummary,
                           List<String> requiredItems) {

    public PlaceVerdict {
        verdicts = verdicts == null ? List.of() : verdicts.stream().filter(Objects::nonNull).toList();
        requiredItems = requiredItems == null ? List.of() : requiredItems.stream().filter(Objects::nonNull).toList();
    }

    /**
     * 판정을 못 받은 장소입니다. verdict 가 그 장소를 빠뜨렸을 때만 씁니다.
     */
    public static PlaceVerdict empty(UUID placeId) {
        return new PlaceVerdict(placeId, false, List.of(), null, List.of());
    }

    /**
     * 장소의 판정입니다. 데려가는 반려동물 가운데 가장 막히는 마리의 판정입니다(search ㉤).
     *
     * 「동반 가능만」은 모든 마리가 가능한 곳이 됩니다.
     *
     * @return 반려동물 없이 불렀으면 null 입니다.
     */
    public Verdict placeVerdict() {
        return verdicts.stream()
                .map(PetVerdict::verdict)
                .filter(Objects::nonNull)
                .reduce(Verdict::stricter)
                .orElse(null);
    }
}
