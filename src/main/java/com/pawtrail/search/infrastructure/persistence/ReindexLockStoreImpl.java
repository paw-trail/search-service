package com.pawtrail.search.infrastructure.persistence;

import com.pawtrail.search.domain.repository.ReindexLockStore;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * 재색인 잠금을 Redis 키 하나로 둡니다.
 *
 * 잡을 때는 "없을 때만 넣는다" 를 만료와 함께 한 번에 합니다 (SET NX + 만료).
 * user 의 하루 요약 쿨다운(SummaryRateLimitStoreImpl)과 같은 방식입니다.
 *
 * 풀 때는 자기가 넣은 표일 때만 지웁니다.
 * 재색인이 만료보다 오래 걸려 잠금이 풀렸고 그 사이 다른 대가 잡았다면,
 * 그냥 지우면 남의 잠금을 풀게 되어 셋째 실행이 겹쳐 들어옵니다.
 * 확인과 지우기를 한 번에 하려고 Lua 스크립트를 씁니다.
 *
 * 만료는 config 의 app.search.reindex.lock-ttl 이고 없으면 30분입니다.
 */
@Slf4j
@Repository
public class ReindexLockStoreImpl implements ReindexLockStore {

    private static final String KEY = "search:reindex:lock";

    // 값이 내 표일 때만 지움, 아니면 0
    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public ReindexLockStoreImpl(StringRedisTemplate redisTemplate,
                                @Value("${app.search.reindex.lock-ttl:30m}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public Optional<String> tryAcquire() {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(KEY, token, ttl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    @Override
    public void release(String token) {
        try {
            Long released = redisTemplate.execute(RELEASE, List.of(KEY), token);
            if (released == null || released == 0L) {
                // 만료로 이미 풀렸거나 다른 대가 잡은 뒤 — 건드리지 않음
                log.warn("재색인 잠금이 이미 풀려 있었습니다. 실행이 만료({})보다 오래 걸렸을 수 있습니다", ttl);
            }
        } catch (RuntimeException e) {
            // 풀지 못해도 만료가 지나면 저절로 풀림, 재색인 결과를 실패로 바꾸지 않음
            log.warn("재색인 잠금을 풀지 못했습니다. 만료({}) 뒤에 저절로 풀립니다: reason={}", ttl, e.getMessage());
        }
    }
}
