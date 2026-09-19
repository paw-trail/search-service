package com.pawtrail.search.domain.repository;

import com.pawtrail.search.domain.enums.SearchSort;
import com.pawtrail.search.domain.model.IndexedCard;
import com.pawtrail.search.domain.model.IndexedPlace;
import com.pawtrail.search.domain.model.RegionCount;
import com.pawtrail.search.domain.model.ReviewStats;
import com.pawtrail.search.domain.model.SearchFilter;
import com.pawtrail.search.domain.model.Suggestion;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 검색 색인을 저장하는 약속입니다.
 *
 * 이 인터페이스에는 SQL 이라는 단어가 나오지 않습니다.
 * 무엇을 할 수 있는지만 적고 어떻게 하는지는 infrastructure 가 정합니다.
 */
public interface SearchIndexRepository {

    /**
     * 장소들을 색인에 넣거나 덮어씁니다.
     *
     * place 수정 시각이 지금 담긴 것보다 새로울 때만 덮어씁니다.
     * 같거나 옛 시각이면 그대로 둡니다.
     * 이벤트 처리와 재색인이 겹쳐 옛 값을 늦게 쓰려 해도 새 값이 지켜집니다.
     * 같은 이벤트가 두 번 와도 두 번째는 아무것도 바꾸지 않습니다.
     *
     * 평점은 건드리지 않습니다. 처음 넣을 때는 평점 없음 · 후기 0 입니다.
     *
     * @return 실제로 넣거나 덮어쓴 행 수입니다.
     */
    int saveAllIfNewer(List<IndexedPlace> places);

    /**
     * 평점을 갈아 끼웁니다. 재색인이 review 에서 받은 값을 씁니다.
     *
     * place 에서 온 칸과 place 수정 시각은 건드리지 않습니다.
     * 평점과 후기 수가 지금 담긴 것과 같으면 쓰지 않습니다.
     *
     * @return 실제로 바뀐 행 수입니다. 색인에 없는 장소는 세지 않습니다.
     */
    int updateReviewStats(List<ReviewStats> stats);

    /**
     * 주어진 장소들 밖에 있는 색인 행을 셉니다. 재색인이 한 바퀴 동안 본 장소를 넘깁니다.
     *
     * 지우지 않고 세기만 합니다.
     * place 는 장소를 지우지 않아 운영에서는 0 이어야 합니다.
     * 0 이 아니면 로컬에서 place_db 를 다시 적재해 식별자가 새로 발급된 흔적이라,
     * 색인을 비우고 재색인해 맞춥니다.
     */
    long countOutside(Collection<UUID> placeIds);

    /**
     * 조건에 맞는 장소를 줄 세워 한 쪽만 돌려줍니다. 판정 필터도 인기순도 아닐 때 씁니다.
     *
     * 폐업한 장소는 늘 뺍니다.
     */
    List<UUID> findIds(SearchFilter filter, SearchSort sort, int offset, int limit);

    /**
     * 조건에 맞는 장소를 줄 세워 전부 돌려줍니다.
     *
     * 판정 필터가 걸렸거나 인기순일 때 씁니다. 판정과 조회수가 색인 밖에 있어
     * 후보를 다 뽑아 메모리에서 거르고 줄 세운 뒤에야 쪽을 자를 수 있습니다(search ㉣ · ㉺).
     */
    List<UUID> findAllIds(SearchFilter filter, SearchSort sort);

    /**
     * 조건에 맞는 장소 수입니다.
     */
    long count(SearchFilter filter);

    /**
     * 장소들의 카드 값입니다. 위치가 있으면 거리를 함께 셉니다.
     *
     * 폐업한 장소는 빠집니다. 인기 급상승처럼 색인 밖(조회수)에서 온 식별자를 넘길 때가 있어서입니다.
     *
     * @return 장소 식별자로 찾는 카드입니다. 차례는 부르는 쪽이 맞춥니다.
     */
    Map<UUID, IndexedCard> findCards(Collection<UUID> placeIds, SearchFilter filter);

    /**
     * 자동완성입니다. 이름이나 별칭이 검색어로 시작하는 곳을 먼저, 모자라면 검색어가 들어 있는 곳으로 채웁니다(search ㉽).
     *
     * 폐업한 장소는 뺍니다. 각 무리 안에서는 이름의 가나다순입니다.
     */
    List<Suggestion> suggest(String query, int limit);

    /**
     * 색인에 있는 장소의 시도 코드입니다. 조회수를 시도 열쇠에도 올릴 때 씁니다.
     *
     * @return 색인에 없으면 비어 있습니다. 있는데 시도를 모르면 빈 글자입니다.
     */
    Optional<String> findSidoCode(UUID placeId);

    /**
     * 시도 · 시군구마다 폐업을 뺀 장소 수입니다. 시도 코드 · 시군구 이름의 가나다순입니다.
     */
    List<RegionCount> countByRegion();
}
