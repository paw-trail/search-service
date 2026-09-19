package com.pawtrail.search.domain.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * 시도 17곳입니다. 지역 목록이 코드에 이름을 붙일 때 씁니다.
 *
 * 코드와 이름은 place 의 Sido 와 같습니다. 이름은 place 의 짧은 이름입니다(search ㉱).
 * 강원 · 전북은 특별자치도로 바뀐 뒤의 코드(51 · 52)입니다. place 가 색인에 그 코드를 넣습니다.
 *
 * 선언 차례가 지역 목록의 차례입니다. 행정 목록의 관례 차례라 코드 숫자 차례와 다릅니다.
 */
public enum Sido {

    SEOUL("11", "서울"),
    BUSAN("26", "부산"),
    DAEGU("27", "대구"),
    INCHEON("28", "인천"),
    GWANGJU("29", "광주"),
    DAEJEON("30", "대전"),
    ULSAN("31", "울산"),
    SEJONG("36", "세종"),
    GYEONGGI("41", "경기"),
    GANGWON("51", "강원"),
    CHUNGBUK("43", "충북"),
    CHUNGNAM("44", "충남"),
    JEONBUK("52", "전북"),
    JEONNAM("46", "전남"),
    GYEONGBUK("47", "경북"),
    GYEONGNAM("48", "경남"),
    JEJU("50", "제주");

    private final String code;
    private final String displayName;

    Sido(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<Sido> fromCode(String code) {
        return Arrays.stream(values()).filter(sido -> sido.code.equals(code)).findFirst();
    }
}
