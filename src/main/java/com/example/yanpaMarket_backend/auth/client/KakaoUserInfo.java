package com.example.yanpaMarket_backend.auth.client; // auth.client = 외부(카카오) API 호출 관련 패키지

/**
 * [무엇] 카카오 사용자 정보 API 응답을 우리 형태로 정리한 데이터 묶음.
 * [어떻게 쓰임]
 *   - KakaoOAuthClient 가 카카오에서 받은 JSON을 이 record로 변환해 반환한다.
 * [연결]
 *   - AuthService 가 이 정보로 기존 회원 여부를 판단하고, 신규면 회원가입 토큰에 담는다.
 *   - email/nickname/profileImageUrl 은 카카오 동의 항목에 따라 null 일 수 있다.
 */
public record KakaoUserInfo(
        String providerUserId,  // 카카오 고유 사용자 ID (필수)
        String email,           // 이메일 (동의 안 하면 null)
        String nickname,        // 닉네임 (없으면 null)
        String profileImageUrl  // 프로필 이미지 URL (없으면 null)
) {
}
