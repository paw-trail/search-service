-- search 의 첫 마이그레이션입니다. 검색 색인 한 표를 만듭니다.
--
-- search_index 는 place 의 장소를 검색하기 좋게 옮겨 담은 사본입니다.
-- 원본은 place 에 있고, 여기 값은 place.updated 를 받거나 매일 전량 재색인할 때 다시 읽어 덮어씁니다.
-- 그래서 이 표가 비어도 재색인 한 번이면 되살아납니다.
--
-- 반려동물 동반 조건은 한 칸도 담지 않습니다.
-- 담으면 SQL 로 판정하고 싶어지고, 그러면 판정 규칙이 verdict 와 여기 두 곳에 생깁니다.
-- 판정은 검색할 때 verdict 에 묻습니다.
--
-- 확장 둘을 이 파일이 만듭니다.
-- compose 의 search_db 에는 infra 가 이미 만들어 두어 여기서는 아무 일도 하지 않습니다.
-- 테스트 컨테이너(postgis/postgis)에는 pg_trgm 이 없어 여기서 만들어야 이름 트라이그램 인덱스가 섭니다.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE search_index (
    place_id          uuid                    NOT NULL,
    name              varchar(200)            NOT NULL,
    name_alias        text[]                  NOT NULL DEFAULT '{}',
    place_type        varchar(12)             NOT NULL,
    address_road      varchar(300),
    address_jibun     varchar(300),
    sido_code         varchar(2),
    sigungu_name      varchar(20),
    geom              geography(Point, 4326)  NOT NULL,
    facility_codes    text[]                  NOT NULL DEFAULT '{}',
    status            varchar(10)             NOT NULL,
    image_url         text,
    search_text       tsvector                NOT NULL,
    data_base_date    date,
    rating_avg        numeric(2, 1),
    review_count      integer                 NOT NULL DEFAULT 0,
    place_updated_at  timestamp               NOT NULL,
    indexed_at        timestamp               NOT NULL,
    created_at        timestamp               NOT NULL,
    created_by        varchar(45)             NOT NULL,
    updated_at        timestamp               NOT NULL,
    updated_by        varchar(45)             NOT NULL,
    deleted_at        timestamp,
    deleted_by        varchar(45),
    CONSTRAINT pk_search_index PRIMARY KEY (place_id)
);

COMMENT ON TABLE search_index IS
    '검색 색인. place 장소의 사본이며 place.updated 와 매일 재색인으로 다시 읽어 덮어씀. 판정 조건은 담지 않음';
COMMENT ON COLUMN search_index.name_alias IS '괄호 별칭. 이름과 함께 부분 일치로 찾음';
COMMENT ON COLUMN search_index.sigungu_name IS '시군구 이름. place 가 주소에서 뽑은 값 그대로. 세종은 NULL';
COMMENT ON COLUMN search_index.facility_codes IS 'place 편의시설 코드. 없으면 빈 배열이라 배열 포함 조건이 NULL 로 빠지지 않음';
COMMENT ON COLUMN search_index.search_text IS '낱말 앞부분 검색용. 이름 · 별칭 · 도로명 · 지번 · 소개문을 simple 사전으로';
COMMENT ON COLUMN search_index.rating_avg IS 'review 평점 평균. 매일 재색인 때만 채우고 이벤트로는 건드리지 않음';
COMMENT ON COLUMN search_index.place_updated_at IS 'place 가 이 행을 마지막으로 고친 시각. 더 새 값만 덮어써 재색인과 이벤트가 겹쳐도 옛 값이 이기지 않음';
COMMENT ON COLUMN search_index.indexed_at IS 'search 가 이 행을 마지막으로 쓴 시각';

-- 낱말 앞부분 검색 (소개문 등)
CREATE INDEX idx_search_index_search_text ON search_index USING gin (search_text);

-- 이름 부분 일치 · 자동완성
CREATE INDEX idx_search_index_name_trgm ON search_index USING gin (name gin_trgm_ops);

-- 거리
CREATE INDEX idx_search_index_geom ON search_index USING gist (geom);

-- 지역. 늘 시도 다음에 시군구로 좁혀 둘을 한 인덱스에 둠
CREATE INDEX idx_search_index_region ON search_index (sido_code, sigungu_name);

-- 종류
CREATE INDEX idx_search_index_place_type ON search_index (place_type);

-- 편의시설
CREATE INDEX idx_search_index_facility_codes ON search_index USING gin (facility_codes);
