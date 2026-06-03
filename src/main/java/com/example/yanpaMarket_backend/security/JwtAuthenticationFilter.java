package com.example.yanpaMarket_backend.security;

import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청의 Authorization Bearer 토큰을 해석해 SecurityContext를 채우는 JWT 인증 필터입니다.
 * 토큰이 없으면 그대로 통과시키고, 토큰이 잘못되면 표준 에러 JSON(401)로 응답합니다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * 요청당 1회 실행되는 인증 진입점.
     * - Bearer 토큰 파싱
     * - publicId/권한(Role) 기반 Authentication 생성
     * - 검증 실패 시 인증 컨텍스트 정리 + 401 반환
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            // 인증 정보가 없는 요청은 다음 필터로 전달합니다. (인가 단계에서 최종 판단)
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7);
        try {
            String publicId = jwtProvider.getPublicId(token, TokenType.ACCESS);
            boolean isAdmin = jwtProvider.isAdmin(token);
            String role = isAdmin ? "ROLE_ADMIN" : "ROLE_USER";
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    publicId,
                    null,
                    List.of(new SimpleGrantedAuthority(role))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ApiException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(ErrorCode.UNAUTHORIZED.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"success\":false,\"data\":null,\"message\":\"" + exception.getMessage() + "\",\"code\":\"UNAUTHORIZED\"}"
            );
        }
    }
}
