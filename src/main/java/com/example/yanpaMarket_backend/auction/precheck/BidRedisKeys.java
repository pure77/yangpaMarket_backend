package com.example.yanpaMarket_backend.auction.precheck;

/**
 * [무엇] 입찰 관련 Redis 키·필드 이름을 만드는 유일한 곳.
 * [왜 {id}인가] 중괄호 안이 Redis Cluster의 해시 태그다. 같은 경매의 키(…:bid, 2단계의 …:lock)가
 *   같은 슬롯에 놓여, Cluster로 옮겨도 두 키를 함께 다루는 스크립트가 그대로 동작한다.
 */
public final class BidRedisKeys {

    public static final String PRICE = "price";
    public static final String INC = "inc";
    public static final String SELLER = "seller";
    public static final String END_AT = "endAt";

    private BidRedisKeys() {
    }

    public static String bid(Long auctionId) {
        return "auction:{" + auctionId + "}:bid";
    }
}
