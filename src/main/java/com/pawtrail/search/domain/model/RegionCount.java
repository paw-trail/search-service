package com.pawtrail.search.domain.model;

/**
 * 시도 · 시군구마다 폐업을 뺀 장소 수입니다. 지역 목록이 시도로 묶습니다.
 *
 * @param sidoCode    시도 코드입니다.
 * @param sigunguName 시군구 이름입니다. 세종처럼 시군구가 없으면 null 입니다.
 * @param placeCount  장소 수입니다.
 */
public record RegionCount(String sidoCode, String sigunguName, long placeCount) {
}
