package com.pawtrail.search.application.service;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.search.application.dto.output.SearchCardOutput;
import com.pawtrail.search.domain.model.IndexedCard;
import com.pawtrail.search.domain.model.PlaceVerdict;
import com.pawtrail.search.domain.model.SearchFilter;
import com.pawtrail.search.domain.provider.VerdictProvider;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import com.pawtrail.search.domain.repository.TrendingStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 인기 급상승과 조회수 알림입니다.
 *
 * 조회수는 Redis 에만 있는 누적 값입니다(명세 2026.9.3).
 * 장소 상세를 열 때 화면이 알려 오고(search ㉲), 메인의 「내 주변 인기 급상승」 이 읽습니다.
 */
@Service
@RequiredArgsConstructor
public class TrendingService {

    /** 인기 급상승의 기본 수입니다(search ㉼). */
    public static final int DEFAULT_SIZE = 10;

    /** 인기 급상승의 최대 수입니다(search ㉼). */
    public static final int MAX_SIZE = 50;

    // 순위를 몇 구간까지 이어 읽을지 — 구간 하나가 요청 수의 두 배라 최대 열 배까지 봄
    // 순위 위쪽이 폐업 · 색인 밖으로 가득해도 끝없이 읽지 않게 막음
    private static final int MAX_ROUNDS = 5;

    // 위치 없이 카드를 읽을 때 넘기는 빈 조건 — 거리를 셀 기준점이 없음
    private static final SearchFilter NO_LOCATION = new SearchFilter(List.of(), null, null, null, null, null, null, null);

    private final TrendingStore trendingStore;
    private final SearchIndexRepository searchIndexRepository;
    private final VerdictProvider verdictProvider;

    /**
     * 조회수가 많은 장소를 검색 카드 모양으로 돌려줍니다.
     *
     * 반려동물을 주면 판정을 싣고, 없으면 판정 없이 장소 칸만 싣습니다(search ㉼ · ㉢).
     * 폐업했거나 색인에 없는 장소는 빼고, 모자라면 순위를 구간으로 더 읽어 채웁니다(최대 열 배).
     *
     * @param sidoCode 비어 있으면 전국입니다.
     */
    public List<SearchCardOutput> trending(String sidoCode, int size, List<UUID> petIds) {
        List<UUID> pets = petIds == null ? List.of() : petIds.stream().filter(Objects::nonNull).distinct().toList();
        if (size < 1 || size > MAX_SIZE || pets.size() > SearchService.MAX_PETS) {
            throw new CustomException(CommonErrorCode.VALIDATION_FAILED);
        }

        // 폐업했거나 색인에서 빠진 장소를 건너뛰며 요청한 수가 찰 때까지 순위를 구간으로 이어 읽음
        int window = size * 2;
        List<UUID> shown = new ArrayList<>();
        Map<UUID, IndexedCard> cards = new HashMap<>();
        for (int round = 0; round < MAX_ROUNDS && shown.size() < size; round++) {
            List<UUID> ranked = trendingStore.top(sidoCode, round * window, window);
            if (!ranked.isEmpty()) {
                Map<UUID, IndexedCard> found = searchIndexRepository.findCards(ranked, NO_LOCATION);
                for (UUID placeId : ranked) {
                    if (shown.size() < size && found.containsKey(placeId)) {
                        shown.add(placeId);
                        cards.put(placeId, found.get(placeId));
                    }
                }
            }
            if (ranked.size() < window) {
                // 순위가 끝남
                break;
            }
        }
        if (shown.isEmpty()) {
            return List.of();
        }

        Map<UUID, PlaceVerdict> verdicts = verdictProvider.findByPlaceIds(shown, pets);
        return shown.stream()
                .map(placeId -> SearchCardOutput.of(cards.get(placeId),
                        verdicts.getOrDefault(placeId, PlaceVerdict.empty(placeId))))
                .toList();
    }

    /**
     * 장소 상세를 열었다는 알림을 받아 조회수를 하나 올립니다.
     *
     * 색인에 없는 장소는 세지 않습니다(search ㉲). 잘못 보낸 식별자가 순위에 끼지 않게 하려는 것입니다.
     * 화면에는 결과를 알리지 않습니다 — 세든 안 세든 204 입니다.
     */
    public void recordView(UUID placeId) {
        searchIndexRepository.findSidoCode(placeId)
                .ifPresent(sidoCode -> trendingStore.increment(placeId, sidoCode));
    }
}
