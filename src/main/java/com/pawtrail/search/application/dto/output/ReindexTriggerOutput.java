package com.pawtrail.search.application.dto.output;

import java.time.LocalDateTime;

/**
 * 관리자 재색인 요청에 돌려주는 값입니다.
 *
 * 결과가 아니라 "받았다" 는 표시입니다. 재색인은 뒤에서 돌고 결과는 끝 요약 로그로 남습니다.
 *
 * @param startedAt 요청을 받은 시각입니다. 로그에서 이 실행을 찾을 때 씁니다.
 */
public record ReindexTriggerOutput(LocalDateTime startedAt) {
}
