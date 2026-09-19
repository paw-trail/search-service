package com.pawtrail.search.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 검색 카드에 쓰는 색인 값입니다. 판정은 여기 없고 verdict 에서 따로 받아 붙입니다.
 *
 * @param placeId      장소 식별자입니다.
 * @param name         이름입니다.
 * @param placeType    종류입니다.
 * @param address      표시 주소입니다. 도로명, 없으면 지번입니다 — place 상세와 같은 규칙입니다.
 * @param imageUrl     대표 사진입니다.
 * @param distanceM    검색한 위치에서의 거리(미터)입니다. 위치를 안 보냈으면 null 입니다.
 * @param ratingAvg    평점 평균입니다. 후기가 없으면 null 입니다.
 * @param reviewCount  후기 수입니다.
 * @param dataBaseDate 소스가 밝힌 데이터 기준일입니다.
 */
public record IndexedCard(UUID placeId,
                          String name,
                          String placeType,
                          String address,
                          String imageUrl,
                          Long distanceM,
                          BigDecimal ratingAvg,
                          int reviewCount,
                          LocalDate dataBaseDate) {
}
