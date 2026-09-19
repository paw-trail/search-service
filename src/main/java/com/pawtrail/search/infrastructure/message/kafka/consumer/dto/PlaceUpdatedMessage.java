package com.pawtrail.search.infrastructure.message.kafka.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * place.updated 의 payload 입니다. place 가 식별자 하나만 실어 보냅니다.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) 가 필수입니다.
 * place 가 칸을 더해도 여기서 깨지지 않게 하려는 것입니다.
 *
 * @param placeId 바뀐 장소입니다. 값은 place 에서 다시 읽습니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaceUpdatedMessage(UUID placeId) {
}
