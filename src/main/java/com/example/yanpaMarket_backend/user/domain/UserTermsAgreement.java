package com.example.yanpaMarket_backend.user.domain;

import com.example.yanpaMarket_backend.common.domain.BaseTimeEntity;
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
        name = "user_terms_agreements",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_terms_agreements_user_term", columnNames = {"user_id", "term_code"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTermsAgreement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "term_code", nullable = false, length = 100)
    private String termCode;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "agreed", nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Builder
    public UserTermsAgreement(User user, String termCode, boolean required, boolean agreed, LocalDateTime agreedAt, LocalDateTime revokedAt) {
        this.user = user;
        this.termCode = termCode;
        this.required = required;
        this.agreed = agreed;
        this.agreedAt = agreedAt;
        this.revokedAt = revokedAt;
    }
}
