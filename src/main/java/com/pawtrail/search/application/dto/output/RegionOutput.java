package com.pawtrail.search.application.dto.output;

import java.util.List;

/**
 * 지역 목록의 시도 한 곳입니다(search ㉱).
 *
 * @param sidoCode   시도 코드입니다. 검색의 sidoCode 로 그대로 보냅니다.
 * @param sidoName   시도 이름입니다. place 의 짧은 이름(서울 · 경기 …)입니다.
 * @param placeCount 폐업을 뺀 장소 수입니다.
 * @param sigungus   시군구입니다. 가나다순이며 세종은 빈 목록입니다.
 */
public record RegionOutput(String sidoCode, String sidoName, long placeCount, List<SigunguOutput> sigungus) {

    /**
     * 시군구 한 곳입니다.
     *
     * @param name       시군구 이름입니다. 검색의 sigunguName 으로 그대로 보냅니다.
     * @param placeCount 폐업을 뺀 장소 수입니다.
     */
    public record SigunguOutput(String name, long placeCount) {
    }
}
