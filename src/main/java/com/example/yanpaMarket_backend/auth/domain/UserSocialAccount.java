package com.example.yanpaMarket_backend.auth.domain; // auth.domain = 인증 도메인 엔티티 패키지

import com.example.yanpaMarket_backend.common.domain.BaseTimeEntity; // 생성/수정 시각 자동관리 부모
import com.example.yanpaMarket_backend.user.domain.User;             // 연결되는 우리 서비스 사용자
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

/**
 * [무엇] 우리 서비스 사용자와 소셜(카카오) 계정의 "연결 정보"를 담는 엔티티
 *        (= user_social_accounts 테이블 한 행).
 * [설계] 한 사용자가 여러 소셜 제공자를 연결할 수 있도록 User 와 N:1 관계.
 * [유니크 제약]
 *   - (provider + provider_user_id): 같은 카카오 계정을 두 사용자에 연결 불가.
 *   - (user_id + provider): 한 사용자당 같은 제공자는 1개만 연결.
 * [연결]
 *   - AuthService 가 카카오 로그인 시 "이 카카오 계정이 이미 가입돼 있나"를 이 테이블로 판단.
 *   - UserSocialAccountRepository 로 조회/저장.
 */
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

    /** 연결된 우리 서비스 사용자 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** OAuth 제공자 종류 (현재 KAKAO만) — 문자열로 저장 */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    /** 카카오가 발급한 고유 사용자 ID (제공자 내에서 유일) */
    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    /** 소셜 계정 이메일 (카카오 비공개 시 null) */
    @Column(name = "email", length = 255)
    private String email;

    /** 소셜 계정을 우리 계정에 연결한 시각 */
    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    /** 빌더 생성자. 소셜 연결 행을 만들 때 사용. */
    @Builder
    public UserSocialAccount(User user, AuthProvider provider, String providerUserId, String email, LocalDateTime linkedAt) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.linkedAt = linkedAt;
    }
}
