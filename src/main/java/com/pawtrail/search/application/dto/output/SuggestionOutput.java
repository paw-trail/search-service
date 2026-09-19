package com.pawtrail.search.application.dto.output;

import com.pawtrail.search.domain.model.Suggestion;
import java.util.UUID;

/**
 * 자동완성 한 줄입니다(search ㉽).
 *
 * @param placeId     장소 식별자입니다. 고르면 상세로 바로 갑니다.
 * @param name        이름입니다.
 * @param placeType   종류입니다.
 * @param sigunguName 시군구 이름입니다. 이름이 같은 장소를 가르는 데 씁니다. 세종은 null 입니다.
 */
public record SuggestionOutput(UUID placeId, String name, String placeType, String sigunguName) {

    public static SuggestionOutput from(Suggestion suggestion) {
        return new SuggestionOutput(suggestion.placeId(), suggestion.name(), suggestion.placeType(),
                suggestion.sigunguName());
    }
}
