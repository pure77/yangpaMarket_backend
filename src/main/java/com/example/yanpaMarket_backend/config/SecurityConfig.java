package com.example.yanpaMarket_backend.config;

import com.example.yanpaMarket_backend.security.JwtAuthenticationFilter;
import com.example.yanpaMarket_backend.security.RestAuthenticationEntryPoint;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 스프링 시큐리티 전역 설정.
 * 세션을 사용하지 않는 JWT 기반 API 인증 구조로 동작합니다.
 */
@Configuration
public class SecurityConfig {

    // 인증 없이 접근 가능한 엔드포인트 목록
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/kakao/login",
            "/api/v1/auth/kakao/callback",
            "/api/v1/auth/signup/complete",
            "/api/v1/auth/refresh"
    };

    /**
     * 보안 필터 체인 구성.
     * - CSRF/FormLogin/Basic 비활성화
     * - Stateless 세션 정책
     * - 공개 API 외에는 인증 필요
     * - JwtAuthenticationFilter를 UsernamePasswordAuthenticationFilter 앞단에 배치
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(restAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 로컬 프론트엔드 개발 서버(5173)와 통신하기 위한 CORS 허용 정책입니다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
