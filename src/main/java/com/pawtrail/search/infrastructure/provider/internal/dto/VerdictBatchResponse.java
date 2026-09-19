package com.pawtrail.search.infrastructure.provider.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pawtrail.search.domain.enums.Verdict;
import com.pawtrail.search.domain.model.PetVerdict;
import com.pawtrail.search.domain.model.PlaceVerdict;
import java.util.List;
import java.util.UUID;

/**
 * verdict 의 POST /internal/verdicts/batch 가 돌려주는 data 입니다.
 *
 * verdict 의 VerdictBatchOutput · PlaceVerdictSummary · PetVerdictValue 와 칸이 같습니다.
 * @JsonIgnoreProperties(ignoreUnknown = true) 가 필수입니다. verdict 가 칸을 더해도 여기서 깨지지 않게 하려는 것입니다.
 *
 * @param results 요청한 장소 차례대로의 판정입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VerdictBatchResponse(List<Result> results) {

    /**
     * 장소 한 곳의 판정입니다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(UUID placeId,
                         boolean hasConflict,
                         List<PetValue> verdicts,
                         String evidenceSummary,
                         List<String> requiredItems) {

        public PlaceVerdict toPlaceVerdict() {
            List<PetVerdict> pets = verdicts == null
                    ? List.of()
                    : verdicts.stream()
                            .filter(value -> value != null && value.petId() != null && value.verdict() != null)
                            .map(value -> new PetVerdict(value.petId(), value.verdict()))
                            .toList();
            return new PlaceVerdict(placeId, hasConflict, pets, evidenceSummary, requiredItems);
        }
    }

    /**
     * 반려동물 한 마리의 판정입니다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PetValue(UUID petId, Verdict verdict) {
    }
}
