package com.example.yanpaMarket_backend.security; // security = 인증/토큰 관련 클래스 모음

/**
 * [무엇] 회원가입용 SIGNUP 토큰 안에 담는 카카오 사용자 정보 묶음.
 * [어떻게 쓰임]
 *   - 카카오 로그인했지만 아직 우리 회원이 아닌 사용자의 정보를 토큰에 잠깐 보관할 때 사용.
 *   - 회원가입 2단계(약관 동의 등)에서 이 정보로 실제 User 를 생성한다.
 * [연결]
 *   - JwtProvider.createKakaoSignupToken() 으로 토큰에 넣고,
 *     getKakaoSignupTokenPayload() 로 다시 꺼낸다.
 *   - AuthService 의 회원가입 흐름에서 사용.
 *
 * record = 불변 데이터 묶음. 일부 필드는 카카오가 안 줄 수 있어 null 가능.
 */
public record KakaoSignupTokenPayload(
        String providerUserId,  // 카카오가 부여한 사용자 고유 ID (필수)
        String email,           // 이메일 (동의 안 하면 null)
        String nickname,        // 닉네임 (없으면 null)
        String profileImageUrl  // 프로필 이미지 URL (없으면 null)
) {
}
