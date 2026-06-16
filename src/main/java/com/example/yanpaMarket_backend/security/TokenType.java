package com.example.yanpaMarket_backend.security; // security = 인증/토큰 관련 클래스 모음

/**
 * [무엇] JWT 토큰의 "용도"를 구분하는 enum.
 *        같은 시크릿으로 서명하더라도 이 타입으로 쓰임새를 강제 구분한다.
 * [연결]
 *   - JwtProvider 가 토큰 생성/검증 시 tokenType 클레임으로 이 값을 넣고 확인한다.
 *   - 잘못된 타입의 토큰을 쓰면 UNAUTHORIZED 예외가 난다(예: 리프레시 토큰을 API 인증에 사용).
 */
public enum TokenType {
    ACCESS,  // 보호 API 인증용 단기 토큰 (기본 1시간)
    REFRESH, // 세션 유지용 장기 토큰 (기본 14일), DB에 해시로 저장
    SIGNUP   // 회원가입 2단계용 임시 토큰 (기본 30분), 카카오 사용자 정보 포함
}
