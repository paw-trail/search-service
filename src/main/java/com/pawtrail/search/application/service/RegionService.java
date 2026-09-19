package com.pawtrail.search.application.service;

import com.pawtrail.search.application.dto.output.RegionOutput;
import com.pawtrail.search.application.dto.output.RegionOutput.SigunguOutput;
import com.pawtrail.search.domain.enums.Sido;
import com.pawtrail.search.domain.model.RegionCount;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 지역 목록입니다. 검색 화면의 시도 · 시군구 고르기가 씁니다(search ㉱).
 *
 * 색인에서 셉니다. 그래서 장소가 없는 지역은 목록에 나오지 않고, 고른 뒤 결과가 0곳인 일이 없습니다.
 * 목록이 place 의 시군구 이름과 늘 같아 화면에 지역 표를 따로 박아 두지 않아도 됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionService {

    private final SearchIndexRepository searchIndexRepository;

    public List<RegionOutput> regions() {
        Map<String, List<RegionCount>> bySido = new LinkedHashMap<>();
        for (RegionCount count : searchIndexRepository.countByRegion()) {
            bySido.computeIfAbsent(count.sidoCode(), code -> new ArrayList<>()).add(count);
        }

        List<RegionOutput> regions = new ArrayList<>();
        // 시도는 행정 목록의 관례 차례로 — Sido 선언 차례
        for (Sido sido : Sido.values()) {
            List<RegionCount> counts = bySido.remove(sido.code());
            if (counts == null) {
                continue;
            }
            long total = counts.stream().mapToLong(RegionCount::placeCount).sum();
            // 세종처럼 시군구가 없는 장소는 시도 수에만 들어가고 시군구 목록에는 안 나옴
            List<SigunguOutput> sigungus = counts.stream()
                    .filter(count -> count.sigunguName() != null)
                    .map(count -> new SigunguOutput(count.sigunguName(), count.placeCount()))
                    .toList();
            regions.add(new RegionOutput(sido.code(), sido.displayName(), total, sigungus));
        }

        if (!bySido.isEmpty()) {
            // place 에 시도가 새로 생기면 여기서 드러남 — 표에 더할 때까지 목록에서 빠짐
            log.warn("지역 목록이 모르는 시도 코드를 건너뜁니다: {}", bySido.keySet());
        }
        return regions;
    }
}
