package com.pawtrail.search.domain.model;

import java.util.UUID;

/**
 * 자동완성 한 줄입니다. 판정은 붙이지 않습니다 — 글자를 칠 때마다 부르기 때문입니다.
 *
 * @param placeId     장소 식별자입니다.
 * @param name        이름입니다.
 * @param placeType   종류입니다.
 * @param sigunguName 시군구 이름입니다. 같은 이름의 장소를 가를 때 씁니다. 세종은 null 입니다.
 */
public record Suggestion(UUID placeId, String name, String placeType, String sigunguName) {
}
