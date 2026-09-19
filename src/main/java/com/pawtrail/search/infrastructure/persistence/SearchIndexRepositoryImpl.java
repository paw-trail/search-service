package com.pawtrail.search.infrastructure.persistence;

import com.pawtrail.common.audit.AuditorProvider;
import com.pawtrail.search.domain.enums.SearchSort;
import com.pawtrail.search.domain.model.IndexedCard;
import com.pawtrail.search.domain.model.IndexedPlace;
import com.pawtrail.search.domain.model.RegionCount;
import com.pawtrail.search.domain.model.ReviewStats;
import com.pawtrail.search.domain.model.SearchFilter;
import com.pawtrail.search.domain.model.Suggestion;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    // 평점만 갈아 끼움, place 에서 온 칸과 place 수정 시각은 SET 에 없음
    // 값이 같으면 쓰지 않아 바뀐 곳만 셈 — IS DISTINCT FROM 은 null 끼리도 같다고 봄
    private static final String UPDATE_REVIEW_STATS = """
            UPDATE search_index
            SET rating_avg   = :ratingAvg,
                review_count = :reviewCount,
                updated_at   = :now,
                updated_by   = :actor
            WHERE place_id = :placeId
              AND (rating_avg IS DISTINCT FROM :ratingAvg OR review_count <> :reviewCount)
            """;

    // 한 바퀴 동안 본 장소 밖의 행을 셈, 지우지 않음
    // 식별자를 글자 배열로 넘기고 SQL 이 uuid 배열로 바꿈
    private static final String COUNT_OUTSIDE = """
            SELECT count(*) FROM search_index
            WHERE NOT (place_id = ANY(CAST(:placeIds AS uuid[])))
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

    @Override
    @Transactional
    public int updateReviewStats(List<ReviewStats> stats) {
        if (stats.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        String actor = auditorProvider.current();
        SqlParameterSource[] batch = stats.stream()
                .map(stat -> new MapSqlParameterSource()
                        .addValue("placeId", stat.placeId())
                        .addValue("ratingAvg", stat.ratingAvg())
                        .addValue("reviewCount", stat.reviewCount())
                        .addValue("now", now)
                        .addValue("actor", actor))
                .toArray(SqlParameterSource[]::new);

        int changed = 0;
        for (int count : jdbcTemplate.batchUpdate(UPDATE_REVIEW_STATS, batch)) {
            if (count > 0) {
                changed += count;
            }
        }
        return changed;
    }

    @Override
    @Transactional(readOnly = true)
    public long countOutside(Collection<UUID> placeIds) {
        String[] ids = placeIds.stream().map(UUID::toString).toArray(String[]::new);
        Long count = jdbcTemplate.queryForObject(COUNT_OUTSIDE, new MapSqlParameterSource("placeIds", ids), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findIds(SearchFilter filter, SearchSort sort, int offset, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT place_id FROM search_index WHERE " + SearchSql.where(filter, params)
                + " ORDER BY " + SearchSql.orderBy(sort)
                + " LIMIT :limit OFFSET :offset";
        params.addValue("limit", limit).addValue("offset", offset);
        return jdbcTemplate.queryForList(sql, params, UUID.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findAllIds(SearchFilter filter, SearchSort sort) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT place_id FROM search_index WHERE " + SearchSql.where(filter, params)
                + " ORDER BY " + SearchSql.orderBy(sort);
        return jdbcTemplate.queryForList(sql, params, UUID.class);
    }

    @Override
    @Transactional(readOnly = true)
    public long count(SearchFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM search_index WHERE " + SearchSql.where(filter, params), params, Long.class);
        return count == null ? 0 : count;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, IndexedCard> findCards(Collection<UUID> placeIds, SearchFilter filter) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource("placeIds", List.copyOf(placeIds));
        String distance = "NULL";
        if (filter.hasLocation()) {
            params.addValue("lat", filter.lat()).addValue("lon", filter.lon());
            distance = "ST_Distance(geom, " + SearchSql.POINT + ")";
        }
        // 표시 주소는 도로명, 없으면 지번 — place 상세와 같은 규칙
        String sql = "SELECT place_id, name, place_type, COALESCE(address_road, address_jibun) AS address, image_url, "
                + distance + " AS distance_m, rating_avg, review_count, data_base_date"
                + " FROM search_index WHERE place_id IN (:placeIds) AND status = 'ACTIVE'";

        Map<UUID, IndexedCard> cards = new HashMap<>();
        jdbcTemplate.query(sql, params, rs -> {
            Object distanceM = rs.getObject("distance_m");
            IndexedCard card = new IndexedCard(
                    rs.getObject("place_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("place_type"),
                    rs.getString("address"),
                    rs.getString("image_url"),
                    distanceM == null ? null : Math.round(((Number) distanceM).doubleValue()),
                    rs.getObject("rating_avg", BigDecimal.class),
                    rs.getInt("review_count"),
                    rs.getObject("data_base_date", LocalDate.class));
            cards.put(card.placeId(), card);
        });
        return cards;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Suggestion> suggest(String query, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("prefix", SearchSql.escapeLike(query) + "%")
                .addValue("like", "%" + SearchSql.escapeLike(query) + "%")
                .addValue("limit", limit);

        // 이름이나 별칭이 검색어로 시작하는 곳
        String startsWith = "(name ILIKE :prefix ESCAPE '\\'"
                + " OR EXISTS (SELECT 1 FROM unnest(name_alias) AS alias WHERE alias ILIKE :prefix ESCAPE '\\'))";
        // 이름이나 별칭에 검색어가 들어 있는 곳
        String contains = "(name ILIKE :like ESCAPE '\\'"
                + " OR EXISTS (SELECT 1 FROM unnest(name_alias) AS alias WHERE alias ILIKE :like ESCAPE '\\'))";
        String select = "SELECT place_id, name, place_type, sigungu_name FROM search_index WHERE status = 'ACTIVE' AND ";
        String order = " ORDER BY " + SearchSql.NAME_ORDER + ", place_id LIMIT :limit";

        List<Suggestion> suggestions = new ArrayList<>(
                jdbcTemplate.query(select + startsWith + order, params, SearchIndexRepositoryImpl::suggestion));
        if (suggestions.size() < limit) {
            // 모자라면 앞부분이 아닌 부분 일치로 채움 — 앞의 무리와 겹치지 않게 뺌
            params.addValue("limit", limit - suggestions.size());
            suggestions.addAll(jdbcTemplate.query(select + contains + " AND NOT " + startsWith + order,
                    params, SearchIndexRepositoryImpl::suggestion));
        }
        return suggestions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findSidoCode(UUID placeId) {
        List<String> found = jdbcTemplate.queryForList(
                "SELECT COALESCE(sido_code, '') FROM search_index WHERE place_id = :placeId",
                new MapSqlParameterSource("placeId", placeId), String.class);
        return found.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegionCount> countByRegion() {
        String sql = "SELECT sido_code, sigungu_name, count(*) AS place_count FROM search_index"
                + " WHERE status = 'ACTIVE' AND sido_code IS NOT NULL"
                + " GROUP BY sido_code, sigungu_name"
                + " ORDER BY sido_code, sigungu_name COLLATE \"ko-x-icu\"";
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> new RegionCount(
                rs.getString("sido_code"), rs.getString("sigungu_name"), rs.getLong("place_count")));
    }

    private static Suggestion suggestion(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Suggestion(rs.getObject("place_id", UUID.class), rs.getString("name"),
                rs.getString("place_type"), rs.getString("sigungu_name"));
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
