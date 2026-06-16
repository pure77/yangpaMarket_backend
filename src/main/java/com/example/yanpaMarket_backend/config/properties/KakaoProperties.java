package com.example.yanpaMarket_backend.config.properties; // config.properties = 외부 설정값을 담는 클래스 모음

import lombok.Getter; // getter 자동 생성
import lombok.Setter; // setter 자동 생성 (스프링이 설정값 주입 시 사용)
import org.springframework.boot.context.properties.ConfigurationProperties; // prefix로 묶인 설정값 바인딩

/**
 * [무엇] 카카오 OAuth 로그인에 필요한 설정값 모음.
 *        application.properties 의 "app.kakao.*" 값들이 자동 주입된다.
 * [어떻게 쓰임]
 *   - KakaoOAuthClient / OAuthStateService 가 이 값으로 카카오 인증·토큰·사용자정보 요청을 보낸다.
 * [연결]
 *   - prefix="app.kakao" → app.kakao.client-id, app.kakao.redirect-uri 등과 매핑.
 *   - clientSecret 등 민감값은 환경변수로 주입(Git 커밋 금지).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.kakao")
public class KakaoProperties {
    private String clientId;      // 카카오 앱 REST API 키 (앱 식별)
    private String clientSecret;  // 카카오 앱 시크릿 (토큰 교환 시 검증)
    private String redirectUri;   // 인가코드를 돌려받을 우리 콜백 주소
    private String authUri;       // 카카오 로그인(인가) 페이지 URL
    private String tokenUri;      // 인가코드 → 액세스토큰 교환 API URL
    private String userInfoUri;   // 액세스토큰으로 사용자 정보 조회 API URL
}
