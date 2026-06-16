package com.example.yanpaMarket_backend.auth.dto; // auth.dto = 인증 API 요청/응답 객체 패키지

/**
 * [무엇] 카카오 콜백 처리 결과 응답 DTO.
 *        결과가 두 갈래라 한 객체로 표현한다:
 *          (A) 기존 회원 → 바로 로그인 완료(토큰 발급)
 *          (B) 신규 사용자 → 추가정보 입력 필요(signupToken 발급)
 * [어떻게 쓰임]
 *   - POST /api/v1/auth/kakao/callback 응답.
 *   - requiresProfileSetup 값으로 프론트가 분기(로그인 완료 vs 회원가입 화면 이동).
 * [연결]
 *   - AuthService 가 상황에 맞게 두 팩토리 메서드 중 하나로 만든다.
 */
public record KakaoCallbackResponse(
        boolean requiresProfileSetup, // true면 회원가입 추가입력 필요, false면 로그인 완료
        String signupToken,           // (신규) 회원가입 단계용 임시 토큰
        String userId,                // 사용자 식별자
        String email,                 // (신규) 카카오 이메일(화면 표시용)
        String nickname,              // (신규) 카카오 닉네임(화면 표시용)
        String accessToken,           // (기존) 로그인 액세스 토큰
        String refreshToken,          // (기존) 로그인 리프레시 토큰
        Long expiresIn                // (기존) 액세스 토큰 만료까지 남은 초
) {
    /**
     * [B: 신규] 추가정보 입력이 필요한 경우의 응답을 만든다.
     * 토큰 대신 signupToken 과 화면 표시용 이메일/닉네임을 담는다.
     */
    public static KakaoCallbackResponse profileSetupRequired(String signupToken, String userId, String email, String nickname) {
        return new KakaoCallbackResponse(true, signupToken, userId, email, nickname, null, null, null);
    }

    /**
     * [A: 기존] 로그인 완료 응답을 만든다.
     * TokenResponse 의 토큰들을 그대로 옮겨 담는다.
     */
    public static KakaoCallbackResponse authenticated(TokenResponse tokenResponse) {
        return new KakaoCallbackResponse(
                false,                          // 추가입력 불필요
                null,                           // signupToken 없음
                tokenResponse.userId(),
                null,                           // 이메일/닉네임은 로그인 응답에 불필요
                null,
                tokenResponse.accessToken(),
                tokenResponse.refreshToken(),
                tokenResponse.expiresIn()
        );
    }
}
