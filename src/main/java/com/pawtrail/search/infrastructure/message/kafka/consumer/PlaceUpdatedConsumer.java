package com.pawtrail.search.infrastructure.message.kafka.consumer;

import com.pawtrail.common.message.EventEnvelope;
import com.pawtrail.search.application.dto.output.IndexRefreshResult;
import com.pawtrail.search.application.service.SearchIndexService;
import com.pawtrail.search.infrastructure.message.kafka.consumer.dto.PlaceUpdatedMessage;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * place.updated 를 묶어 받아 색인을 다시 읽어 덮어씁니다.
 *
 * 묶어 받는 것은 이 리스너에 batch = "true" 로 적습니다. 설정에 두지 않았습니다.
 * 설정 서버 연결이 optional 이라 설정 서버 없이 뜨면 그 값이 안 내려와
 * 한 건씩 받는 모드로 뜨는데, 목록으로 받게 짠 이 메서드가 변환에 실패해 전부 .dlq 로 갑니다.
 * 묶음 여부는 메서드가 List 로 받는 것과 짝이라 코드에 같이 둡니다.
 *
 * 스프링 부트가 common 의 변환기를 감싼 묶음 변환기와 오류 처리기를 기본 리스너에 붙여 두므로
 * 따로 팩터리를 만들지 않습니다.
 *
 * 실패하면 묶음 전체를 1 · 2 · 4초 간격으로 다시 시도하고, 끝내 안 되면 묶음의 이벤트가 전부
 * place.updated.dlq 로 갑니다. 이벤트엔 placeId 만 있어 잃는 것은 "그 장소들이 다음 재색인까지 옛 값" 입니다.
 *
 * 한 번에 받는 수는 카프카 기본 500 이고, 서비스가 100개씩 나눠 place 를 부릅니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceUpdatedConsumer {

    private static final String TOPIC = "place.updated";

    private final SearchIndexService searchIndexService;

    @KafkaListener(topics = TOPIC, batch = "true")
    public void consume(List<EventEnvelope<PlaceUpdatedMessage>> envelopes) {
        // 같은 장소가 한 묶음에 여러 번 오면 한 번만 읽음 — 어차피 최신 값을 읽으므로 결과가 같음
        List<UUID> placeIds = envelopes.stream()
                .filter(Objects::nonNull)
                .map(EventEnvelope::data)
                .filter(Objects::nonNull)
                .map(PlaceUpdatedMessage::placeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        IndexRefreshResult result = searchIndexService.refresh(placeIds);
        log.info("place.updated {}건 처리: 장소 {}곳 · 받음 {}곳 · 덮어씀 {}곳 · 건너뜀 {}곳",
                envelopes.size(), result.requested(), result.received(), result.written(), result.skipped());
    }
}
