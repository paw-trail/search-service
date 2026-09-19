package com.pawtrail.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.search.application.dto.input.SearchCommand;
import com.pawtrail.search.application.dto.output.SearchCardOutput;
import com.pawtrail.search.application.dto.output.SearchSummaryOutput;
import com.pawtrail.search.domain.enums.SearchSort;
import com.pawtrail.search.domain.enums.Verdict;
import com.pawtrail.search.domain.model.IndexedCard;
import com.pawtrail.search.domain.model.PetVerdict;
import com.pawtrail.search.domain.model.PlaceVerdict;
import com.pawtrail.search.domain.model.SearchFilter;
import com.pawtrail.search.domain.provider.VerdictProvider;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import com.pawtrail.search.domain.repository.TrendingStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 검색의 두 길과 탐색 카운트를 검사합니다.
 *
 * 색인 · verdict · 조회수는 흉내 냅니다. SQL 은 SearchQueryTest 가 실제 데이터베이스로 봅니다.
 * 여기서 지키려는 것은 어느 길로 가는가 · 판정을 몇 곳에 묻는가 · 무엇으로 거르고 줄 세우는가입니다.
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    private static final SearchFilter NO_FILTER = new SearchFilter(List.of(), null, null, null, null, null, null, null);
    private static final UUID MALTESE = UUID.fromString("01a0726a-1111-7000-8000-000000000001");
    private static final UUID RETRIEVER = UUID.fromString("01a0726a-1111-7000-8000-000000000002");

    @Mock
    private SearchIndexRepository searchIndexRepository;

    @Mock
    private VerdictProvider verdictProvider;

    @Mock
    private TrendingStore trendingStore;

    @InjectMocks
    private SearchService searchService;

    @Test
    @DisplayName("판정 필터도 인기순도 아니면 SQL 로 한 쪽만 뽑고 그 쪽만 판정한다")
    void SQL_로_쪽을_자른다() {
        when(searchIndexRepository.count(NO_FILTER)).thenReturn(45L);
        when(searchIndexRepository.findIds(NO_FILTER, SearchSort.RATING, 20, 20)).thenReturn(List.of(place(21), place(22)));
        when(searchIndexRepository.findCards(List.of(place(21), place(22)), NO_FILTER)).thenReturn(cards(21, 22));
        when(verdictProvider.findByPlaceIds(List.of(place(21), place(22)), List.of())).thenReturn(Map.of());

        PageResponse<SearchCardOutput> page = searchService.search(new SearchCommand(NO_FILTER, null, null, null, 1, 20));

        // 정렬을 안 줬고 위치도 없으니 평점순 (㉻) · 반려동물이 없어도 verdict 를 부름 (㉢)
        assertThat(page.content()).extracting(SearchCardOutput::placeId).containsExactly(place(21), place(22));
        assertThat(page.page().totalElements()).isEqualTo(45L);
        assertThat(page.page().totalPages()).isEqualTo(3);
        verify(searchIndexRepository, never()).findAllIds(any(), any());
    }

    @Test
    @DisplayName("판정 필터가 걸리면 후보 전부를 판정해 가장 막히는 마리 기준으로 거른 뒤 쪽을 자른다")
    void 전수_판정으로_거른다() {
        List<UUID> candidates = List.of(place(1), place(2), place(3));
        when(searchIndexRepository.findAllIds(NO_FILTER, SearchSort.RATING)).thenReturn(candidates);
        when(verdictProvider.findByPlaceIds(candidates, List.of(MALTESE, RETRIEVER))).thenReturn(Map.of(
                place(1), judged(place(1), Verdict.ALLOWED, Verdict.ALLOWED),
                place(2), judged(place(2), Verdict.ALLOWED, Verdict.NOT_ALLOWED),
                place(3), judged(place(3), Verdict.CONDITIONAL, Verdict.ALLOWED)));
        when(searchIndexRepository.findCards(List.of(place(1)), NO_FILTER)).thenReturn(cards(1));

        PageResponse<SearchCardOutput> page = searchService.search(new SearchCommand(
                NO_FILTER, List.of(Verdict.ALLOWED), List.of(MALTESE, RETRIEVER), null, 0, 20));

        // 「동반 가능만」은 두 마리 모두 가능한 곳 — 한 마리라도 막히면 빠짐
        assertThat(page.content()).extracting(SearchCardOutput::placeId).containsExactly(place(1));
        assertThat(page.page().totalElements()).isEqualTo(1L);
        assertThat(page.content().get(0).verdicts()).hasSize(2);
        // 걸러진 카드에는 전수 판정 결과를 그대로 싣고 다시 부르지 않음
        verify(verdictProvider, times(1)).findByPlaceIds(anyList(), anyList());
    }

    @Test
    @DisplayName("후보가 500곳을 넘으면 verdict 를 500곳씩 나눠 부른다")
    void 전수_판정은_500곳씩() {
        List<UUID> candidates = IntStream.range(0, 1001).mapToObj(SearchServiceTest::place).toList();
        when(searchIndexRepository.findAllIds(NO_FILTER, SearchSort.RATING)).thenReturn(candidates);
        when(verdictProvider.findByPlaceIds(anyList(), eq(List.of(MALTESE)))).thenReturn(Map.of());

        searchService.search(new SearchCommand(NO_FILTER, List.of(Verdict.ALLOWED), List.of(MALTESE), null, 0, 20));

        verify(verdictProvider).findByPlaceIds(candidates.subList(0, 500), List.of(MALTESE));
        verify(verdictProvider).findByPlaceIds(candidates.subList(500, 1000), List.of(MALTESE));
        verify(verdictProvider).findByPlaceIds(candidates.subList(1000, 1001), List.of(MALTESE));
    }

    @Test
    @DisplayName("인기순은 조회수로 다시 줄 세우고, 같으면 이름순 차례를 지킨다")
    void 인기순() {
        List<UUID> byName = List.of(place(1), place(2), place(3));
        when(searchIndexRepository.findAllIds(NO_FILTER, SearchSort.NAME)).thenReturn(byName);
        when(trendingStore.scoresOf(byName)).thenReturn(Map.of(place(2), 5.0, place(3), 1.0));
        when(searchIndexRepository.findCards(List.of(place(2), place(3), place(1)), NO_FILTER)).thenReturn(cards(1, 2, 3));

        PageResponse<SearchCardOutput> page = searchService.search(new SearchCommand(
                NO_FILTER, null, null, SearchSort.POPULAR, 0, 20));

        assertThat(page.content()).extracting(SearchCardOutput::placeId).containsExactly(place(2), place(3), place(1));
    }

    @Test
    @DisplayName("탐색 카운트는 후보 전부를 판정해 장소 판정별로 센다")
    void 탐색_카운트() {
        List<UUID> candidates = List.of(place(1), place(2), place(3), place(4));
        when(searchIndexRepository.findAllIds(NO_FILTER, SearchSort.NAME)).thenReturn(candidates);
        when(verdictProvider.findByPlaceIds(candidates, List.of(MALTESE))).thenReturn(Map.of(
                place(1), judged(place(1), Verdict.ALLOWED),
                place(2), judged(place(2), Verdict.ALLOWED),
                place(3), judged(place(3), Verdict.NOT_ALLOWED),
                place(4), judged(place(4), Verdict.UNKNOWN)));

        SearchSummaryOutput summary = searchService.summarize(new SearchCommand(
                NO_FILTER, null, List.of(MALTESE), null, 0, 1));

        assertThat(summary).isEqualTo(new SearchSummaryOutput(4, 2, 0, 1, 1));
    }

    @Test
    @DisplayName("말이 안 되는 요청은 400 — 시군구만 · 위치 없는 거리순 · 반려동물 없는 판정 필터와 카운트")
    void 잘못된_요청() {
        SearchFilter sigunguOnly = new SearchFilter(List.of(), null, "영등포구", null, null, null, null, null);

        assertThatThrownBy(() -> searchService.search(new SearchCommand(sigunguOnly, null, null, null, 0, 20)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> searchService.search(new SearchCommand(NO_FILTER, null, null, SearchSort.DISTANCE, 0, 20)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> searchService.search(new SearchCommand(NO_FILTER, List.of(Verdict.ALLOWED), null, null, 0, 20)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> searchService.summarize(new SearchCommand(NO_FILTER, null, null, null, 0, 1)))
                .isInstanceOf(CustomException.class);
    }

    private static UUID place(int n) {
        return new UUID(0x01a0901500007000L, 0x8000000000000000L | n);
    }

    private static Map<UUID, IndexedCard> cards(int... ns) {
        return IntStream.of(ns).mapToObj(n -> new IndexedCard(place(n), "장소 " + n, "PARK", "주소 " + n,
                        null, null, null, 0, null))
                .collect(Collectors.toMap(IndexedCard::placeId, Function.identity()));
    }

    private static PlaceVerdict judged(UUID placeId, Verdict... verdicts) {
        List<UUID> pets = List.of(MALTESE, RETRIEVER);
        return new PlaceVerdict(placeId, false,
                IntStream.range(0, verdicts.length).mapToObj(i -> new PetVerdict(pets.get(i), verdicts[i])).toList(),
                "근거", List.of());
    }
}
