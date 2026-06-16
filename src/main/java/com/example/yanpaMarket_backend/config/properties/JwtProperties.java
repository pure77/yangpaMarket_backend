package com.example.yanpaMarket_backend.config.properties; // config.properties = 외부 설정값(application.properties)을 담는 클래스 모음

import lombok.Getter; // getter 자동 생성
import lombok.Setter; // setter 자동 생성 (스프링이 설정값을 주입할 때 사용)
import org.springframework.boot.context.properties.ConfigurationProperties; // prefix로 묶인 설정값을 필드에 바인딩

/**
 * [무엇] JWT(토큰) 관련 설정값을 담는 객체.
 *        application.properties 의 "app.jwt.*" 값들이 자동으로 채워진다.
 * [어떻게 쓰임]
 *   - JwtProvider 가 이 값으로 토큰 서명/만료시간을 설정한다.
 * [연결]
 *   - prefix="app.jwt" → app.jwt.secret, app.jwt.access-expiration-seconds 등과 매핑.
 *   - 등록은 메인 클래스의 @ConfigurationPropertiesScan 이 자동 처리.
 *   - secret 같은 민감값은 코드가 아니라 환경변수/외부 설정으로 주입(보안).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret;                  // 토큰 서명에 쓰는 비밀키 (외부 주입)
    private long accessExpirationSeconds;   // 액세스 토큰 유효시간(초)
    private long refreshExpirationSeconds;  // 리프레시 토큰 유효시간(초)
    private long signupExpirationSeconds;   // 회원가입 단계용 임시 토큰 유효시간(초)
}
