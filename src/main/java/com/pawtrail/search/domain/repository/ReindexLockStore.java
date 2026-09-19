package com.pawtrail.search.domain.repository;

import java.util.Optional;

/**
 * 전량 재색인이 한 번에 하나만 돌게 하는 잠금입니다.
 *
 * search 는 두 대로 뜹니다. 두 대가 각자 매일 스케줄을 돌리거나
 * 관리자 요청이 돌고 있지 않은 대로 가면 재색인이 겹칩니다.
 * 잠금을 대 사이에 나눠 가져야 하므로 한 서버의 메모리가 아니라 바깥 저장소에 둡니다.
 *
 * 잠금에는 만료가 있습니다.
 * 재색인 중에 프로세스가 죽으면 풀어 줄 사람이 없는데, 만료가 지나면 저절로 풀립니다.
 * 사람 없이 매일 도는 작업이라 멈춘 채로 남으면 아무도 모르기 때문입니다.
 */
public interface ReindexLockStore {

    /**
     * 잠금을 잡습니다.
     *
     * @return 잡았으면 풀 때 쓸 표입니다. 누가 이미 잡고 있으면 비어 있습니다.
     */
    Optional<String> tryAcquire();

    /**
     * 자기가 잡은 잠금만 풉니다.
     *
     * 오래 걸려 만료로 이미 풀렸고 그 사이 다른 대가 잡았다면 그 잠금은 건드리지 않습니다.
     */
    void release(String token);
}
