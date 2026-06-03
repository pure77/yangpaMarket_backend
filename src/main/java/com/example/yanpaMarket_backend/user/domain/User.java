package com.example.yanpaMarket_backend.user.domain;

import com.example.yanpaMarket_backend.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_phone", columnNames = "phone")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64)
    private String publicId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "nickname", length = 20)
    private String nickname;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "is_admin", nullable = false)
    private boolean isAdmin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Builder
    public User(
            String publicId,
            String email,
            String passwordHash,
            String nickname,
            String phone,
            boolean isAdmin,
            UserStatus status,
            boolean marketingOptIn,
            String profileImageUrl
    ) {
        this.publicId = publicId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.phone = phone;
        this.isAdmin = isAdmin;
        this.status = status;
        this.marketingOptIn = marketingOptIn;
        this.profileImageUrl = profileImageUrl;
    }

    public void completeProfile(String nickname, String phone, boolean marketingOptIn) {
        this.nickname = nickname;
        this.phone = phone;
        this.marketingOptIn = marketingOptIn;
        this.status = UserStatus.ACTIVE;
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
