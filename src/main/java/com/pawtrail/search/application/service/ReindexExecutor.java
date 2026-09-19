package com.pawtrail.search.application.service;

import com.pawtrail.search.domain.enums.ReindexTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 관리자가 건 재색인을 뒤에서 돌립니다.
 *
 * 트리거 서비스와 나눠 둔 이유는 @Async 가 같은 객체 안의 호출에는 걸리지 않기 때문입니다.
 * 한 클래스에 두면 관리자 요청이 재색인이 끝날 때까지 막힙니다.
 * extract 의 ExtractTriggerService · ExtractExecutor, ingest 의 IngestTriggerService · IngestExecutor 와 같은 나눔입니다.
 *
 * 비동기는 common 이 켜 두었고 스프링 부트의 applicationTaskExecutor 에서 돕니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReindexExecutor {

    private final ReindexRunner reindexRunner;

    /**
     * 재색인을 돌리고, 끝나면 성공이든 실패든 onFinish 를 부릅니다. 잠금을 푸는 자리입니다.
     */
    @Async
    public void execute(ReindexTrigger trigger, Runnable onFinish) {
        try {
            reindexRunner.run(trigger);
        } catch (RuntimeException e) {
            // 실행이 잡지 못한 예외 — 끝 요약 로그가 안 남으므로 여기서 남김
            log.error("재색인이 예상하지 못한 예외로 끝났습니다 ({})", trigger, e);
        } finally {
            onFinish.run();
        }
    }
}
