package com.pawtrail.search.application.service;

import com.pawtrail.search.application.dto.output.SuggestionOutput;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 자동완성입니다. 색인만 보고 판정하지 않습니다 — 글자를 칠 때마다 부르기 때문입니다(명세).
 */
@Service
@RequiredArgsConstructor
public class SuggestService {

    /** 돌려주는 최대 수입니다(search ㉽). */
    public static final int LIMIT = 10;

    private final SearchIndexRepository searchIndexRepository;

    /**
     * 검색어로 시작하는 곳을 먼저, 모자라면 검색어가 들어 있는 곳으로 채워 10곳까지 돌려줍니다.
     *
     * 검색어가 비어 있으면 빈 목록입니다. 입력칸을 다 지웠을 때도 부르므로 400 으로 답하지 않습니다.
     */
    public List<SuggestionOutput> suggest(String q) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            return List.of();
        }
        return searchIndexRepository.suggest(query, LIMIT).stream()
                .map(SuggestionOutput::from)
                .toList();
    }
}
