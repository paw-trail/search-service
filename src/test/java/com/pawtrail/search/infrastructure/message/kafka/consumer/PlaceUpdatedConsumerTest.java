package com.pawtrail.search.infrastructure.message.kafka.consumer;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawtrail.common.message.EventEnvelope;
import com.pawtrail.search.application.dto.output.IndexRefreshResult;
import com.pawtrail.search.application.service.SearchIndexService;
import com.pawtrail.search.infrastructure.message.kafka.consumer.dto.PlaceUpdatedMessage;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 묶어 받은 place.updated 에서 다시 읽을 장소를 추리는 규칙을 검사합니다.
 *
 * 카프카는 띄우지 않고 리스너 메서드를 직접 부릅니다.
 * 받은 글을 봉투로 바꾸는 일은 common 의 변환기와 스프링이 합니다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceUpdatedConsumerTest {

    private static final UUID PLACE_A = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");
    private static final UUID PLACE_B = UUID.fromString("bbbbbbbb-0000-7000-8000-000000000002");

    @Mock
    private SearchIndexService searchIndexService;

    @InjectMocks
    private PlaceUpdatedConsumer consumer;

    @Test
    @DisplayName("한 묶음에 같은 장소가 여러 번 와도 한 번만 다시 읽는다")
    void 같은_장소는_한_번() {
        when(searchIndexService.refresh(anyList())).thenReturn(new IndexRefreshResult(2, 2, 0, 2));

        consumer.consume(List.of(envelope(PLACE_A), envelope(PLACE_B), envelope(PLACE_A)));

        verify(searchIndexService).refresh(List.of(PLACE_A, PLACE_B));
    }

    @Test
    @DisplayName("payload 나 placeId 가 빈 이벤트는 거른다")
    void 빈_이벤트는_거른다() {
        when(searchIndexService.refresh(anyList())).thenReturn(IndexRefreshResult.EMPTY);
        EventEnvelope<PlaceUpdatedMessage> noData = new EventEnvelope<>(
                EventEnvelope.generateUuidV7(), "place.updated", LocalDateTime.now(), "Place", null, null);

        consumer.consume(Arrays.asList(envelope(PLACE_A), noData, envelope(null), null));

        verify(searchIndexService).refresh(List.of(PLACE_A));
    }

    private static EventEnvelope<PlaceUpdatedMessage> envelope(UUID placeId) {
        return new EventEnvelope<>(EventEnvelope.generateUuidV7(), "place.updated", LocalDateTime.now(),
                "Place", placeId == null ? null : placeId.toString(), new PlaceUpdatedMessage(placeId));
    }
}
