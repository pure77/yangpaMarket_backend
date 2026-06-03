package com.example.yanpaMarket_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot 애플리케이션 진입점.
 * @ConfigurationPropertiesScan으로 app.jwt, app.kakao 설정 바인딩 클래스를 자동 등록합니다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class YanpaMarketBackendApplication {

	/**
	 * JVM 시작 시 스프링 컨테이너를 부팅합니다.
	 */
	public static void main(String[] args) {
		SpringApplication.run(YanpaMarketBackendApplication.class, args);
	}

}
