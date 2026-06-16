package com.example.yanpaMarket_backend.auth.domain; // auth.domain = 인증 도메인 엔티티/enum 패키지

/**
 * [무엇] 소셜 로그인 "제공자" 종류를 나타내는 enum.
 * [연결]
 *   - UserSocialAccount.provider 컬럼에 문자열로 저장된다.
 *   - 현재는 카카오만 지원. 추후 네이버/구글 등을 추가하면 여기에 상수만 늘리면 된다.
 */
public enum AuthProvider {
    KAKAO // 카카오 OAuth (현재 유일한 로그인 방식)
}
