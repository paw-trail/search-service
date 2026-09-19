package com.pawtrail.search.application.service;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.search.application.dto.output.ReindexResult;
import com.pawtrail.search.domain.enums.ReindexTrigger;
import com.pawtrail.search.domain.model.IndexedPlace;
import com.pawtrail.search.domain.model.ReviewStats;
import com.pawtrail.search.domain.provider.PlaceIndexingProvider;
import com.pawtrail.search.domain.provider.ReviewStatsProvider;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 전량 재색인 한 번을 끝까지 돌립니다.
 *
 * 순서가 셋입니다.
 *   ① place 를 id 순으로 500곳씩 이어받아 색인에 넣거나 덮어씀
 *   ② review 평점을 100곳씩 받아 갈아 끼움
 *   ③ 색인에만 있고 이번 바퀴에 못 본 행을 셈 (지우지 않음)
 *
 * 덮어쓰기는 place.updated 를 받았을 때와 같은 규칙입니다.
 * place 수정 시각이 더 새로울 때만 덮어써, 재색인이 이벤트보다 늦게 옛 값을 써도 새 값이 지켜집니다.
 * 그래서 평소 재색인은 거의 아무것도 덮어쓰지 않고, 놓친 이벤트가 있었을 때만 메웁니다.
 *
 * 잠금을 여기서 잡지 않습니다. 부르는 쪽(ReindexTriggerService)이 잡고 풉니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReindexRunner {

    private final PlaceIndexingProvider placeIndexingProvider;
    private final ReviewStatsProvider reviewStatsProvider;
    private final SearchIndexRepository searchIndexRepository;

    public ReindexResult run(ReindexTrigger trigger) {
        long startedNanos = System.nanoTime();
        List<UUID> seen = new ArrayList<>();
        int pages = 0;
        int read = 0;
        int skipped = 0;
        int written = 0;
        UUID after = null;

        try {
            while (true) {
                List<IndexedPlace> page = placeIndexingProvider.findPageAfter(after, PlaceIndexingProvider.MAX_PAGE_SIZE);
                pages++;
                read += page.size();

                List<IndexedPlace> indexable = page.stream().filter(IndexedPlace::isIndexable).toList();
                skipped += page.size() - indexable.size();
                written += searchIndexRepository.saveAllIfNewer(indexable);
                page.stream().map(IndexedPlace::placeId).filter(Objects::nonNull).forEach(seen::add);

                // 받은 수가 쪽 크기보다 적으면 끝
                if (page.size() < PlaceIndexingProvider.MAX_PAGE_SIZE) {
                    break;
                }
                after = lastPlaceId(page);
                if (after == null) {
                    // 마지막 원소에 식별자가 없으면 다음 쪽을 물을 수 없음, 같은 쪽을 되풀이하지 않게 멈춤
                    log.warn("재색인 이어받기 기준이 비어 여기서 멈춥니다: {}쪽", pages);
                    break;
                }
            }
        } catch (CustomException e) {
            // place 를 못 부르면 멈춤
            // 평점과 행 세기는 한 바퀴를 다 봤을 때만 뜻이 있어 건너뜀, 이미 쓴 것은 남음
            ReindexResult stopped = new ReindexResult(trigger, false, pages, read, skipped, written,
                    0, true, -1, elapsedMillis(startedNanos));
            log.warn("재색인이 멈췄습니다 ({}): place {}쪽 · {}곳 읽음 · 덮어씀 {}곳 · reason={}",
                    trigger, pages, read, written, e.getMessage());
            return stopped;
        }

        RatingOutcome ratings = refreshRatings(seen);
        long outside = searchIndexRepository.countOutside(seen);

        ReindexResult result = new ReindexResult(trigger, true, pages, read, skipped, written,
                ratings.changed(), ratings.kept(), outside, elapsedMillis(startedNanos));
        log.info("재색인 끝 ({}): place {}쪽 · {}곳 읽음 · 덮어씀 {}곳 · 건너뜀 {}곳 · 평점 바뀜 {}곳{} · 색인에만 있는 행 {} · {}ms",
                trigger, pages, read, written, skipped, ratings.changed(),
                ratings.kept() ? " (review 를 못 불러 남은 평점은 그대로)" : "",
                outside, result.elapsedMillis());
        if (outside > 0) {
            log.warn("색인에만 있고 place 에 없는 행이 {}개 있습니다. place_db 를 다시 적재했다면 색인을 비우고 재색인하십시오", outside);
        }
        return result;
    }

    // review 평점을 100곳씩 받아 갈아 끼움
    // 한 번이라도 못 부르면 거기서 멈추고 남은 장소의 평점은 그대로 둠 — 빈 결과로 읽어 지우지 않음
    private RatingOutcome refreshRatings(List<UUID> placeIds) {
        int changed = 0;
        for (int from = 0; from < placeIds.size(); from += ReviewStatsProvider.MAX_IDS) {
            List<UUID> chunk = placeIds.subList(from, Math.min(from + ReviewStatsProvider.MAX_IDS, placeIds.size()));

            Map<UUID, ReviewStats> stats;
            try {
                stats = reviewStatsProvider.findByPlaceIds(chunk);
            } catch (CustomException e) {
                log.warn("후기 평점을 못 불러 남은 {}곳의 평점은 그대로 둡니다: reason={}",
                        placeIds.size() - from, e.getMessage());
                return new RatingOutcome(changed, true);
            }

            // 응답에 없는 장소는 후기가 없는 것 — 평점 없음 · 0개로 맞춤
            List<ReviewStats> rows = chunk.stream()
                    .map(placeId -> stats.getOrDefault(placeId, ReviewStats.none(placeId)))
                    .toList();
            changed += searchIndexRepository.updateReviewStats(rows);
        }
        return new RatingOutcome(changed, false);
    }

    private static UUID lastPlaceId(List<IndexedPlace> page) {
        return page.get(page.size() - 1).placeId();
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private record RatingOutcome(int changed, boolean kept) {
    }
}
