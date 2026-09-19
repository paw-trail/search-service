package com.pawtrail.search.domain.model;

import com.pawtrail.search.domain.enums.Verdict;
import java.util.UUID;

/**
 * 반려동물 한 마리의 판정입니다.
 */
public record PetVerdict(UUID petId, Verdict verdict) {
}
