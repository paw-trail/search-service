package com.pawtrail.search.infrastructure.persistence;

import com.pawtrail.search.domain.repository.TrendingStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 장소 조회수를 Redis 정렬 집합에 둡니다. 원소는 장소 식별자, 점수는 누적 조회수입니다.
 *
 * 열쇠
 *   search:trending:all          전국
 *   search:trending:sido:{코드}   시도마다 — 「내 주변 인기 급상승」 이 시도로 거름
 *
 * 만료를 두지 않습니다. 기간을 자르지 않은 누적 조회수라 지울 때가 없습니다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TrendingStoreImpl implements TrendingStore {

    private static final String ALL = "search:trending:all";
    private static final String SIDO_PREFIX = "search:trending:sido:";

    // 한 번에 점수를 묻는 원소 수 — 후보가 수만 곳이어도 명령 하나가 너무 커지지 않게 나눔
    private static final int SCORE_CHUNK = 1_000;

    private final StringRedisTemplate redisTemplate;

    @Override
    public void increment(UUID placeId, String sidoCode) {
        String member = placeId.toString();
        redisTemplate.opsForZSet().incrementScore(ALL, member, 1);
        if (sidoCode != null && !sidoCode.isBlank()) {
            redisTemplate.opsForZSet().incrementScore(SIDO_PREFIX + sidoCode, member, 1);
        }
    }

    @Override
    public List<UUID> top(String sidoCode, int size) {
        String key = sidoCode == null || sidoCode.isBlank() ? ALL : SIDO_PREFIX + sidoCode;
        Set<String> members = redisTemplate.opsForZSet().reverseRange(key, 0, size - 1L);
        if (members == null) {
            return List.of();
        }
        List<UUID> placeIds = new ArrayList<>(members.size());
        for (String member : members) {
            UUID placeId = parse(member);
            if (placeId != null) {
                placeIds.add(placeId);
            }
        }
        return placeIds;
    }

    @Override
    public Map<UUID, Double> scoresOf(Collection<UUID> placeIds) {
        List<UUID> ids = List.copyOf(placeIds);
        Map<UUID, Double> scores = new HashMap<>();
        for (int from = 0; from < ids.size(); from += SCORE_CHUNK) {
            List<UUID> chunk = ids.subList(from, Math.min(from + SCORE_CHUNK, ids.size()));
            // ZMSCORE — 없는 원소는 null 로 옴
            List<Double> chunkScores = redisTemplate.opsForZSet().score(ALL, chunk.stream().map(UUID::toString).toArray());
            if (chunkScores == null) {
                continue;
            }
            for (int i = 0; i < chunk.size() && i < chunkScores.size(); i++) {
                if (chunkScores.get(i) != null) {
                    scores.put(chunk.get(i), chunkScores.get(i));
                }
            }
        }
        return scores;
    }

    private static UUID parse(String member) {
        try {
            return UUID.fromString(member);
        } catch (IllegalArgumentException e) {
            log.warn("조회수 원소가 장소 식별자가 아닙니다: {}", member);
            return null;
        }
    }
}
