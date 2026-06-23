package com.example.yanpaMarket_backend.common.util;

/** 닉네임을 "첫 글자 + **" 로 마스킹한다. 입찰 내역/실시간 broadcast 공용. */
public final class NicknameMasker {

    private NicknameMasker() {
    }

    /** null/공백 → "익명", 그 외 → 첫 글자 + "**". */
    public static String mask(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "익명";
        }
        return nickname.charAt(0) + "**";
    }
}
