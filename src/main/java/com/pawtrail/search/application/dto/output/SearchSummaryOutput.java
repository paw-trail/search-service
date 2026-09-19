package com.pawtrail.search.application.dto.output;

/**
 * 탐색 카운트입니다. 같은 조건에서 장소 판정별 장소 수입니다(search ㉤).
 *
 * @param total       조건에 맞는 장소 수입니다.
 * @param allowed     모든 반려동물이 들어갈 수 있는 곳입니다.
 * @param conditional 가장 막히는 마리가 조건부인 곳입니다.
 * @param notAllowed  한 마리라도 못 들어가는 곳입니다.
 * @param unknown     가장 막히는 마리가 확인 필요인 곳입니다.
 */
public record SearchSummaryOutput(long total, long allowed, long conditional, long notAllowed, long unknown) {
}
