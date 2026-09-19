package com.pawtrail.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pawtrail.search.application.dto.output.RegionOutput;
import com.pawtrail.search.application.dto.output.RegionOutput.SigunguOutput;
import com.pawtrail.search.domain.model.RegionCount;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 지역 목록이 시도로 묶이고 이름이 붙는지 검사합니다.
 */
@ExtendWith(MockitoExtension.class)
class RegionServiceTest {

    @Mock
    private SearchIndexRepository searchIndexRepository;

    @InjectMocks
    private RegionService regionService;

    @Test
    @DisplayName("시도는 관례 차례로 묶어 짧은 이름을 붙이고, 세종은 시군구 없이 시도 수만 센다")
    void 시도로_묶는다() {
        when(searchIndexRepository.countByRegion()).thenReturn(List.of(
                new RegionCount("11", "마포구", 3),
                new RegionCount("11", "영등포구", 2),
                new RegionCount("36", null, 4),
                new RegionCount("51", "춘천시", 1)));

        List<RegionOutput> regions = regionService.regions();

        // 강원(51)이 세종(36) 뒤 · 경기 자리 다음 — 코드 숫자 차례가 아니라 행정 목록 차례
        assertThat(regions).extracting(RegionOutput::sidoName).containsExactly("서울", "세종", "강원");
        assertThat(regions.get(0).placeCount()).isEqualTo(5L);
        assertThat(regions.get(0).sigungus()).containsExactly(new SigunguOutput("마포구", 3), new SigunguOutput("영등포구", 2));
        assertThat(regions.get(1).sigungus()).isEmpty();
        assertThat(regions.get(1).placeCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("표에 없는 시도 코드는 목록에서 뺀다")
    void 모르는_시도() {
        when(searchIndexRepository.countByRegion()).thenReturn(List.of(new RegionCount("99", "어딘가", 1)));

        assertThat(regionService.regions()).isEmpty();
    }
}
