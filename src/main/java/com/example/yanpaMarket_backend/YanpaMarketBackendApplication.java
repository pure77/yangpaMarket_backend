package com.example.yanpaMarket_backend; // 애플리케이션 루트 패키지 (이 아래 모든 컴포넌트가 자동 스캔됨)

import org.springframework.boot.SpringApplication;                       // 스프링 부트 부팅 실행기
import org.springframework.boot.autoconfigure.SpringBootApplication;     // 자동설정+컴포넌트 스캔을 한번에 켜는 핵심 어노테이션
import org.springframework.boot.context.properties.ConfigurationPropertiesScan; // @ConfigurationProperties 클래스 자동 등록
import org.springframework.scheduling.annotation.EnableScheduling;       // @Scheduled 메서드 활성화

/**
 * [무엇] Spring Boot 애플리케이션의 "시작점(진입점)".
 *        JVM이 main()을 호출하면 스프링 컨테이너 전체가 부팅된다.
 * [어떻게 쓰임]
 *   - ./gradlew bootRun 또는 IDE 실행 → 여기 main()이 호출됨.
 * [연결]
 *   - @SpringBootApplication: 이 패키지(com.example.yanpaMarket_backend) 하위의
 *     @Controller/@Service/@Repository/@Configuration 등을 모두 자동으로 찾아 등록한다.
 *   - @ConfigurationPropertiesScan: JwtProperties, KakaoProperties, S3Properties, UploadProperties 같은
 *     설정 바인딩 클래스(application.properties 값 매핑)를 자동 등록한다.
 *   - @EnableScheduling: AuctionCloseScheduler 등 @Scheduled 메서드가 동작하도록 스케줄링을 활성화한다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class YanpaMarketBackendApplication {

	/**
	 * JVM 진입점. 스프링 컨테이너를 생성하고 내장 웹서버(Tomcat)를 띄운다.
	 * @param args 실행 시 전달되는 커맨드라인 인자
	 */
	public static void main(String[] args) {
		SpringApplication.run(YanpaMarketBackendApplication.class, args); // 이 클래스를 기준으로 앱 부팅
	}

}
