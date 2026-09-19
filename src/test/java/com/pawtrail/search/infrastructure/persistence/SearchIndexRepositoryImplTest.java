package com.pawtrail.search.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.pawtrail.search.domain.model.IndexedPlace;
import com.pawtrail.search.domain.model.ReviewStats;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 색인에 넣고 덮어쓰는 SQL 을 실제 PostgreSQL + PostGIS 에서 검사합니다.
 *
 * 흉내 내지 않고 컨테이너를 띄우는 이유는 검사하려는 것이 전부 데이터베이스 안에서 일어나기 때문입니다.
 * 조건부 덮어쓰기(ON CONFLICT … WHERE) · 배열 칸 · 좌표 · 낱말 검색 글이 그것이고,
 * 흉내 내면 SQL 이 틀려도 통과합니다.
 *
 * 이미지와 asCompatibleSubstituteFor 의 이유는 SearchApplicationTests 에 적었습니다.
 */
@SpringBootTest
@Testcontainers
class SearchIndexRepositoryImplTest {

    private static final DockerImageName POSTGIS_IMAGE =
        DockerImageName.parse("postgis/postgis:17-3.5")
            .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGIS_IMAGE);

    private static final UUID PLACE_A = UUID.fromString("01a09015-85fa-7f60-b85c-030b6d0f3f54");
    private static final UUID PLACE_B = UUID.fromString("01a09015-8618-76fc-b5ac-ed94e035d675");
    private static final UUID PLACE_C = UUID.fromString("01a09015-8623-77d5-ae0a-a0608fbf1d4a");

    // place 는 마이크로초까지 담아 보냄 — 비교가 그 자리까지 맞는지 함께 봄
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 11, 19, 48, 47, 915_252_000);
    private static final LocalDateTime T2 = T1.plusDays(1);
    private static final LocalDateTime T3 = T2.plusDays(1);

    @Autowired
    private SearchIndexRepository searchIndexRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 비우기() {
        jdbcTemplate.update("DELETE FROM search_index");
    }

    @Test
    @DisplayName("처음 보는 장소는 넣고 좌표 · 배열 · 감사 칸을 채운다")
    void 새_장소() {
        int written = searchIndexRepository.saveAllIfNewer(List.of(yeouido("여의도한강공원", T1)));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT array_to_string(name_alias, ',') AS alias,
                       array_to_string(facility_codes, ',') AS facilities,
                       ST_Y(geom::geometry) AS lat, ST_X(geom::geometry) AS lon,
                       created_by, rating_avg, review_count, sigungu_name, data_base_date
                FROM search_index WHERE place_id = ?
                """, PLACE_A);
        assertThat(written).isEqualTo(1);
        // null 원소는 빠지고 순서는 그대로
        assertThat(row.get("alias")).isEqualTo("여의도공원");
        assertThat(row.get("facilities")).isEqualTo("PARKING,WALKING_TRAIL");
        // ST_MakePoint 는 (경도, 위도) 순서라 뒤집히면 여기서 드러남
        assertThat((Double) row.get("lat")).isCloseTo(37.5283, within(1e-7));
        assertThat((Double) row.get("lon")).isCloseTo(126.9326, within(1e-7));
        assertThat(row.get("created_by")).isEqualTo("SYSTEM");
        assertThat(row.get("rating_avg")).isNull();
        assertThat(row.get("review_count")).isEqualTo(0);
        assertThat(row.get("sigungu_name")).isEqualTo("영등포구");
        assertThat(row.get("data_base_date").toString()).isEqualTo("2025-03-24");
    }

    @Test
    @DisplayName("소개문과 지번의 낱말도 앞부분으로 걸린다")
    void 낱말_앞부분() {
        searchIndexRepository.saveAllIfNewer(List.of(yeouido("여의도한강공원", T1)));

        // "한강을" 처럼 조사가 붙은 낱말이 "한강" 으로 걸려야 함
        assertThat(matches("한강:*")).isTrue();
        assertThat(matches("여의도동:*")).isTrue();
        assertThat(matches("부산:*")).isFalse();
    }

    @Test
    @DisplayName("place 수정 시각이 더 새로울 때만 덮어쓴다")
    void 더_새_것만() {
        searchIndexRepository.saveAllIfNewer(List.of(yeouido("여의도한강공원", T1)));

        assertThat(searchIndexRepository.saveAllIfNewer(List.of(yeouido("새 이름", T2)))).isEqualTo(1);
        assertThat(nameOf(PLACE_A)).isEqualTo("새 이름");

        // 재색인이 늦게 옛 값을 쓰려 해도 새 값이 지켜짐
        assertThat(searchIndexRepository.saveAllIfNewer(List.of(yeouido("옛 이름", T1)))).isZero();
        assertThat(nameOf(PLACE_A)).isEqualTo("새 이름");

        // 같은 이벤트를 두 번 받은 경우
        assertThat(searchIndexRepository.saveAllIfNewer(List.of(yeouido("같은 시각", T2)))).isZero();
        assertThat(nameOf(PLACE_A)).isEqualTo("새 이름");
    }

    @Test
    @DisplayName("덮어써도 평점과 후기 수는 그대로다")
    void 평점은_그대로() {
        searchIndexRepository.saveAllIfNewer(List.of(yeouido("여의도한강공원", T1)));
        jdbcTemplate.update("UPDATE search_index SET rating_avg = 4.5, review_count = 3 WHERE place_id = ?", PLACE_A);

        searchIndexRepository.saveAllIfNewer(List.of(yeouido("여의도한강공원", T3)));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT rating_avg, review_count FROM search_index WHERE place_id = ?", PLACE_A);
        assertThat((BigDecimal) row.get("rating_avg")).isEqualByComparingTo("4.5");
        assertThat(row.get("review_count")).isEqualTo(3);
    }

    @Test
    @DisplayName("한 묶음에서 새 장소는 넣고 옛 값은 건너뛰며, 빈 칸과 폐업도 담는다")
    void 묶음() {
        searchIndexRepository.saveAllIfNewer(List.of(yeouido("여의도한강공원", T2)));
        IndexedPlace sejong = new IndexedPlace(PLACE_C, "세종 어딘가", null, "ETC",
                null, "세종특별자치시 조치원읍 1", "36", null,
                new BigDecimal("36.6000000"), new BigDecimal("127.3000000"),
                null, "CLOSED", null, null, null, T1);

        int written = searchIndexRepository.saveAllIfNewer(List.of(
                other(PLACE_B), yeouido("옛 이름", T1), sejong));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT sigungu_name, address_road, facility_codes = '{}' AS empty_facilities, status
                FROM search_index WHERE place_id = ?
                """, PLACE_C);
        assertThat(written).isEqualTo(2);
        assertThat(row.get("sigungu_name")).isNull();
        assertThat(row.get("address_road")).isNull();
        assertThat(row.get("empty_facilities")).isEqualTo(true);
        assertThat(row.get("status")).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("평점을 갈아 끼우되 place 에서 온 칸은 두고, 같은 값이면 쓰지 않는다")
    void 평점_갈아_끼우기() {
        searchIndexRepository.saveAllIfNewer(List.of(yeouido("여의도한강공원", T1), other(PLACE_B)));

        // B 는 이미 평점 없음 · 0 이라 안 씀 · C 는 색인에 없음
        int changed = searchIndexRepository.updateReviewStats(List.of(
                new ReviewStats(PLACE_A, new BigDecimal("4.5"), 3),
                ReviewStats.none(PLACE_B),
                new ReviewStats(PLACE_C, new BigDecimal("3.0"), 1)));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT rating_avg, review_count, name, place_updated_at FROM search_index WHERE place_id = ?", PLACE_A);
        assertThat(changed).isEqualTo(1);
        assertThat((BigDecimal) row.get("rating_avg")).isEqualByComparingTo("4.5");
        assertThat(row.get("review_count")).isEqualTo(3);
        assertThat(row.get("name")).isEqualTo("여의도한강공원");
        // 평점을 바꿔도 place 수정 시각이 안 바뀌어야 다음 이벤트의 덮어쓰기 판단이 흔들리지 않음
        assertThat(row.get("place_updated_at").toString()).startsWith("2026-09-11 19:48:47.915252");

        assertThat(searchIndexRepository.updateReviewStats(List.of(
                new ReviewStats(PLACE_A, new BigDecimal("4.5"), 3)))).isZero();
        // 후기가 모두 지워지면 옛 평점이 남지 않게 되돌림
        assertThat(searchIndexRepository.updateReviewStats(List.of(ReviewStats.none(PLACE_A)))).isEqualTo(1);
    }

    @Test
    @DisplayName("한 바퀴에서 못 본 행을 세기만 하고 지우지 않는다")
    void 못_본_행은_세기만() {
        searchIndexRepository.saveAllIfNewer(List.of(yeouido("여의도한강공원", T1), other(PLACE_B)));

        assertThat(searchIndexRepository.countOutside(List.of(PLACE_A))).isEqualTo(1L);
        assertThat(searchIndexRepository.countOutside(List.of(PLACE_A, PLACE_B))).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_index", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("빈 목록이면 아무것도 하지 않는다")
    void 빈_목록() {
        assertThat(searchIndexRepository.saveAllIfNewer(List.of())).isZero();
    }

    private boolean matches(String query) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT search_text @@ to_tsquery('simple', ?) FROM search_index WHERE place_id = ?",
                Boolean.class, query, PLACE_A));
    }

    private String nameOf(UUID placeId) {
        return jdbcTemplate.queryForObject("SELECT name FROM search_index WHERE place_id = ?", String.class, placeId);
    }

    private static IndexedPlace yeouido(String name, LocalDateTime updatedAt) {
        return new IndexedPlace(PLACE_A, name, Arrays.asList("여의도공원", null), "PARK",
                "서울특별시 영등포구 여의동로 330", "서울특별시 영등포구 여의도동 8", "11", "영등포구",
                new BigDecimal("37.5283000"), new BigDecimal("126.9326000"),
                List.of("PARKING", "WALKING_TRAIL"), "ACTIVE", "https://example.com/a.jpg",
                "한강을 따라 걷는 공원입니다", LocalDate.of(2025, 3, 24), updatedAt);
    }

    private static IndexedPlace other(UUID placeId) {
        return new IndexedPlace(placeId, "북촌 8경", List.of(), "CULTURE", "서울특별시 종로구 계동길 37", null,
                "11", "종로구", new BigDecimal("37.5820000"), new BigDecimal("126.9860000"),
                List.of(), "ACTIVE", null, null, null, T1);
    }
}
