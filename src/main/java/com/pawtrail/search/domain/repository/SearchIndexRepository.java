package com.pawtrail.search.domain.repository;

import com.pawtrail.search.domain.model.IndexedPlace;
import java.util.List;

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
}
