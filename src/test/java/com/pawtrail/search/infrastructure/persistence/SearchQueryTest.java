package com.pawtrail.search.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawtrail.search.domain.enums.SearchSort;
import com.pawtrail.search.domain.model.IndexedCard;
import com.pawtrail.search.domain.model.IndexedPlace;
import com.pawtrail.search.domain.model.RegionCount;
import com.pawtrail.search.domain.model.SearchFilter;
import com.pawtrail.search.domain.model.Suggestion;
import com.pawtrail.search.domain.repository.SearchIndexRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 검색 SQL 을 실제 PostgreSQL + PostGIS 에서 검사합니다.
 *
 * 조건 조립(SearchSql)이 틀리면 흉내로는 드러나지 않습니다.
 * 부분 일치 · 낱말 앞부분 · 배열 포함 · 반경 · 거리 차례는 전부 데이터베이스가 계산하기 때문입니다.
 *
 * 장소 다섯 곳을 둡니다. 여섯째는 폐업이라 어느 검색에도 나오면 안 됩니다.
 */
@SpringBootTest
@Testcontainers
class SearchQueryTest {

    private static final DockerImageName POSTGIS_IMAGE =
        DockerImageName.parse("postgis/postgis:17-3.5")
            .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGIS_IMAGE);

    private static final UUID YEOUIDO = place(1);
    private static final UUID SONGPA = place(2);
    private static final UUID CAFE = place(3);
    private static final UUID VET = place(4);
    private static final UUID HAEUNDAE = place(5);
    private static final UUID CLOSED = place(6);

    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 9, 11, 19, 48, 47);

    // 여의도한강공원 한가운데 — 반경 5km 에 여의도 · 당산 카페 · 마포 병원이 들어옴
    private static final SearchFilter NEAR_YEOUIDO =
            new SearchFilter(List.of(), null, null, 37.5285, 126.9327, 5_000, null, null);

    @Autowired
    private SearchIndexRepository searchIndexRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 장소_다섯과_폐업_하나() {
        jdbcTemplate.update("DELETE FROM search_index");
        searchIndexRepository.saveAllIfNewer(List.of(
                place(YEOUIDO, "여의도한강공원", List.of("여의도공원"), "PARK", "서울특별시 영등포구 여의동로 330", null,
                        "11", "영등포구", "37.5285", "126.9327", List.of("PARKING", "PLAYGROUND"), "ACTIVE",
                        "넓은 잔디밭과 수영장이 있는 한강 공원"),
                place(SONGPA, "송파나루공원", List.of(), "PARK", "서울특별시 송파구 잠실동 47", null,
                        "11", "송파구", "37.5096", "127.1038", List.of("PARKING"), "ACTIVE", "석촌호수 산책로"),
                place(CAFE, "멍멍카페 100%_진짜", List.of(), "CAFE", null, "서울특별시 영등포구 당산동 1-1",
                        "11", "영등포구", "37.5340", "126.9020", List.of(), "ACTIVE", "반려견 동반 카페"),
                place(VET, "한강동물병원", List.of(), "VET", "서울특별시 마포구 월드컵로 1", null,
                        "11", "마포구", "37.5550", "126.9100", List.of(), "ACTIVE", null),
                place(HAEUNDAE, "해운대해수욕장", List.of(), "BEACH", "부산광역시 해운대구 해운대해변로 264", null,
                        "26", "해운대구", "35.1587", "129.1604", List.of("PARKING"), "ACTIVE", "여름 해수욕장"),
                place(CLOSED, "문닫은공원", List.of(), "PARK", "서울특별시 영등포구 어딘가 1", null,
                        "11", "영등포구", "37.5290", "126.9330", List.of("PARKING"), "CLOSED", "폐업")));
        jdbcTemplate.update("UPDATE search_index SET rating_avg = 4.5, review_count = 10 WHERE place_id = ?", SONGPA);
        jdbcTemplate.update("UPDATE search_index SET rating_avg = 3.0, review_count = 2 WHERE place_id = ?", HAEUNDAE);
    }

    @Test
    @DisplayName("낱말은 이름 · 별칭 · 주소의 부분 일치나 소개문 낱말의 앞부분에 걸리면 된다")
    void 낱말이_걸리는_자리() {
        // 붙여 쓴 이름 속 「나루」 · 별칭 · 도로명이 없는 곳의 지번 · 소개문의 「수영장이」
        assertThat(ids("나루")).containsExactly(SONGPA);
        assertThat(ids("여의도공원")).containsExactly(YEOUIDO);
        assertThat(ids("당산동")).containsExactly(CAFE);
        assertThat(ids("수영장")).containsExactly(YEOUIDO);
        // 폐업한 「문닫은공원」 은 영등포구에 있어도 안 나옴
        assertThat(ids("한강")).containsExactlyInAnyOrder(YEOUIDO, VET);
    }

    @Test
    @DisplayName("낱말 둘은 모두 맞아야 한다")
    void 낱말은_모두_맞아야() {
        assertThat(ids("한강 영등포구")).containsExactly(YEOUIDO);
    }

    @Test
    @DisplayName("검색어의 % · _ 는 글자 그대로이고, 따옴표 · 연산자가 섞여도 오류 없이 값으로만 쓴다")
    void 특수_문자() {
        assertThat(ids("100%_")).containsExactly(CAFE);
        assertThat(ids("%")).containsExactly(CAFE);
        for (String word : List.of("a'b", "&", "!:*", "(", "한강:*|&", "\\", "'); DROP TABLE search_index;--")) {
            ids(word);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_index", Integer.class)).isEqualTo(6);
    }

    @Test
    @DisplayName("지역 · 종류 · 편의시설로 좁힌다 — 편의시설 여럿은 모두 갖춘 곳만")
    void 지역_종류_편의시설() {
        assertThat(searchIndexRepository.count(
                new SearchFilter(List.of(), "11", "영등포구", null, null, null, null, null))).isEqualTo(2L);
        assertThat(searchIndexRepository.findAllIds(
                new SearchFilter(List.of(), null, null, null, null, null, List.of("PARK", "BEACH"), null), SearchSort.NAME))
                .containsExactlyInAnyOrder(YEOUIDO, SONGPA, HAEUNDAE);
        assertThat(searchIndexRepository.findAllIds(
                new SearchFilter(List.of(), null, null, null, null, null, null, List.of("PARKING", "PLAYGROUND")), SearchSort.NAME))
                .containsExactly(YEOUIDO);
    }

    @Test
    @DisplayName("반경 안에서 가까운 차례로, 평점순은 평점 없는 곳을 뒤로 둔다")
    void 거리와_평점() {
        assertThat(searchIndexRepository.findAllIds(NEAR_YEOUIDO, SearchSort.DISTANCE)).containsExactly(YEOUIDO, CAFE, VET);
        assertThat(searchIndexRepository.findAllIds(noFilter(), SearchSort.RATING).subList(0, 2)).containsExactly(SONGPA, HAEUNDAE);
        // 쪽으로 잘라도 전체 차례와 같음 — 차례가 흔들리면 쪽을 넘길 때 같은 장소가 두 번 나옴
        assertThat(searchIndexRepository.findIds(noFilter(), SearchSort.NAME, 1, 2))
                .isEqualTo(searchIndexRepository.findAllIds(noFilter(), SearchSort.NAME).subList(1, 3));
    }

    @Test
    @DisplayName("카드는 도로명이 없으면 지번을 쓰고, 넣은 좌표를 그대로 싣고, 위치가 있을 때만 거리를 미터로 싣는다")
    void 카드() {
        Map<UUID, IndexedCard> cards = searchIndexRepository.findCards(List.of(YEOUIDO, CAFE), NEAR_YEOUIDO);

        assertThat(cards.get(CAFE).address()).isEqualTo("서울특별시 영등포구 당산동 1-1");
        // 색인에 (경도, 위도) 순서로 넣으므로 꺼낼 때 둘이 뒤바뀌면 여기서 걸림
        assertThat(cards.get(YEOUIDO).lat()).isEqualByComparingTo(new BigDecimal("37.5285"));
        assertThat(cards.get(YEOUIDO).lon()).isEqualByComparingTo(new BigDecimal("126.9327"));
        assertThat(cards.get(CAFE).lat()).isEqualByComparingTo(new BigDecimal("37.5340"));
        assertThat(cards.get(CAFE).lon()).isEqualByComparingTo(new BigDecimal("126.9020"));
        assertThat(cards.get(YEOUIDO).distanceM()).isZero();
        assertThat(cards.get(CAFE).distanceM()).isBetween(2_500L, 3_000L);

        // 좌표는 위치를 보냈는지와 상관없이 늘 실림 — 웹 화면은 위치를 안 보내고 이 좌표로 거리를 직접 잼
        IndexedCard withoutLocation = searchIndexRepository.findCards(List.of(YEOUIDO), noFilter()).get(YEOUIDO);
        assertThat(withoutLocation.distanceM()).isNull();
        assertThat(withoutLocation.lat()).isEqualByComparingTo(new BigDecimal("37.5285"));
        assertThat(withoutLocation.lon()).isEqualByComparingTo(new BigDecimal("126.9327"));
    }

    @Test
    @DisplayName("이름순은 가나다다 — 컨테이너의 기본 정렬(en_US)이 한글을 글자 수로 세워도 쿼리가 규칙을 정한다")
    void 이름순은_가나다() {
        assertThat(searchIndexRepository.findAllIds(noFilter(), SearchSort.NAME))
                .containsExactly(CAFE, SONGPA, YEOUIDO, VET, HAEUNDAE);
    }

    @Test
    @DisplayName("자동완성은 이름 · 별칭이 검색어로 시작하는 곳을 먼저, 모자라면 들어 있는 곳으로 채우고 폐업은 뺀다")
    void 자동완성() {
        assertThat(searchIndexRepository.suggest("한강", 10))
                .extracting(Suggestion::placeId)
                .containsExactly(VET, YEOUIDO);
        assertThat(searchIndexRepository.suggest("여의도공", 10))
                .extracting(Suggestion::placeId)
                .containsExactly(YEOUIDO);
        assertThat(searchIndexRepository.suggest("문닫", 10)).isEmpty();
        assertThat(searchIndexRepository.suggest("공원", 1)).hasSize(1);
    }

    @Test
    @DisplayName("지역 수는 폐업을 빼고 시군구 가나다로, 시도 코드는 색인에 있을 때만 준다")
    void 지역_수와_시도_코드() {
        assertThat(searchIndexRepository.countByRegion()).containsExactly(
                new RegionCount("11", "마포구", 1),
                new RegionCount("11", "송파구", 1),
                new RegionCount("11", "영등포구", 2),
                new RegionCount("26", "해운대구", 1));
        assertThat(searchIndexRepository.findSidoCode(YEOUIDO)).contains("11");
        assertThat(searchIndexRepository.findSidoCode(place(99))).isEmpty();
        // 인기 급상승은 조회수에서 온 식별자로 카드를 읽음 — 폐업한 곳은 빠져야 함
        assertThat(searchIndexRepository.findCards(List.of(CLOSED, YEOUIDO), noFilter())).containsOnlyKeys(YEOUIDO);
    }

    private List<UUID> ids(String q) {
        return searchIndexRepository.findAllIds(
                new SearchFilter(List.of(q.trim().split("\\s+")), null, null, null, null, null, null, null),
                SearchSort.NAME);
    }

    private static SearchFilter noFilter() {
        return new SearchFilter(List.of(), null, null, null, null, null, null, null);
    }

    private static UUID place(int n) {
        return new UUID(0x01a0901500007000L, 0x8000000000000000L | n);
    }

    private static IndexedPlace place(UUID placeId, String name, List<String> alias, String type, String road,
                                      String jibun, String sidoCode, String sigunguName, String lat, String lon,
                                      List<String> facilities, String status, String overview) {
        return new IndexedPlace(placeId, name, alias, type, road, jibun, sidoCode, sigunguName,
                new BigDecimal(lat), new BigDecimal(lon), facilities, status, null, overview,
                LocalDate.of(2026, 9, 1), UPDATED_AT);
    }
}
