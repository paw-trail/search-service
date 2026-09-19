package com.pawtrail.search.domain.enums;

/**
 * verdict 가 내는 판정입니다. verdict 의 열거와 이름 · 순서가 같습니다.
 *
 * 막히는 쪽부터 선언합니다.
 * 여러 마리를 데려갈 때 장소의 판정은 가장 막히는 마리의 판정이고(search ㉤),
 * 선언 순서가 곧 그 비교 순서입니다.
 */
public enum Verdict {

    // 들어갈 수 없음
    NOT_ALLOWED,

    // 판정에 필요한 정보가 비어 있음 — 화면 문구는 "확인 필요"
    UNKNOWN,

    // 들어갈 수 있으나 지킬 것이 있음
    CONDITIONAL,

    // 들어갈 수 있음
    ALLOWED;

    /**
     * 둘 중 더 막히는 판정입니다.
     */
    public Verdict stricter(Verdict other) {
        return compareTo(other) <= 0 ? this : other;
    }
}
