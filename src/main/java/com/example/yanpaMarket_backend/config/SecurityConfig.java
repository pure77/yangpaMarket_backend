package com.example.yanpaMarket_backend.config; // config = 앱 전역 설정 모음

import com.example.yanpaMarket_backend.security.JwtAuthenticationFilter;   // 요청마다 JWT를 검사하는 커스텀 필터
import com.example.yanpaMarket_backend.security.RestAuthenticationEntryPoint; // 인증 실패 시 401 JSON 응답 처리기
import java.util.List;
import org.springframework.context.annotation.Bean;          // 빈 등록
import org.springframework.context.annotation.Configuration; // 설정 클래스
import org.springframework.http.HttpMethod;                  // GET/POST 등 메서드 구분
import org.springframework.security.config.Customizer;       // 기본 설정 적용 헬퍼
import org.springframework.security.config.annotation.web.builders.HttpSecurity;       // 보안 규칙 빌더
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // 기능 on/off 헬퍼
import org.springframework.security.config.http.SessionCreationPolicy; // 세션 정책(STATELESS 등)
import org.springframework.security.web.SecurityFilterChain; // 완성된 보안 필터 체인 타입
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // 기준이 되는 기본 인증 필터
import org.springframework.web.cors.CorsConfiguration;          // CORS 규칙
import org.springframework.web.cors.CorsConfigurationSource;    // CORS 규칙 공급자 인터페이스
import org.springframework.web.cors.UrlBasedCorsConfigurationSource; // URL별 CORS 규칙 등록기

/**
 * [무엇] 스프링 시큐리티 전역 설정.
 *        세션을 쓰지 않는 JWT 기반 API 인증 구조를 정의한다.
 * [어떻게 쓰임]
 *   - 모든 HTTP 요청이 이 필터 체인을 통과한다. 공개 경로는 통과, 그 외엔 인증 필요.
 * [연결]
 *   - JwtAuthenticationFilter: 요청 헤더의 Bearer 토큰을 검증해 인증 정보를 채운다.
 *   - RestAuthenticationEntryPoint: 인증 실패 시 401 JSON(ApiResponse 포맷)을 응답.
 *   - CORS 정책으로 프론트(localhost:5173)와의 통신을 허용.
 */
@Configuration
public class SecurityConfig {

    // [공개 엔드포인트] 토큰 없이도 접근 가능한 경로들(주로 로그인/토큰 갱신)
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/kakao/login",      // 카카오 로그인 URL 발급
            "/api/v1/auth/kakao/callback",   // 카카오 콜백(인가코드 처리)
            "/api/v1/auth/signup/complete",  // 회원가입 2단계 완료
            "/api/v1/auth/refresh"           // 토큰 갱신
    };

    /**
     * [핵심] 보안 필터 체인 구성.
     * - CSRF/폼로그인/기본인증 비활성화 (REST API라 불필요)
     * - 세션 미사용(STATELESS): 인증 상태를 서버 세션이 아닌 토큰으로 유지
     * - 공개 경로 외에는 인증 요구
     * - JwtAuthenticationFilter 를 기본 인증 필터 앞에 끼워 넣음
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,                                   // 보안 규칙 빌더(스프링 주입)
            JwtAuthenticationFilter jwtAuthenticationFilter,     // 우리가 만든 JWT 검사 필터(빈 주입)
            RestAuthenticationEntryPoint restAuthenticationEntryPoint // 인증 실패 응답기(빈 주입)
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())                 // 아래 corsConfigurationSource() 빈을 CORS 규칙으로 사용
                .csrf(AbstractHttpConfigurer::disable)           // CSRF 토큰 검증 끔(세션 미사용 API)
                .formLogin(AbstractHttpConfigurer::disable)      // 기본 로그인 폼 끔
                .httpBasic(AbstractHttpConfigurer::disable)      // 기본 인증 팝업 끔
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 만들지 않음
                .exceptionHandling(exception -> exception.authenticationEntryPoint(restAuthenticationEntryPoint)) // 인증 실패 → 401 JSON
                .authorizeHttpRequests(auth -> auth              // 경로별 접근 권한 규칙 (위에서부터 순서대로 평가)
                        // "내 경매"는 인증 필요 — 아래 GET 공개 매처보다 반드시 먼저 둬야 가려지지 않음
                        .requestMatchers(HttpMethod.GET, "/api/v1/auctions/mine").authenticated()
                        // 경매 목록/상세 조회는 비로그인 공개
                        .requestMatchers(HttpMethod.GET, "/api/v1/auctions", "/api/v1/auctions/*").permitAll()
                        // 업로드된 이미지 정적 파일 공개
                        .requestMatchers("/uploads/**").permitAll()
                        // 로그인/토큰 관련 공개 경로
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class); // JWT 필터를 앞단에 배치
        return http.build(); // 위 설정으로 필터 체인 완성
    }

    /**
     * [CORS] 로컬 프론트 개발 서버(5173)에서 오는 요청을 허용하는 정책.
     * 브라우저의 교차 출처(origin) 차단을 풀어주는 설정.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173")); // 허용할 프론트 주소
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")); // 허용 메서드
        configuration.setAllowedHeaders(List.of("*"));   // 모든 요청 헤더 허용(Authorization 포함)
        configuration.setAllowCredentials(true);          // 쿠키/인증정보 동반 요청 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // 모든 경로에 위 규칙 적용
        return source;
    }
}
