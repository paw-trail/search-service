package com.pawtrail.search.application.dto.output;

import com.pawtrail.search.domain.model.IndexedCard;
import com.pawtrail.search.domain.model.PetVerdict;
import com.pawtrail.search.domain.model.PlaceVerdict;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 검색 결과 카드 한 장입니다. 명세의 검색 응답 원소입니다.
 *
 * 이름 · 주소 · 좌표 · 사진 · 평점은 색인에서, 판정 · 충돌 · 한 줄 근거 · 준비물은 verdict 에서 옵니다.
 * place 는 부르지 않습니다. 카드에 쓰는 값이 전부 색인에 옮겨져 있습니다.
 *
 * @param placeId         장소 식별자입니다.
 * @param name            이름입니다.
 * @param placeType       종류입니다. 동물병원(VET)이면 화면이 판정 배지를 그리지 않습니다.
 * @param address         표시 주소입니다.
 * @param lat             장소 위도입니다. 웹 화면은 이 좌표와 브라우저가 받은 내 위치로 거리를 직접 계산합니다.
 *                        내 위치를 서버로 보내지 않기 위해서입니다.
 * @param lon             장소 경도입니다.
 * @param imageUrl        대표 사진입니다.
 * @param distanceM       검색한 위치에서의 거리(미터)입니다. 위치를 안 보냈으면 null 입니다.
 * @param verdicts        반려동물별 판정입니다. 반려동물 없이 검색했으면 빈 목록입니다.
 * @param hasConflict     소스끼리 조건이 엇갈리는지입니다.
 * @param evidenceSummary 카드 한 줄 근거입니다.
 * @param requiredItems   준비물입니다.
 * @param ratingAvg       평점 평균입니다.
 * @param reviewCount     후기 수입니다.
 * @param dataBaseDate    데이터 기준일입니다.
 */
public record SearchCardOutput(UUID placeId,
                               String name,
                               String placeType,
                               String address,
                               BigDecimal lat,
                               BigDecimal lon,
                               String imageUrl,
                               Long distanceM,
                               List<PetVerdict> verdicts,
                               boolean hasConflict,
                               String evidenceSummary,
                               List<String> requiredItems,
                               BigDecimal ratingAvg,
                               int reviewCount,
                               LocalDate dataBaseDate) {

    public static SearchCardOutput of(IndexedCard card, PlaceVerdict verdict) {
        return new SearchCardOutput(card.placeId(), card.name(), card.placeType(), card.address(),
                card.lat(), card.lon(), card.imageUrl(), card.distanceM(),
                verdict.verdicts(), verdict.hasConflict(), verdict.evidenceSummary(), verdict.requiredItems(),
                card.ratingAvg(), card.reviewCount(), card.dataBaseDate());
    }
}
