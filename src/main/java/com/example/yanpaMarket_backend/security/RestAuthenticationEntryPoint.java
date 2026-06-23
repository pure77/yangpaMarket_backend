package com.example.yanpaMarket_backend.security; // security = 인증/토큰 관련 클래스 모음

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;                          // 응답 콘텐츠 타입(JSON) 상수
import org.springframework.security.core.AuthenticationException;   // 인증 실패 예외 타입
import org.springframework.security.web.AuthenticationEntryPoint;   // 인증 실패 시 호출되는 진입점 인터페이스
import org.springframework.stereotype.Component;                    // 스프링 빈 등록

/**
 * [무엇] 인증되지 않은 사용자가 보호된 API에 접근했을 때 호출되는 "인증 실패 응답기".
 * [어떻게 쓰임]
 *   - 토큰이 아예 없는데 인증 필요한 경로에 접근하면 스프링 시큐리티가 여기를 부른다.
 *   - 프로젝트 공통 실패 포맷(success/data/message/code)의 401 JSON을 직접 써서 응답한다.
 * [연결]
 *   - SecurityConfig 의 exceptionHandling 에 이 빈이 등록되어 있다.
 *   - (참고) 토큰이 "있지만 잘못된" 경우는 JwtAuthenticationFilter 가 직접 401을 응답.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    /**
     * 인증 실패 시 스프링이 호출. 401 + JSON 본문을 직접 작성한다.
     */
    @Override
    public void commence(
            HttpServletRequest request,            // 들어온 요청
            HttpServletResponse response,          // 우리가 채울 응답
            AuthenticationException authException   // 발생한 인증 예외
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);        // HTTP 401 설정
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);      // 본문 타입을 JSON으로
        response.getWriter().write(                                     // 공통 실패 응답 JSON을 직접 기록
                "{\"success\":false,\"data\":null,\"message\":\"인증이 필요합니다.\",\"code\":\"UNAUTHORIZED\"}"
        );
    }
}
