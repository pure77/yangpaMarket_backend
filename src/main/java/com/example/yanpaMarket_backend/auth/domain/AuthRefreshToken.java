package com.example.yanpaMarket_backend.auth.domain; // auth.domain = 인증 도메인 엔티티 패키지

import com.example.yanpaMarket_backend.common.domain.BaseTimeEntity; // 생성/수정 시각 자동관리 부모
import com.example.yanpaMarket_backend.user.domain.User;             // 토큰 소유자
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [무엇] 리프레시 토큰을 DB에 보관하는 엔티티 (= auth_refresh_tokens 테이블 한 행).
 * [보안 원칙] 토큰 "원문"은 저장하지 않고 SHA-256 해시(Base64)만 저장한다.
 *            (DB가 유출돼도 실제 토큰을 복원할 수 없게)
 * [정책] 한 사용자가 여러 토큰을 가질 수 있으나, 로그인/재발급 시 기존 토큰을 전부 폐기하고
 *        새 토큰 1개만 활성으로 유지한다 (MySqlRefreshTokenStore.replace 참조).
 * [연결]
 *   - User 와 N:1 관계.
 *   - AuthRefreshTokenRepository 로 조회/저장, MySqlRefreshTokenStore 가 사용.
 */
@Getter
@Entity
@Table(
        name = "auth_refresh_tokens",
        // 같은 토큰 해시가 두 번 저장되지 않도록 유니크 제약
        uniqueConstraints = @UniqueConstraint(name = "uk_auth_refresh_tokens_hash", columnNames = "token_hash")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthRefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 토큰을 소유한 사용자 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256(원문 토큰)을 Base64로 인코딩한 값 — 원문은 절대 저장하지 않음 */
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    /** 토큰 만료 시각 — 이 시각이 지나면 유효성 검증 실패 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 폐기 여부 — true면 재사용 불가 */
    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    /** 폐기된 시각 (null = 아직 유효) */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /** 마지막으로 검증에 사용된 시각 (사용 이력 추적) */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 빌더 생성자. 새 리프레시 토큰 기록을 만들 때 사용. */
    @Builder
    public AuthRefreshToken(User user, String tokenHash, LocalDateTime expiresAt, boolean revoked, LocalDateTime revokedAt, LocalDateTime lastUsedAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
        this.revokedAt = revokedAt;
        this.lastUsedAt = lastUsedAt;
    }

    /** [상태 변경] 토큰 폐기: revoked=true 로 바꾸고 폐기 시각을 기록 */
    public void revoke() {
        this.revoked = true;
        this.revokedAt = LocalDateTime.now();
    }

    /** [상태 변경] 토큰 사용 시각 갱신 (유효성 검증 성공 후 호출) */
    public void markUsedNow() {
        this.lastUsedAt = LocalDateTime.now();
    }
}
