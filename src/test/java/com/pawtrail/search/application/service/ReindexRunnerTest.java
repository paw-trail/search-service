package com.pawtrail.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.search.application.dto.output.ReindexResult;
import com.pawtrail.search.domain.enums.ReindexTrigger;
import com.pawtrail.search.domain.exception.SearchErrorCode;
import com.pawtrail.search.domain.model.IndexedPlace;
import com.pawtrail.search.domain.model.ReviewStats;
import com.pawtrail.search.domain.provider.PlaceIndexingProvider;
import com.pawtrail.search.domain.provider.ReviewStatsProvider;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 전량 재색인 한 바퀴의 순서와 멈추는 자리를 검사합니다.
 *
 * place · review · 데이터베이스는 흉내 냅니다.
 * 여기서 지키려는 것은 이어받기 기준 · 평점을 100곳씩 나누는 것 · 실패했을 때 무엇을 그대로 두는가입니다.
 */
@ExtendWith(MockitoExtension.class)
class ReindexRunnerTest {

    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 9, 11, 19, 48, 47);

    @Mock
    private PlaceIndexingProvider placeIndexingProvider;

    @Mock
    private ReviewStatsProvider reviewStatsProvider;

    @Mock
    private SearchIndexRepository searchIndexRepository;

    @InjectMocks
    private ReindexRunner reindexRunner;

    @Test
    @DisplayName("place 를 500곳씩 끝까지 이어받고, 쪽마다 마지막 식별자 뒤를 묻는다")
    void 끝까지_이어받는다() {
        when(placeIndexingProvider.findPageAfter(null, 500)).thenReturn(places(0, 500));
        when(placeIndexingProvider.findPageAfter(uuid(499), 500)).thenReturn(places(500, 500));
        when(placeIndexingProvider.findPageAfter(uuid(999), 500)).thenReturn(places(1000, 1));

        ReindexResult result = reindexRunner.run(ReindexTrigger.SCHEDULE);

        assertThat(result.completed()).isTrue();
        assertThat(result.pages()).isEqualTo(3);
        assertThat(result.read()).isEqualTo(1001);
        // 평점도 한 바퀴에서 본 1001곳을 100곳씩 — 열한 번
        verify(reviewStatsProvider, times(11)).findByPlaceIds(anyList());
    }

    @Test
    @DisplayName("review 응답에 없는 장소는 후기 없음으로 채워 넘긴다")
    void 응답에_없는_장소는_후기_없음() {
        when(placeIndexingProvider.findPageAfter(null, 500)).thenReturn(places(0, 2));
        when(reviewStatsProvider.findByPlaceIds(List.of(uuid(0), uuid(1))))
                .thenReturn(Map.of(uuid(0), new ReviewStats(uuid(0), new BigDecimal("4.5"), 3)));

        reindexRunner.run(ReindexTrigger.ADMIN);

        // 후기가 모두 지워진 장소의 옛 평점이 남지 않게 null · 0 으로 맞춤
        verify(searchIndexRepository).updateReviewStats(List.of(
                new ReviewStats(uuid(0), new BigDecimal("4.5"), 3),
                ReviewStats.none(uuid(1))));
    }

    @Test
    @DisplayName("review 를 못 부르면 거기서 멈추고 남은 평점은 그대로 두되 재색인은 끝낸다")
    void review_장애() {
        when(placeIndexingProvider.findPageAfter(null, 500)).thenReturn(places(0, 2));
        when(reviewStatsProvider.findByPlaceIds(anyList()))
                .thenThrow(new CustomException(SearchErrorCode.REVIEW_UNAVAILABLE));

        ReindexResult result = reindexRunner.run(ReindexTrigger.SCHEDULE);

        // 못 받은 것을 빈 결과로 읽으면 평점이 전부 지워짐
        verify(searchIndexRepository, never()).updateReviewStats(anyList());
        assertThat(result.completed()).isTrue();
        assertThat(result.ratingsKept()).isTrue();
    }

    @Test
    @DisplayName("place 를 못 부르면 멈추고 평점과 행 세기를 건너뛴다")
    void place_장애() {
        when(placeIndexingProvider.findPageAfter(null, 500)).thenReturn(places(0, 500));
        when(placeIndexingProvider.findPageAfter(uuid(499), 500))
                .thenThrow(new CustomException(SearchErrorCode.PLACE_UNAVAILABLE));

        ReindexResult result = reindexRunner.run(ReindexTrigger.SCHEDULE);

        // 한 바퀴를 다 못 봤으니 "색인에만 있는 행" 을 세면 멀쩡한 행까지 셈
        assertThat(result.completed()).isFalse();
        assertThat(result.read()).isEqualTo(500);
        assertThat(result.outside()).isEqualTo(-1L);
        verifyNoInteractions(reviewStatsProvider);
        verify(searchIndexRepository, never()).countOutside(anyCollection());
    }

    // UUID v7 모양을 흉내 낸 고정 식별자임, 순서대로 만들어 이어받기 기준을 읽기 쉽게 함
    private static UUID uuid(int n) {
        return new UUID(0x01a0901500007000L, 0x8000000000000000L | n);
    }

    private static List<IndexedPlace> places(int from, int count) {
        return IntStream.range(from, from + count)
                .mapToObj(n -> new IndexedPlace(uuid(n), "장소 " + n, List.of(), "PARK", null, null,
                        "11", "종로구", new BigDecimal("37.5000000"), new BigDecimal("127.0000000"),
                        List.of(), "ACTIVE", null, null, null, UPDATED_AT))
                .toList();
    }
}
