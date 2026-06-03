package com.example.yanpaMarket_backend.common.util;

import com.github.f4b6a3.ulid.UlidCreator;

/**
 * 신규 테이블(images/auctions/bids)의 public_id(CHAR(26) = ULID)를 생성하는 유틸리티.
 * ULID는 26자 Crockford Base32(시간 정렬 가능)로, users(UUID/VARCHAR64)와 달리 CHAR(26)에 저장된다.
 */
public final class PublicIdGenerator {

    private PublicIdGenerator() {
    }

    /** 26자 ULID 문자열을 생성합니다. */
    public static String newUlid() {
        return UlidCreator.getUlid().toString();
    }
}
