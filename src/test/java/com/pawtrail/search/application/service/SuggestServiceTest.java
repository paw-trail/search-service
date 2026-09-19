package com.pawtrail.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawtrail.search.application.dto.output.SuggestionOutput;
import com.pawtrail.search.domain.model.Suggestion;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 자동완성의 입력 다듬기를 검사합니다. 찾는 SQL 은 SearchQueryTest 가 봅니다.
 */
@ExtendWith(MockitoExtension.class)
class SuggestServiceTest {

    @Mock
    private SearchIndexRepository searchIndexRepository;

    @InjectMocks
    private SuggestService suggestService;

    @Test
    @DisplayName("검색어가 비었으면 색인을 부르지 않고 빈 목록이다 — 입력칸을 다 지웠을 때도 부르므로")
    void 빈_검색어() {
        assertThat(suggestService.suggest(null)).isEmpty();
        assertThat(suggestService.suggest("   ")).isEmpty();

        verifyNoInteractions(searchIndexRepository);
    }

    @Test
    @DisplayName("앞뒤 공백을 걷고 10곳까지 묻는다")
    void 공백을_걷는다() {
        UUID placeId = UUID.randomUUID();
        when(searchIndexRepository.suggest("노들", 10))
                .thenReturn(List.of(new Suggestion(placeId, "노들섬", "ETC", "용산구")));

        assertThat(suggestService.suggest("  노들 "))
                .containsExactly(new SuggestionOutput(placeId, "노들섬", "ETC", "용산구"));
    }
}
