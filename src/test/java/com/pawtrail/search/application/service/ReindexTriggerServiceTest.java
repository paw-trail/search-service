package com.pawtrail.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.search.application.dto.output.ReindexTriggerOutput;
import com.pawtrail.search.domain.enums.ReindexTrigger;
import com.pawtrail.search.domain.exception.SearchErrorCode;
import com.pawtrail.search.domain.repository.ReindexLockStore;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 재색인의 두 문이 잠금을 어떻게 다루는지 검사합니다.
 *
 * 잠금이 새면 재색인이 겹치고, 풀리지 않으면 만료까지 아무도 재색인을 못 겁니다.
 * 그래서 성공 · 거절 · 실패 모든 길에서 잠금이 풀리는지를 봅니다.
 * 실제 Redis 로 잡고 푸는 것은 실물 확인에서 봅니다.
 */
@ExtendWith(MockitoExtension.class)
class ReindexTriggerServiceTest {

    private static final String TOKEN = "잠금 표";

    @Mock
    private ReindexLockStore reindexLockStore;

    @Mock
    private ReindexExecutor reindexExecutor;

    @Mock
    private ReindexRunner reindexRunner;

    @InjectMocks
    private ReindexTriggerService reindexTriggerService;

    @Captor
    private ArgumentCaptor<Runnable> onFinishCaptor;

    @Test
    @DisplayName("관리자 — 잠금을 잡으면 뒤로 넘기고, 끝났을 때 푼다")
    void 관리자_시작() {
        when(reindexLockStore.tryAcquire()).thenReturn(Optional.of(TOKEN));

        ReindexTriggerOutput output = reindexTriggerService.start();

        verify(reindexExecutor).execute(eq(ReindexTrigger.ADMIN), onFinishCaptor.capture());
        // 돌려주는 시점에는 아직 돌고 있으므로 풀면 안 됨
        verify(reindexLockStore, never()).release(any());
        onFinishCaptor.getValue().run();
        verify(reindexLockStore).release(TOKEN);
        assertThat(output.startedAt()).isNotNull();
    }

    @Test
    @DisplayName("관리자 — 어느 대에서든 이미 돌고 있으면 409 이고 아무것도 넘기지 않는다")
    void 관리자_이미_도는_중() {
        when(reindexLockStore.tryAcquire()).thenReturn(Optional.empty());

        assertThatThrownBy(reindexTriggerService::start)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(SearchErrorCode.REINDEX_ALREADY_RUNNING);

        verifyNoInteractions(reindexExecutor, reindexRunner);
    }

    @Test
    @DisplayName("관리자 — 실행을 넘기지 못하면 잠금을 풀고 예외를 올린다")
    void 관리자_넘기기_실패() {
        when(reindexLockStore.tryAcquire()).thenReturn(Optional.of(TOKEN));
        doThrow(new IllegalStateException("실행기가 거절함")).when(reindexExecutor).execute(any(), any());

        assertThatThrownBy(reindexTriggerService::start).isInstanceOf(IllegalStateException.class);

        // 안 풀면 만료 30분 동안 아무도 재색인을 못 검
        verify(reindexLockStore).release(TOKEN);
    }

    @Test
    @DisplayName("스케줄 — 잠금이 잡혀 있으면 조용히 건너뛴다")
    void 스케줄_건너뜀() {
        when(reindexLockStore.tryAcquire()).thenReturn(Optional.empty());

        reindexTriggerService.runScheduled();

        verifyNoInteractions(reindexRunner);
    }

    @Test
    @DisplayName("스케줄 — 돌다 터져도 잠금을 푼다")
    void 스케줄_실패해도_푼다() {
        when(reindexLockStore.tryAcquire()).thenReturn(Optional.of(TOKEN));
        when(reindexRunner.run(ReindexTrigger.SCHEDULE)).thenThrow(new IllegalStateException("터짐"));

        assertThatThrownBy(reindexTriggerService::runScheduled).isInstanceOf(IllegalStateException.class);

        verify(reindexLockStore).release(TOKEN);
    }
}
