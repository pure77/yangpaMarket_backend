package com.example.yanpaMarket_backend; // 테스트도 본문과 같은 패키지 구조를 따른다

import org.junit.jupiter.api.Test;                       // 테스트 메서드 표시
import org.springframework.boot.test.context.SpringBootTest; // 스프링 컨테이너 전체를 띄우는 통합 테스트
import org.springframework.test.context.ActiveProfiles;  // 테스트 시 사용할 프로필 지정

/**
 * [무엇] 가장 기본적인 "스모크 테스트".
 *        스프링 컨텍스트(빈 구성 전체)가 오류 없이 로딩되는지만 확인한다.
 * [어떻게 쓰임]
 *   - 빈 설정/의존성 누락 같은 치명적 구성 오류를 조기에 잡는다.
 * [연결]
 *   - @ActiveProfiles("test"): test 프로필 설정으로 실행.
 */
@SpringBootTest
@ActiveProfiles("test")
class YanpaMarketBackendApplicationTests {

	/** 컨텍스트가 정상적으로 로딩되면 통과(본문 비어 있어도 로딩 실패 시 테스트 실패). */
	@Test
	void contextLoads() {
	}

}
