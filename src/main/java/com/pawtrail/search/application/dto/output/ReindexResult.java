package com.pawtrail.search.application.dto.output;

import com.pawtrail.search.domain.enums.ReindexTrigger;

/**
 * 전량 재색인 한 번의 결과입니다. 끝 요약 로그와 시험이 읽습니다.
 *
 * @param trigger        누가 걸었는지입니다.
 * @param completed      place 를 끝까지 읽었는지입니다. place 를 못 불러 멈추면 false 이고 평점 · 행 세기를 건너뜁니다.
 * @param pages          place 에서 받은 쪽 수입니다.
 * @param read           place 에서 받은 장소 수입니다.
 * @param skipped        필수 칸이 비어 넣지 않은 장소 수입니다.
 * @param written        넣거나 덮어쓴 행 수입니다. place 수정 시각이 같으면 세지 않아 평소엔 작습니다.
 * @param ratingsChanged 평점이 바뀐 장소 수입니다.
 * @param ratingsKept    review 를 못 불러 평점을 그대로 둔 장소가 있는지입니다.
 * @param outside        색인에만 있고 이번 바퀴에 place 가 주지 않은 행 수입니다. 멈췄으면 -1 입니다.
 * @param elapsedMillis  걸린 시간입니다.
 */
public record ReindexResult(ReindexTrigger trigger,
                            boolean completed,
                            int pages,
                            int read,
                            int skipped,
                            int written,
                            int ratingsChanged,
                            boolean ratingsKept,
                            long outside,
                            long elapsedMillis) {
}
