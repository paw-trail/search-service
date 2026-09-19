package com.pawtrail.search.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.search.application.dto.output.ReindexTriggerOutput;
import com.pawtrail.search.application.service.ReindexTriggerService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 관리자 재색인이 202 로 받은 시각을 돌려주는지 검사합니다.
 *
 * 컨트롤러를 직접 부릅니다. 409 는 서비스가 던지는 예외를 common 이 바꾸므로 여기서 보지 않습니다.
 */
@ExtendWith(MockitoExtension.class)
class AdminSearchControllerTest {

    @Mock
    private ReindexTriggerService reindexTriggerService;

    @InjectMocks
    private AdminSearchController controller;

    @Test
    @DisplayName("202 와 함께 요청을 받은 시각을 돌려준다")
    void 받았다고_답한다() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 19, 23, 50);
        when(reindexTriggerService.start()).thenReturn(new ReindexTriggerOutput(startedAt));

        ResponseEntity<CommonApiResponse<ReindexTriggerOutput>> response = controller.reindex();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().getData().startedAt()).isEqualTo(startedAt);
    }
}
