package com.pawtrail.search.application.service;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.search.application.dto.output.ReindexTriggerOutput;
import com.pawtrail.search.domain.enums.ReindexTrigger;
import com.pawtrail.search.domain.exception.SearchErrorCode;
import com.pawtrail.search.domain.repository.ReindexLockStore;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 전량 재색인의 두 문입니다. 매일 스케줄과 관리자 요청이 같은 작업을 겁니다.
 *
 * 두 문 모두 먼저 잠금을 잡습니다.
 * search 는 두 대로 떠서 각자의 스케줄이 같은 시각에 돌고, 관리자 요청도 어느 대로 갈지 모릅니다.
 * 잠금을 못 잡으면 스케줄은 조용히 건너뛰고, 관리자 요청은 409 로 답합니다.
 *
 * ingest · extract 는 한 대로 떠서 메모리의 표시(AtomicBoolean)로 막았습니다.
 * 여기는 대 사이에 나눠 가져야 해서 Redis 잠금을 씁니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReindexTriggerService {

    private final ReindexLockStore reindexLockStore;
    private final ReindexExecutor reindexExecutor;
    private final ReindexRunner reindexRunner;

    /**
     * 관리자가 건 재색인을 받습니다. 잠금을 잡으면 뒤에서 돌리고 바로 돌아갑니다.
     *
     * @throws CustomException REINDEX_ALREADY_RUNNING — 어느 대에서든 이미 돌고 있을 때
     */
    public ReindexTriggerOutput start() {
        String token = reindexLockStore.tryAcquire()
                .orElseThrow(() -> new CustomException(SearchErrorCode.REINDEX_ALREADY_RUNNING));

        LocalDateTime startedAt = LocalDateTime.now();
        try {
            reindexExecutor.execute(ReindexTrigger.ADMIN, () -> reindexLockStore.release(token));
        } catch (RuntimeException e) {
            // 실행을 넘기지도 못했으면 잠금을 쥔 채로 남지 않게 여기서 풂
            reindexLockStore.release(token);
            throw e;
        }

        log.info("재색인을 받았습니다 (관리자). 시작={}", startedAt);
        return new ReindexTriggerOutput(startedAt);
    }

    /**
     * 매일 스케줄이 부릅니다. 잠금을 잡으면 그 자리에서 끝까지 돌립니다.
     *
     * 못 잡으면 건너뜁니다. 다른 대의 같은 스케줄이나 관리자 요청이 이미 돌고 있다는 뜻입니다.
     */
    public void runScheduled() {
        Optional<String> token = reindexLockStore.tryAcquire();
        if (token.isEmpty()) {
            log.info("재색인이 이미 돌고 있어 이번 스케줄은 건너뜁니다");
            return;
        }

        try {
            reindexRunner.run(ReindexTrigger.SCHEDULE);
        } finally {
            reindexLockStore.release(token.get());
        }
    }
}
