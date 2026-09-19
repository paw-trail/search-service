package com.pawtrail.search.application.service;

import com.pawtrail.search.application.dto.output.IndexRefreshResult;
import com.pawtrail.search.domain.model.IndexedPlace;
import com.pawtrail.search.domain.provider.PlaceIndexingProvider;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 장소를 place 에서 다시 읽어 색인을 덮어씁니다. place.updated 를 받았을 때 씁니다.
 *
 * 이벤트에는 placeId 만 있습니다.
 * 값은 place 의 색인용 조회로 다시 읽습니다. 이벤트에 값을 싣지 않은 이유는
 * 복제할 칸이 늘어도 이벤트 모양이 안 바뀌게 하려는 것입니다.
 *
 * 처리 기록(Inbox)을 남기지 않습니다.
 * 같은 장소를 두 번 읽어 덮어써도 결과가 같고, 순서가 뒤바뀌어도
 * place 수정 시각이 더 새 값만 덮어쓰므로 옛 값이 이기지 않습니다.
 * 기록을 남기면 받는 이벤트 수만큼 표가 끝없이 커집니다.
 *
 * 트랜잭션을 여기서 열지 않습니다.
 * place 를 부르는 동안 데이터베이스 연결을 붙잡지 않으려는 것이고,
 * 저장은 100개 한 묶음마다 저장소가 따로 끝냅니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    private final PlaceIndexingProvider placeIndexingProvider;
    private final SearchIndexRepository searchIndexRepository;

    /**
     * 그 장소들을 다시 읽어 색인에 넣거나 덮어씁니다.
     *
     * 중복과 null 을 거른 뒤 100개씩 나눠 place 를 부릅니다.
     * place 가 한 번에 받는 상한이 100 이고, 식별자가 주소에 실려 그보다 많으면 주소 길이 천장에 닿습니다.
     *
     * place 를 부르지 못하면 PLACE_UNAVAILABLE 이 그대로 올라갑니다.
     * 앞 묶음에서 이미 쓴 것은 남습니다. 다시 시도하면 같은 값으로 덮어써 결과가 같습니다.
     */
    public IndexRefreshResult refresh(Collection<UUID> placeIds) {
        List<UUID> ids = placeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return IndexRefreshResult.EMPTY;
        }

        int received = 0;
        int skipped = 0;
        int written = 0;
        for (int from = 0; from < ids.size(); from += PlaceIndexingProvider.MAX_IDS) {
            List<UUID> chunk = ids.subList(from, Math.min(from + PlaceIndexingProvider.MAX_IDS, ids.size()));
            List<IndexedPlace> places = placeIndexingProvider.findByIds(chunk);
            received += places.size();

            List<IndexedPlace> indexable = places.stream().filter(IndexedPlace::isIndexable).toList();
            skipped += places.size() - indexable.size();
            written += searchIndexRepository.saveAllIfNewer(indexable);
        }

        if (received < ids.size()) {
            // place 는 장소를 지우지 않으므로 정상이라면 생기지 않음
            log.warn("place 에 없는 장소가 있습니다: 요청 {}곳 · 받음 {}곳", ids.size(), received);
        }
        if (skipped > 0) {
            log.warn("필수 칸이 비어 색인에 넣지 않은 장소가 있습니다: {}곳", skipped);
        }

        return new IndexRefreshResult(ids.size(), received, skipped, written);
    }
}
