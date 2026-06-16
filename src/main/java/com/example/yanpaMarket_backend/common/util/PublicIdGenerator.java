package com.example.yanpaMarket_backend.common.util; // common.util = 도메인 무관 공용 유틸리티 모음

import com.github.f4b6a3.ulid.UlidCreator; // 외부 라이브러리: ULID 문자열 생성기

/**
 * [무엇] 외부에 노출할 식별자 public_id(ULID 26자 문자열)를 만드는 유틸리티.
 *        ULID = 생성 시각이 앞부분에 들어가 "시간순 정렬이 가능한" 고유 ID.
 * [왜] DB의 자동증가 PK(숫자)를 그대로 노출하면 추측/스크래핑이 쉬우므로,
 *      대외 노출용 ID는 ULID(CHAR(26))로 따로 둔다.
 * [연결]
 *   - Auction, Image 등 엔티티 생성 시 public_id 값으로 사용된다.
 *   - users 테이블은 UUID(VARCHAR64)를 쓰지만, 신규 테이블은 CHAR(26) ULID로 통일.
 *
 * final class = 상속 금지(유틸 클래스라 확장 불필요).
 */
public final class PublicIdGenerator {

    /**
     * private 생성자 = 객체 생성을 막는다. 모든 메서드가 static 이라 인스턴스가 필요 없기 때문.
     */
    private PublicIdGenerator() {
    }

    /**
     * 26자 ULID 문자열을 새로 생성해 반환한다.
     * 예) "01HZX8K9..." (앞부분=시각, 뒷부분=랜덤)
     */
    public static String newUlid() {
        return UlidCreator.getUlid().toString(); // 라이브러리로 ULID 생성 후 문자열로 변환
    }
}
