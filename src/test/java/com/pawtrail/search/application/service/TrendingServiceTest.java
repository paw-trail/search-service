package com.pawtrail.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.search.application.dto.output.SearchCardOutput;
import com.pawtrail.search.domain.model.IndexedCard;
import com.pawtrail.search.domain.provider.VerdictProvider;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import com.pawtrail.search.domain.repository.TrendingStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 인기 급상승과 조회 알림을 검사합니다.
 *
 * 순위는 색인 밖(Redis)에 있어 폐업했거나 색인에 없는 장소가 섞여 올 수 있습니다.
 * 그런 곳을 빼고도 요청한 수를 채우는지, 판정은 보여 줄 곳만 묻는지를 봅니다.
 */
@ExtendWith(MockitoExtension.class)
class TrendingServiceTest {

    private static final UUID FIRST = UUID.fromString("01a09015-0000-7000-8000-000000000001");
    private static final UUID CLOSED = UUID.fromString("01a09015-0000-7000-8000-000000000002");
    private static final UUID THIRD = UUID.fromString("01a09015-0000-7000-8000-000000000003");
    private static final UUID MALTESE = UUID.fromString("01a0726a-1111-7000-8000-000000000001");

    @Mock
    private TrendingStore trendingStore;

    @Mock
    private SearchIndexRepository searchIndexRepository;

    @Mock
    private VerdictProvider verdictProvider;

    @InjectMocks
    private TrendingService trendingService;

    @Test
    @DisplayName("순위를 넉넉히 읽어 폐업한 곳을 빼고 요청한 수만큼, 판정은 그 곳들만 묻는다")
    void 폐업을_빼고_채운다() {
        when(trendingStore.top("11", 0, 4)).thenReturn(List.of(FIRST, CLOSED, THIRD));
        when(searchIndexRepository.findCards(any(), any())).thenReturn(Map.of(FIRST, card(FIRST), THIRD, card(THIRD)));
        when(verdictProvider.findByPlaceIds(List.of(FIRST, THIRD), List.of(MALTESE))).thenReturn(Map.of());

        List<SearchCardOutput> cards = trendingService.trending("11", 2, List.of(MALTESE));

        assertThat(cards).extracting(SearchCardOutput::placeId).containsExactly(FIRST, THIRD);
    }

    @Test
    @DisplayName("첫 구간이 폐업 · 색인 밖으로 가득하면 다음 구간을 이어 읽어 채운다")
    void 다음_구간을_이어_읽는다() {
        UUID fourth = UUID.fromString("01a09015-0000-7000-8000-000000000004");
        UUID fifth = UUID.fromString("01a09015-0000-7000-8000-000000000005");
        UUID gone = UUID.fromString("01a09015-0000-7000-8000-000000000009");
        when(trendingStore.top(null, 0, 2)).thenReturn(List.of(CLOSED, gone));
        when(trendingStore.top(null, 2, 2)).thenReturn(List.of(fourth, fifth));
        when(searchIndexRepository.findCards(any(), any()))
                .thenReturn(Map.of())
                .thenReturn(Map.of(fourth, card(fourth), fifth, card(fifth)));
        when(verdictProvider.findByPlaceIds(List.of(fourth), List.of())).thenReturn(Map.of());

        List<SearchCardOutput> cards = trendingService.trending(null, 1, null);

        assertThat(cards).extracting(SearchCardOutput::placeId).containsExactly(fourth);
    }

    @Test
    @DisplayName("순위가 끝나면 모자라도 거기서 멈춘다")
    void 순위가_끝나면_멈춘다() {
        when(trendingStore.top(null, 0, 10)).thenReturn(List.of(FIRST));
        when(searchIndexRepository.findCards(any(), any())).thenReturn(Map.of(FIRST, card(FIRST)));
        when(verdictProvider.findByPlaceIds(List.of(FIRST), List.of())).thenReturn(Map.of());

        assertThat(trendingService.trending(null, 5, null)).hasSize(1);
        verify(trendingStore, never()).top(any(), eq(10), anyInt());
    }

    @Test
    @DisplayName("크기가 1 보다 작거나 50 보다 크면 400 이고 순위를 읽지 않는다")
    void 크기_범위() {
        assertThatThrownBy(() -> trendingService.trending(null, 0, null)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> trendingService.trending(null, 51, null)).isInstanceOf(CustomException.class);

        verifyNoInteractions(trendingStore);
    }

    @Test
    @DisplayName("조회 알림은 색인에 있는 장소만 세고, 그 장소의 시도 열쇠에도 올린다")
    void 조회_알림() {
        when(searchIndexRepository.findSidoCode(FIRST)).thenReturn(Optional.of("11"));
        when(searchIndexRepository.findSidoCode(CLOSED)).thenReturn(Optional.empty());

        trendingService.recordView(FIRST);
        trendingService.recordView(CLOSED);

        verify(trendingStore).increment(FIRST, "11");
        verify(trendingStore, never()).increment(eq(CLOSED), any());
    }

    private static IndexedCard card(UUID placeId) {
        return new IndexedCard(placeId, "장소", "PARK", "주소", null, null, null, 0, null);
    }
}
