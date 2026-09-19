package com.pawtrail.search.domain.model;

import java.util.List;

/**
 * 색인에서 후보를 좁히는 조건입니다. 판정 조건은 여기 없습니다 — 판정은 verdict 가 합니다.
 *
 * @param words       검색어를 띄어쓰기로 나눈 낱말입니다. 낱말마다 모두 맞아야 합니다.
 * @param sidoCode    시도 코드입니다.
 * @param sigunguName 시군구 이름입니다. 시도 코드와 함께만 씁니다.
 * @param lat         위도입니다. 경도와 함께만 씁니다.
 * @param lon         경도입니다.
 * @param radiusM     반경(미터)입니다. 위치가 있을 때만 씁니다.
 * @param placeTypes  종류입니다. 비면 전부입니다.
 * @param facilities  편의시설입니다. 고른 것을 모두 갖춘 곳만 남습니다.
 */
public record SearchFilter(List<String> words,
                           String sidoCode,
                           String sigunguName,
                           Double lat,
                           Double lon,
                           Integer radiusM,
                           List<String> placeTypes,
                           List<String> facilities) {

    public SearchFilter {
        words = words == null ? List.of() : List.copyOf(words);
        placeTypes = placeTypes == null ? List.of() : List.copyOf(placeTypes);
        facilities = facilities == null ? List.of() : List.copyOf(facilities);
    }

    public boolean hasLocation() {
        return lat != null && lon != null;
    }
}
