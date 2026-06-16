package com.example.yanpaMarket_backend.security; // security = 인증/토큰 관련 클래스 모음

import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import jakarta.servlet.FilterChain;            // 다음 필터로 요청을 넘기는 통로
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;   // "Authorization" 등 표준 헤더 이름 상수
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // 인증 완료 표현 객체
import org.springframework.security.core.authority.SimpleGrantedAuthority;              // 권한(ROLE) 표현
import org.springframework.security.core.context.SecurityContextHolder;                // 현재 요청의 인증정보 보관소
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter; // 요청당 1번만 실행되는 필터 베이스

/**
 * [무엇] 들어오는 요청의 "Authorization: Bearer {토큰}" 헤더를 읽어
 *        토큰을 검증하고, 통과하면 SecurityContext에 인증 정보를 채우는 필터.
 * [어떻게 동작]
 *   - 토큰이 없으면 → 그냥 통과(이후 인가 단계에서 공개/비공개 판단).
 *   - 토큰이 유효하면 → 사용자/권한 정보를 인증 컨텍스트에 등록.
 *   - 토큰이 잘못되면 → 공통 포맷의 401 JSON으로 즉시 응답.
 * [연결]
 *   - SecurityConfig 에서 기본 인증 필터 앞단에 배치된다.
 *   - JwtProvider 로 토큰을 해석한다.
 *   - 여기서 등록한 publicId 는 컨트롤러에서 @AuthenticationPrincipal 등으로 꺼내 쓴다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider; // 토큰 해석기

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * 요청당 1회 실행되는 인증 처리.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION); // Authorization 헤더 읽기
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            // 토큰이 없거나 형식이 다르면 인증 시도 없이 통과 (공개 API일 수 있으므로)
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7); // "Bearer " (7글자) 뒤의 실제 토큰만 추출
        try {
            String publicId = jwtProvider.getPublicId(token, TokenType.ACCESS); // ACCESS 토큰 검증 + 사용자 ID 추출
            boolean isAdmin = jwtProvider.isAdmin(token);                       // 관리자 여부 확인
            String role = isAdmin ? "ROLE_ADMIN" : "ROLE_USER";                // 권한 문자열 결정
            // 인증 완료 객체 생성: principal=publicId, credentials=null, 권한 목록 부여
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    publicId,
                    null,
                    List.of(new SimpleGrantedAuthority(role))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication); // 현재 요청의 인증정보로 등록
            filterChain.doFilter(request, response);                             // 다음 필터/컨트롤러로 진행
        } catch (ApiException exception) {
            // 토큰이 잘못된 경우: 인증정보 비우고 401 JSON 직접 응답
            SecurityContextHolder.clearContext();
            response.setStatus(ErrorCode.UNAUTHORIZED.getStatus().value()); // 401
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);      // JSON
            response.getWriter().write(                                     // 공통 실패 포맷으로 응답
                    "{\"success\":false,\"data\":null,\"message\":\"" + exception.getMessage() + "\",\"code\":\"UNAUTHORIZED\"}"
            );
        }
    }
}
