package com.pawtrail.search.infrastructure.persistence;

import com.pawtrail.search.domain.enums.SearchSort;
import com.pawtrail.search.domain.model.SearchFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * 검색 조건을 SQL 조각과 매개변수로 바꿉니다. SearchIndexRepositoryImpl 만 씁니다.
 *
 * 검색어 낱말마다 다섯 자리 가운데 하나에 걸리면 됩니다(search ㉩).
 *   이름 · 별칭 · 도로명 · 지번   부분 일치 (ILIKE)      붙여 쓴 이름(송파나루공원)도 「나루」로 찾음
 *   검색 글(소개문 등)           낱말 앞부분 (tsvector)   「수영장이」처럼 조사가 붙은 낱말을 「수영장」으로 찾음
 * 낱말끼리는 모두 맞아야 합니다(AND).
 *
 * 사용자 입력은 값으로만 넘깁니다. SQL 문장에 이어 붙이지 않습니다.
 * 다만 LIKE 의 % · _ 와 tsquery 의 연산자는 값 안에서도 뜻을 가지므로 따로 걷어 냅니다.
 */
final class SearchSql {

    // 위치가 있을 때 거리를 재는 기준점 — ST_MakePoint 는 (경도, 위도) 순서임
    static final String POINT = "CAST(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS geography)";

    // 이름을 가나다로 세우는 규칙 (search ㉫)
    // DB 기본 정렬(en_US.utf8)은 한글 음절을 1단계 비교에서 무시해 글자 수가 먼저 오는 차례가 됨
    // ICU 한국어 규칙은 한글을 가나다로, 한글을 영문보다 앞에 세움
    static final String NAME_ORDER = "name COLLATE \"ko-x-icu\"";

    // tsquery 에 넣을 낱말에서 글자 · 숫자가 아닌 것을 걷어 냄 — & | ! ( ) : * 가 연산자라서
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private SearchSql() {
    }

    /**
     * WHERE 뒤에 붙일 조건입니다. 폐업한 장소는 늘 뺍니다.
     */
    static String where(SearchFilter filter, MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
        clauses.add("status = 'ACTIVE'");

        List<String> words = filter.words();
        for (int i = 0; i < words.size(); i++) {
            String like = "like" + i;
            params.addValue(like, "%" + escapeLike(words.get(i)) + "%");
            StringBuilder clause = new StringBuilder("(")
                    .append("name ILIKE :").append(like).append(" ESCAPE '\\'")
                    .append(" OR EXISTS (SELECT 1 FROM unnest(name_alias) AS alias WHERE alias ILIKE :")
                    .append(like).append(" ESCAPE '\\')")
                    .append(" OR address_road ILIKE :").append(like).append(" ESCAPE '\\'")
                    .append(" OR address_jibun ILIKE :").append(like).append(" ESCAPE '\\'");

            String prefix = NOT_WORD.matcher(words.get(i)).replaceAll("").toLowerCase(Locale.ROOT);
            if (!prefix.isEmpty()) {
                String query = "prefix" + i;
                params.addValue(query, prefix + ":*");
                clause.append(" OR search_text @@ to_tsquery('simple', :").append(query).append(")");
            }
            clauses.add(clause.append(")").toString());
        }

        if (filter.sidoCode() != null) {
            clauses.add("sido_code = :sidoCode");
            params.addValue("sidoCode", filter.sidoCode());
        }
        if (filter.sigunguName() != null) {
            clauses.add("sigungu_name = :sigunguName");
            params.addValue("sigunguName", filter.sigunguName());
        }
        if (!filter.placeTypes().isEmpty()) {
            clauses.add("place_type IN (:placeTypes)");
            params.addValue("placeTypes", filter.placeTypes());
        }
        if (!filter.facilities().isEmpty()) {
            // 고른 편의시설을 모두 갖춘 곳 — 배열 포함
            clauses.add("facility_codes @> CAST(:facilities AS text[])");
            params.addValue("facilities", filter.facilities().toArray(String[]::new));
        }
        if (filter.hasLocation()) {
            params.addValue("lat", filter.lat());
            params.addValue("lon", filter.lon());
            if (filter.radiusM() != null) {
                clauses.add("ST_DWithin(geom, " + POINT + ", :radius)");
                params.addValue("radius", filter.radiusM());
            }
        }
        return String.join(" AND ", clauses);
    }

    /**
     * ORDER BY 뒤에 붙일 차례입니다. 끝에 place_id 를 두어 같은 값끼리도 차례가 늘 같게 합니다.
     * 차례가 흔들리면 쪽을 넘길 때 같은 장소가 두 번 나오거나 빠집니다.
     */
    static String orderBy(SearchSort sort) {
        return switch (sort) {
            case DISTANCE -> "ST_Distance(geom, " + POINT + "), " + NAME_ORDER + ", place_id";
            case RATING -> "rating_avg DESC NULLS LAST, review_count DESC, " + NAME_ORDER + ", place_id";
            case POPULAR, NAME -> NAME_ORDER + ", place_id";
        };
    }

    // LIKE 의 특수 문자를 글자 그대로로 — ESCAPE '\' 와 짝
    static String escapeLike(String word) {
        return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
