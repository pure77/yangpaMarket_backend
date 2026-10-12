package com.example.yanpaMarket_backend.config.properties; // config.properties = 외부 설정값(application.properties)을 담는 클래스 모음

import java.util.List;
import lombok.Getter; // getter 자동 생성
import lombok.Setter; // setter 자동 생성 (스프링이 설정값을 주입할 때 사용)
import org.springframework.boot.context.properties.ConfigurationProperties; // prefix로 묶인 설정값을 필드에 바인딩

/**
 * [무엇] CORS 허용 출처(Origin) 설정을 담는 객체.
 *        application.properties 의 "app.cors.*" 값들이 자동으로 채워진다.
 *
 * [왜 한곳에 모았나]
 *   예전에는 두 곳이 같은 값을 각자 하드코딩하고 있었다.
 *     WebSocketConfig  : setAllowedOriginPatterns("http://localhost:5173")  ← 핸드셰이크 Origin 검사
 *     SecurityConfig   : setAllowedOrigins(List.of("http://localhost:5173")) ← 일반 REST CORS
 *   배포할 때 한쪽만 고치면 나머지가 조용히 막힌다(서비스는 뜨는데 프론트만 안 붙는 장애).
 *   이제 두 곳 모두 이 클래스 하나를 주입받아 같은 값을 공유한다.
 *
 * [어떻게 쓰임]
 *   - WebSocketConfig: WebSocket 핸드셰이크의 Origin 화이트리스트(CSWSH 방어)
 *   - SecurityConfig : corsConfigurationSource 의 허용 출처
 *
 * [환경별 주입]
 *   - 로컬: 아래 필드 기본값 또는 application.properties 의 기본값(http://localhost:5173)
 *   - 운영: application-prod.yml 이 CORS_ALLOWED_ORIGINS 환경변수를 요구한다(미설정 시 기동 실패).
 *          CORS를 빠뜨린 채 배포되는 것보다 아예 뜨지 않는 편이 낫다는 판단.
 *
 * [기본값을 필드에 둔 이유]
 *   테스트는 src/test/resources/application.properties 가 메인 설정을 가려 app.cors.* 가 없다.
 *   자바 쪽 기본값이 있어야 테스트가 별도 설정 없이 그대로 돈다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * 허용할 프론트 출처 목록. 쉼표로 여러 개 지정할 수 있다.
     * 와일드카드 패턴도 가능하다(예: https://*.yangpa.com) — 두 곳 모두 "Patterns" API를 쓰기 때문.
     */
    private List<String> allowedOrigins = List.of("http://localhost:5173");
}
