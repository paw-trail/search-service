package com.pawtrail.search.application.service;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.search.application.dto.input.SearchCommand;
import com.pawtrail.search.application.dto.output.SearchCardOutput;
import com.pawtrail.search.application.dto.output.SearchSummaryOutput;
import com.pawtrail.search.domain.enums.SearchSort;
import com.pawtrail.search.domain.enums.Verdict;
import com.pawtrail.search.domain.model.IndexedCard;
import com.pawtrail.search.domain.model.PlaceVerdict;
import com.pawtrail.search.domain.model.SearchFilter;
import com.pawtrail.search.domain.provider.VerdictProvider;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import com.pawtrail.search.domain.repository.TrendingStore;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 검색과 탐색 카운트를 답합니다.
 *
 * 길이 둘입니다.
 *   SQL 로 쪽을 자르는 길    판정 필터도 인기순도 아닐 때. 색인에서 한 쪽만 뽑고 그 쪽만 판정함
 *   후보를 다 뽑는 길        판정 필터가 걸렸거나 인기순일 때. 판정과 조회수가 색인 밖에 있어
 *                          후보 전부를 판정하거나(search ㉣) 조회수로 줄 세운 뒤(search ㉺) 쪽을 자름
 *
 * 판정은 verdict 에 묻습니다. 색인에는 동반 조건이 한 칸도 없습니다.
 * 반려동물이 없어도 verdict 를 부릅니다(search ㉢). 충돌 여부 · 준비물은 반려동물과 무관하게 싣기 때문입니다.
 *
 * 요청이 말이 안 되면 400 VALIDATION_FAILED 입니다.
 *   시군구만 주고 시도를 안 줌 (시군구 이름이 시도마다 겹침)
 *   위도 · 경도 중 하나만 줌 · 거리순인데 위치가 없음
 *   판정 필터나 탐색 카운트인데 반려동물이 없음 (search ㉢)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    /** 한 쪽의 최대 크기입니다. */
    public static final int MAX_PAGE_SIZE = 100;

    /** 한 번에 판정할 수 있는 반려동물 수입니다. verdict 의 상한과 같습니다. */
    public static final int MAX_PETS = 100;

    private final SearchIndexRepository searchIndexRepository;
    private final VerdictProvider verdictProvider;
    private final TrendingStore trendingStore;

    public PageResponse<SearchCardOutput> search(SearchCommand command) {
        validate(command);
        SearchFilter filter = command.filter();
        SearchSort sort = command.effectiveSort();
        long offset = (long) command.page() * command.size();

        boolean needsAll = !command.verdictFilter().isEmpty() || sort == SearchSort.POPULAR;
        if (!needsAll) {
            long total = searchIndexRepository.count(filter);
            List<UUID> pageIds = offset >= total
                    ? List.of()
                    : searchIndexRepository.findIds(filter, sort, (int) offset, command.size());
            return page(command, pageIds, verdictsOf(pageIds, command.petIds()), total);
        }

        long startedNanos = System.nanoTime();
        // 인기순은 조회수로 다시 줄 세우므로 SQL 차례는 이름순으로 둠 — 조회수가 같은 곳끼리의 차례가 됨
        List<UUID> candidates = searchIndexRepository.findAllIds(filter,
                sort == SearchSort.POPULAR ? SearchSort.NAME : sort);
        if (sort == SearchSort.POPULAR) {
            candidates = byPopularity(candidates);
        }

        Map<UUID, PlaceVerdict> verdicts = Map.of();
        if (!command.verdictFilter().isEmpty()) {
            // 전수 판정 — 후보 전부를 판정해야 걸러진 뒤의 총 개수와 쪽이 맞음
            verdicts = verdictsOf(candidates, command.petIds());
            Set<Verdict> keep = EnumSet.copyOf(command.verdictFilter());
            Map<UUID, PlaceVerdict> judged = verdicts;
            candidates = candidates.stream()
                    .filter(placeId -> keep.contains(verdictOf(judged, placeId).placeVerdict()))
                    .toList();
        }

        long total = candidates.size();
        List<UUID> pageIds = offset >= total
                ? List.of()
                : candidates.subList((int) offset, (int) Math.min(offset + command.size(), total));
        if (command.verdictFilter().isEmpty()) {
            verdicts = verdictsOf(pageIds, command.petIds());
        }

        log.info("후보를 다 뽑는 검색: 정렬 {} · 판정 필터 {} · 남은 {}곳 · {}ms",
                sort, command.verdictFilter(), total, (System.nanoTime() - startedNanos) / 1_000_000);
        return page(command, pageIds, verdicts, total);
    }

    /**
     * 탐색 카운트 — 같은 조건에서 장소 판정별 장소 수입니다. 후보 전부를 판정합니다.
     */
    public SearchSummaryOutput summarize(SearchCommand command) {
        validate(command);
        if (command.petIds().isEmpty()) {
            // 반려동물이 없으면 판정이 없어 셀 것이 없음 (search ㉢)
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }

        long startedNanos = System.nanoTime();
        List<UUID> candidates = searchIndexRepository.findAllIds(command.filter(), SearchSort.NAME);
        Map<UUID, PlaceVerdict> verdicts = verdictsOf(candidates, command.petIds());

        Map<Verdict, Long> counts = new EnumMap<>(Verdict.class);
        for (UUID placeId : candidates) {
            Verdict verdict = verdictOf(verdicts, placeId).placeVerdict();
            if (verdict != null) {
                counts.merge(verdict, 1L, Long::sum);
            }
        }

        log.info("탐색 카운트: 후보 {}곳 · {}ms", candidates.size(), (System.nanoTime() - startedNanos) / 1_000_000);
        return new SearchSummaryOutput(candidates.size(),
                counts.getOrDefault(Verdict.ALLOWED, 0L),
                counts.getOrDefault(Verdict.CONDITIONAL, 0L),
                counts.getOrDefault(Verdict.NOT_ALLOWED, 0L),
                counts.getOrDefault(Verdict.UNKNOWN, 0L));
    }

    private PageResponse<SearchCardOutput> page(SearchCommand command, List<UUID> pageIds,
                                                Map<UUID, PlaceVerdict> verdicts, long total) {
        Map<UUID, IndexedCard> cards = pageIds.isEmpty()
                ? Map.of()
                : searchIndexRepository.findCards(pageIds, command.filter());

        List<SearchCardOutput> content = pageIds.stream()
                .map(cards::get)
                .filter(Objects::nonNull)
                .map(card -> SearchCardOutput.of(card, verdictOf(verdicts, card.placeId())))
                .toList();

        int totalPages = (int) ((total + command.size() - 1) / command.size());
        return new PageResponse<>(content,
                new PageResponse.PageInfo(command.page(), command.size(), total, totalPages));
    }

    // verdict 는 한 번에 500곳까지라 나눠 부르고 합침
    private Map<UUID, PlaceVerdict> verdictsOf(List<UUID> placeIds, List<UUID> petIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PlaceVerdict> verdicts = new HashMap<>();
        for (int from = 0; from < placeIds.size(); from += VerdictProvider.MAX_PLACES) {
            List<UUID> chunk = placeIds.subList(from, Math.min(from + VerdictProvider.MAX_PLACES, placeIds.size()));
            verdicts.putAll(verdictProvider.findByPlaceIds(chunk, petIds));
        }
        return verdicts;
    }

    // 조회수가 많은 곳부터, 같으면 들어온 차례(이름순) 그대로 — 스트림 정렬은 안정 정렬임
    private List<UUID> byPopularity(List<UUID> candidates) {
        Map<UUID, Double> scores = trendingStore.scoresOf(candidates);
        return candidates.stream()
                .sorted(Comparator.comparingDouble((UUID placeId) -> scores.getOrDefault(placeId, 0.0)).reversed())
                .toList();
    }

    private static PlaceVerdict verdictOf(Map<UUID, PlaceVerdict> verdicts, UUID placeId) {
        PlaceVerdict verdict = verdicts.get(placeId);
        return verdict == null ? PlaceVerdict.empty(placeId) : verdict;
    }

    private static void validate(SearchCommand command) {
        SearchFilter filter = command.filter();
        boolean invalid = (filter.sigunguName() != null && filter.sidoCode() == null)
                || ((filter.lat() == null) != (filter.lon() == null))
                || (filter.radiusM() != null && filter.radiusM() <= 0)
                || (command.effectiveSort() == SearchSort.DISTANCE && !filter.hasLocation())
                || (!command.verdictFilter().isEmpty() && command.petIds().isEmpty())
                || command.petIds().size() > MAX_PETS
                || command.page() < 0
                || command.size() < 1
                || command.size() > MAX_PAGE_SIZE;
        if (invalid) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }
    }
}
