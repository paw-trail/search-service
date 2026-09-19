package com.pawtrail.search.infrastructure.provider.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pawtrail.search.domain.model.IndexedPlace;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * place 의 색인용 조회(GET /internal/places/indexing)가 돌려주는 원소입니다. place v0.1.3 의 16칸입니다.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) 가 필수입니다.
 * place 가 칸을 더해도 여기서 깨지지 않게 하려는 것입니다.
 *
 * 종류 · 상태 · 편의시설은 place 의 열거 이름이 문자열로 옵니다. 그대로 받습니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaceIndexingResponse(UUID placeId,
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
                                    LocalDateTime updatedAt) {

    public IndexedPlace toIndexedPlace() {
        return new IndexedPlace(placeId, name, nameAlias, placeType, addressRoad, addressJibun,
                sidoCode, sigunguName, lat, lon, facilities, status, imageUrl, overview,
                dataBaseDate, updatedAt);
    }
}
