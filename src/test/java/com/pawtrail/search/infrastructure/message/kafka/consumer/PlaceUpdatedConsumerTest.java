package com.pawtrail.search.infrastructure.message.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.kafka.listener.BatchListenerFailedException;

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
    @DisplayName("장소 식별자가 없는 이벤트를 만나면 그 앞까지 처리하고 그 자리를 담아 실패한다")
    void 잘못된_이벤트는_그_자리에서_실패() {
        when(searchIndexService.refresh(anyList())).thenReturn(IndexRefreshResult.EMPTY);
        EventEnvelope<PlaceUpdatedMessage> noData = new EventEnvelope<>(
                EventEnvelope.generateUuidV7(), "place.updated", LocalDateTime.now(), "Place", null, null);

        // 조용히 버리면 처리된 것으로 넘어가 DLQ 에도 안 남음
        // 자리를 담아야 오류 처리기가 앞은 넘기고 이 한 건만 재시도 뒤 .dlq 로 보냄
        assertThatThrownBy(() -> consumer.consume(Arrays.asList(envelope(PLACE_A), envelope(PLACE_B), noData, envelope(PLACE_A))))
                .isInstanceOf(BatchListenerFailedException.class)
                .extracting(e -> ((BatchListenerFailedException) e).getIndex())
                .isEqualTo(2);

        verify(searchIndexService).refresh(List.of(PLACE_A, PLACE_B));
    }

    @Test
    @DisplayName("첫 이벤트부터 잘못됐으면 아무것도 읽지 않고 0번째로 실패한다")
    void 첫_이벤트가_잘못되면_바로_실패() {
        assertThatThrownBy(() -> consumer.consume(Arrays.asList(null, envelope(PLACE_A))))
                .isInstanceOf(BatchListenerFailedException.class)
                .extracting(e -> ((BatchListenerFailedException) e).getIndex())
                .isEqualTo(0);

        verifyNoInteractions(searchIndexService);
    }

    private static EventEnvelope<PlaceUpdatedMessage> envelope(UUID placeId) {
        return new EventEnvelope<>(EventEnvelope.generateUuidV7(), "place.updated", LocalDateTime.now(),
                "Place", placeId == null ? null : placeId.toString(), new PlaceUpdatedMessage(placeId));
    }
}
