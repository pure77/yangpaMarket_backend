package com.example.yanpaMarket_backend.auth.domain;

import com.example.yanpaMarket_backend.common.domain.BaseTimeEntity;
import com.example.yanpaMarket_backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "user_social_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_social_accounts_provider_user", columnNames = {"provider", "provider_user_id"}),
                @UniqueConstraint(name = "uk_user_social_accounts_user_provider", columnNames = {"user_id", "provider"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSocialAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @Builder
    public UserSocialAccount(User user, AuthProvider provider, String providerUserId, String email, LocalDateTime linkedAt) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.linkedAt = linkedAt;
    }
}
