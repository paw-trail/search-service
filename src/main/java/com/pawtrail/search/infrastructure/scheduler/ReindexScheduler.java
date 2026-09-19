package com.pawtrail.search.infrastructure.scheduler;

import com.pawtrail.search.application.service.ReindexTriggerService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 한 번 전량 재색인을 겁니다.
 *
 * 시각은 config 의 app.search.reindex.cron 이고 없으면 매일 04:00 입니다.
 * 사람이 거의 없는 새벽이고, 한 바퀴가 오래 걸리지 않습니다.
 *
 * 두 대가 같은 시각에 이 메서드를 부르지만 한 대만 잠금을 잡아 돌고 나머지는 건너뜁니다.
 * 스케줄은 common 이 켜 두었습니다.
 */
@Component
@RequiredArgsConstructor
public class ReindexScheduler {

    private final ReindexTriggerService reindexTriggerService;

    @Scheduled(cron = "${app.search.reindex.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void reindexDaily() {
        reindexTriggerService.runScheduled();
    }
}
