package com.example.yanpaMarket_backend.auth.domain;

import com.example.yanpaMarket_backend.common.domain.BaseTimeEntity;
import com.example.yanpaMarket_backend.user.domain.User;
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

@Getter
@Entity
@Table(
        name = "auth_refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_auth_refresh_tokens_hash", columnNames = "token_hash")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthRefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Builder
    public AuthRefreshToken(User user, String tokenHash, LocalDateTime expiresAt, boolean revoked, LocalDateTime revokedAt, LocalDateTime lastUsedAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
        this.revokedAt = revokedAt;
        this.lastUsedAt = lastUsedAt;
    }

    public void revoke() {
        this.revoked = true;
        this.revokedAt = LocalDateTime.now();
    }

    public void markUsedNow() {
        this.lastUsedAt = LocalDateTime.now();
    }
}
