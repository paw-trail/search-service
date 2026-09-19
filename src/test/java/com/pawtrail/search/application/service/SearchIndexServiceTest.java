package com.pawtrail.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.search.application.dto.output.IndexRefreshResult;
import com.pawtrail.search.domain.exception.SearchErrorCode;
import com.pawtrail.search.domain.model.IndexedPlace;
import com.pawtrail.search.domain.provider.PlaceIndexingProvider;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * place.updated 로 색인을 다시 읽는 규칙을 검사합니다.
 *
 * place 와 데이터베이스는 흉내 냅니다.
 * 여기서 지키려는 것은 나누고 거르고 세는 규칙입니다.
 * 실제로 넣고 덮어쓰는 것은 SearchIndexRepositoryImplTest 가 PostGIS 컨테이너로 봅니다.
 */
@ExtendWith(MockitoExtension.class)
class SearchIndexServiceTest {

    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 9, 11, 19, 48, 47);

    @Mock
    private PlaceIndexingProvider placeIndexingProvider;

    @Mock
    private SearchIndexRepository searchIndexRepository;

    @InjectMocks
    private SearchIndexService searchIndexService;

    @Captor
    private ArgumentCaptor<List<UUID>> idsCaptor;

    @Test
    @DisplayName("중복과 null 을 걸러 100개씩 나눠 부른다")
    void 백개씩_나눠_부른다() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            ids.add(uuid(i));
        }
        ids.add(null);
        ids.add(uuid(0));
        when(placeIndexingProvider.findByIds(anyList()))
                .thenAnswer(invocation -> invocation.<List<UUID>>getArgument(0).stream()
                        .map(SearchIndexServiceTest::place)
                        .toList());
        when(searchIndexRepository.saveAllIfNewer(anyList()))
                .thenAnswer(invocation -> invocation.<List<IndexedPlace>>getArgument(0).size());

        IndexRefreshResult result = searchIndexService.refresh(ids);

        // place 가 한 번에 받는 상한이 100 이라 그 위로 보내면 400 이 남
        verify(placeIndexingProvider, times(3)).findByIds(idsCaptor.capture());
        assertThat(idsCaptor.getAllValues()).extracting(List::size).containsExactly(100, 100, 50);
        assertThat(result).isEqualTo(new IndexRefreshResult(250, 250, 0, 250));
    }

    @Test
    @DisplayName("물어볼 것이 없으면 place 도 저장소도 부르지 않는다")
    void 빈_요청() {
        assertThat(searchIndexService.refresh(Arrays.asList(null, null))).isEqualTo(IndexRefreshResult.EMPTY);

        verifyNoInteractions(placeIndexingProvider, searchIndexRepository);
    }

    @Test
    @DisplayName("필수 칸이 빈 장소는 넣지 않고 건너뜀으로 센다")
    void 필수_칸이_빈_장소() {
        // 그런 한 곳 때문에 묶음 전체가 실패해 .dlq 로 가면 안 됨
        IndexedPlace complete = place(uuid(1));
        IndexedPlace noCoordinate = new IndexedPlace(uuid(2), "좌표 없는 곳", List.of(), "PARK",
                null, null, "11", null, null, null, List.of(), "ACTIVE", null, null, null, UPDATED_AT);
        when(placeIndexingProvider.findByIds(anyList())).thenReturn(List.of(complete, noCoordinate));
        when(searchIndexRepository.saveAllIfNewer(List.of(complete))).thenReturn(1);

        IndexRefreshResult result = searchIndexService.refresh(List.of(uuid(1), uuid(2)));

        assertThat(result).isEqualTo(new IndexRefreshResult(2, 2, 1, 1));
    }

    @Test
    @DisplayName("place 에 없는 식별자는 빠질 뿐 오류가 아니다")
    void 없는_식별자() {
        when(placeIndexingProvider.findByIds(anyList())).thenReturn(List.of(place(uuid(1))));
        when(searchIndexRepository.saveAllIfNewer(anyList())).thenReturn(1);

        IndexRefreshResult result = searchIndexService.refresh(List.of(uuid(1), uuid(2)));

        assertThat(result).isEqualTo(new IndexRefreshResult(2, 1, 0, 1));
    }

    @Test
    @DisplayName("place 를 못 부르면 그대로 올려 묶음을 다시 시도하게 한다")
    void place_장애() {
        when(placeIndexingProvider.findByIds(anyList()))
                .thenThrow(new CustomException(SearchErrorCode.PLACE_UNAVAILABLE));

        assertThatThrownBy(() -> searchIndexService.refresh(List.of(uuid(1))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SearchErrorCode.PLACE_UNAVAILABLE);

        verifyNoInteractions(searchIndexRepository);
    }

    // UUID v7 모양을 흉내 낸 고정 식별자임, 순서대로 만들어 두면 실패했을 때 어느 것인지 읽기 쉬움
    private static UUID uuid(int n) {
        return new UUID(0x01a0901500007000L, 0x8000000000000000L | n);
    }

    private static IndexedPlace place(UUID id) {
        return new IndexedPlace(id, "장소 " + id, List.of(), "PARK", "서울특별시 영등포구 여의동로 330", null,
                "11", "영등포구", new BigDecimal("37.5283000"), new BigDecimal("126.9326000"), List.of(),
                "ACTIVE", null, null, null, UPDATED_AT);
    }
}
