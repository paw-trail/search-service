package com.pawtrail.search.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.common.response.PageResponse;
import com.pawtrail.search.application.dto.input.SearchCommand;
import com.pawtrail.search.application.dto.output.SearchCardOutput;
import com.pawtrail.search.application.service.SearchService;
import com.pawtrail.search.domain.enums.SearchSort;
import com.pawtrail.search.domain.model.SearchFilter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 검색 파라미터가 조건으로 바뀌는 자리를 검사합니다.
 *
 * 컨트롤러를 직접 부릅니다. 서비스는 흉내 내고 넘겨받은 조건만 봅니다.
 * 위치 없이 부르는 요청이 가장 흔한데, 반경 기본값을 채우는 줄이 그 요청에서 터졌던 적이 있어
 * 위치가 없을 때 · 위치만 있을 때를 나눠 봅니다.
 */
@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    private static final PageResponse<SearchCardOutput> EMPTY =
            new PageResponse<>(List.of(), new PageResponse.PageInfo(0, 20, 0, 0));

    @Mock
    private SearchService searchService;

    @InjectMocks
    private SearchController controller;

    @Captor
    private ArgumentCaptor<SearchCommand> commandCaptor;

    @Test
    @DisplayName("위치 없이 부르면 반경을 비워 두고, 검색어는 띄어쓰기로 나눈다")
    void 위치_없이() {
        when(searchService.search(any())).thenReturn(EMPTY);

        controller.search("  한강   공원 ", null, null, null, null, null, null, null, null, null, null, 0, 20);

        verify(searchService).search(commandCaptor.capture());
        SearchFilter filter = commandCaptor.getValue().filter();
        assertThat(filter.words()).containsExactly("한강", "공원");
        assertThat(filter.radiusM()).isNull();
        assertThat(commandCaptor.getValue().sort()).isNull();
    }

    @Test
    @DisplayName("위치만 주고 반경을 안 주면 명세 기본값 20km 를 쓴다")
    void 위치만() {
        when(searchService.search(any())).thenReturn(EMPTY);

        controller.search(null, null, null, 37.5285, 126.9327, null, null, null, null, null, "distance", 0, 20);

        verify(searchService).search(commandCaptor.capture());
        assertThat(commandCaptor.getValue().filter().radiusM()).isEqualTo(20_000);
        assertThat(commandCaptor.getValue().sort()).isEqualTo(SearchSort.DISTANCE);
    }

    @Test
    @DisplayName("모르는 정렬 값은 서비스까지 가지 않고 400 이다")
    void 모르는_정렬() {
        assertThatThrownBy(() -> controller.search(null, null, null, null, null, null, null, null, null, null,
                "cheap", 0, 20))
                .isInstanceOf(CustomException.class);

        verifyNoInteractions(searchService);
    }
}
