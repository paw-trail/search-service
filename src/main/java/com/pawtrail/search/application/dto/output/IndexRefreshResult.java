package com.pawtrail.search.application.dto.output;

/**
 * 색인을 다시 읽어 덮어쓴 결과입니다. 로그와 시험이 읽습니다.
 *
 * @param requested 다시 읽기로 한 장소 수입니다. 중복과 null 을 거른 뒤입니다.
 * @param received  place 가 돌려준 장소 수입니다. place 에 없는 식별자는 빠집니다.
 * @param skipped   필수 칸이 비어 넣지 않은 장소 수입니다.
 * @param written   실제로 넣거나 덮어쓴 행 수입니다.
 *                  수정 시각이 같거나 옛것이면 세지 않으므로 받은 수보다 작을 수 있습니다.
 */
public record IndexRefreshResult(int requested, int received, int skipped, int written) {

    public static final IndexRefreshResult EMPTY = new IndexRefreshResult(0, 0, 0, 0);
}
