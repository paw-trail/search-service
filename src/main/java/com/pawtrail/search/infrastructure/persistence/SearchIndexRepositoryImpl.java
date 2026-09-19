package com.pawtrail.search.infrastructure.persistence;

import com.pawtrail.common.audit.AuditorProvider;
import com.pawtrail.search.domain.model.IndexedPlace;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검색 색인을 JDBC 로 저장합니다.
 *
 * JPA 엔티티를 두지 않았습니다.
 * 이 표를 다루는 일이 전부 SQL 이 주인공입니다. 쓰기는 조건부 넣기(ON CONFLICT)이고
 * 읽기는 조건을 조립하는 검색 SQL 이라, 엔티티를 저장하거나 불러오는 자리가 없습니다.
 * 소개문 칸(tsvector)은 JPA 가 모르는 타입이라 엔티티에 넣을 수도 없습니다.
 *
 * 감사 칸은 SQL 이 직접 채웁니다.
 * JPA 감사가 돌지 않으므로 AuditorProvider 에 행위자를 물어 넣습니다.
 * 이벤트와 스케줄에서 불리므로 값은 늘 SYSTEM 입니다.
 */
@Repository
@RequiredArgsConstructor
public class SearchIndexRepositoryImpl implements SearchIndexRepository {

    // 처음 보는 장소는 넣고, 있는 장소는 place 수정 시각이 더 새로울 때만 덮어씀
    //
    // WHERE 가 DO UPDATE 에 붙어 있어 조건이 거짓이면 그 행은 아무것도 안 바뀌고 0 행으로 셈
    // 같은 시각도 덮어쓰지 않음 — 같은 이벤트를 두 번 받은 경우라 바꿀 것이 없음
    //
    // 평점(rating_avg · review_count)과 created_* 는 SET 에 없음
    // 평점은 재색인이 따로 채우고, 이벤트로 장소를 다시 읽을 때 지우면 안 됨
    //
    // 배열 칸은 CAST 로 타입을 밝힘
    // :: 캐스트를 쓰면 이름 붙은 매개변수의 콜론과 헷갈려 CAST 로 씀
    private static final String UPSERT = """
            INSERT INTO search_index (
                place_id, name, name_alias, place_type, address_road, address_jibun,
                sido_code, sigungu_name, geom, facility_codes, status, image_url,
                search_text, data_base_date, place_updated_at, indexed_at,
                created_at, created_by, updated_at, updated_by)
            VALUES (
                :placeId, :name, CAST(:nameAlias AS text[]), :placeType, :addressRoad, :addressJibun,
                :sidoCode, :sigunguName,
                CAST(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS geography),
                CAST(:facilityCodes AS text[]), :status, :imageUrl,
                to_tsvector('simple', :searchSource), :dataBaseDate, :placeUpdatedAt, :now,
                :now, :actor, :now, :actor)
            ON CONFLICT (place_id) DO UPDATE SET
                name             = EXCLUDED.name,
                name_alias       = EXCLUDED.name_alias,
                place_type       = EXCLUDED.place_type,
                address_road     = EXCLUDED.address_road,
                address_jibun    = EXCLUDED.address_jibun,
                sido_code        = EXCLUDED.sido_code,
                sigungu_name     = EXCLUDED.sigungu_name,
                geom             = EXCLUDED.geom,
                facility_codes   = EXCLUDED.facility_codes,
                status           = EXCLUDED.status,
                image_url        = EXCLUDED.image_url,
                search_text      = EXCLUDED.search_text,
                data_base_date   = EXCLUDED.data_base_date,
                place_updated_at = EXCLUDED.place_updated_at,
                indexed_at       = EXCLUDED.indexed_at,
                updated_at       = EXCLUDED.updated_at,
                updated_by       = EXCLUDED.updated_by
            WHERE search_index.place_updated_at < EXCLUDED.place_updated_at
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AuditorProvider auditorProvider;

    @Override
    @Transactional
    public int saveAllIfNewer(List<IndexedPlace> places) {
        if (places.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        String actor = auditorProvider.current();
        SqlParameterSource[] batch = places.stream()
                .map(place -> parameters(place, now, actor))
                .toArray(SqlParameterSource[]::new);

        int written = 0;
        for (int count : jdbcTemplate.batchUpdate(UPSERT, batch)) {
            // 드라이버가 건수를 모른다고 답하면(음수) 셀 수 없으므로 세지 않음
            if (count > 0) {
                written += count;
            }
        }
        return written;
    }

    private static SqlParameterSource parameters(IndexedPlace place, LocalDateTime now, String actor) {
        return new MapSqlParameterSource()
                .addValue("placeId", place.placeId())
                .addValue("name", place.name())
                .addValue("nameAlias", place.nameAlias().toArray(String[]::new))
                .addValue("placeType", place.placeType())
                .addValue("addressRoad", place.addressRoad())
                .addValue("addressJibun", place.addressJibun())
                .addValue("sidoCode", place.sidoCode())
                .addValue("sigunguName", place.sigunguName())
                .addValue("lat", place.lat())
                .addValue("lon", place.lon())
                .addValue("facilityCodes", place.facilities().toArray(String[]::new))
                .addValue("status", place.status())
                .addValue("imageUrl", place.imageUrl())
                .addValue("searchSource", place.searchSource())
                .addValue("dataBaseDate", place.dataBaseDate())
                .addValue("placeUpdatedAt", place.placeUpdatedAt())
                .addValue("now", now)
                .addValue("actor", actor);
    }
}
