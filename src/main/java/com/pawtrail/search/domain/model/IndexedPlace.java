package com.pawtrail.search.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 색인에 담을 장소 한 곳입니다. place 의 색인용 조회가 준 값을 그대로 옮겨 담습니다.
 *
 * 평점은 여기 없습니다.
 * 평점은 review 에서 오고 매일 재색인 때 따로 채웁니다.
 * 이벤트로 장소를 다시 읽을 때 평점을 건드리지 않으려고 나눴습니다.
 *
 * 종류 · 상태 · 편의시설은 place 의 코드 문자열 그대로 담습니다.
 * 그 값의 주인은 place 이고, search 가 목록을 따로 들고 있으면 place 가 값을 더할 때 둘이 어긋납니다.
 *
 * @param placeId        장소 식별자입니다.
 * @param name           대표 소스의 이름입니다.
 * @param nameAlias      괄호 별칭입니다. 없으면 빈 목록입니다.
 * @param placeType      서비스 카테고리 코드입니다.
 * @param addressRoad    도로명 주소입니다.
 * @param addressJibun   지번 주소입니다.
 * @param sidoCode       시도 코드(법정동 두 자리)입니다.
 * @param sigunguName    시군구 이름입니다. 세종은 비어 있습니다.
 * @param lat            위도입니다.
 * @param lon            경도입니다.
 * @param facilities     편의시설 코드입니다. 없으면 빈 목록입니다.
 * @param status         영업 상태 코드입니다.
 * @param imageUrl       대표 사진입니다.
 * @param overview       소개문입니다. 칸으로 담지 않고 낱말 검색 글에만 녹입니다.
 * @param dataBaseDate   소스가 밝힌 데이터 기준일입니다.
 * @param placeUpdatedAt place 가 이 장소를 마지막으로 고친 시각입니다. 덮어쓸지 가르는 기준입니다.
 */
public record IndexedPlace(UUID placeId,
                           String name,
                           List<String> nameAlias,
                           String placeType,
                           String addressRoad,
                           String addressJibun,
                           String sidoCode,
                           String sigunguName,
                           BigDecimal lat,
                           BigDecimal lon,
                           List<String> facilities,
                           String status,
                           String imageUrl,
                           String overview,
                           LocalDate dataBaseDate,
                           LocalDateTime placeUpdatedAt) {

    public IndexedPlace {
        nameAlias = withoutNulls(nameAlias);
        facilities = withoutNulls(facilities);
    }

    /**
     * 색인에 넣을 수 있는지 봅니다.
     *
     * 표의 NOT NULL 칸이 하나라도 비면 넣지 않습니다.
     * place 가 비워 보낼 일은 없으나, 그런 한 곳 때문에 묶음 전체가 실패해 .dlq 로 가는 것을 막습니다.
     */
    public boolean isIndexable() {
        return placeId != null
                && name != null && !name.isBlank()
                && placeType != null
                && lat != null && lon != null
                && status != null
                && placeUpdatedAt != null;
    }

    /**
     * 낱말 검색에 쓸 글을 만듭니다. search_text 가 이 글의 tsvector 입니다.
     *
     * 이름 · 별칭 · 도로명 · 지번 · 소개문을 이어 붙입니다.
     * 이름 · 별칭 · 주소는 부분 일치로도 따로 찾지만, 소개문과 같은 낱말 앞부분 검색에도 걸리게 함께 담습니다.
     */
    public String searchSource() {
        return Stream.of(Stream.of(name), nameAlias.stream(), Stream.of(addressRoad, addressJibun, overview))
                .flatMap(part -> part)
                .filter(Objects::nonNull)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining(" "));
    }

    // 빈 목록은 null 이 아니라 [] 로 둠
    // 표의 배열 칸이 NOT NULL 이라 null 을 넘기면 넣기가 실패함
    private static List<String> withoutNulls(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).toList();
    }
}
