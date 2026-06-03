package com.example.yanpaMarket_backend.user.repository;

import com.example.yanpaMarket_backend.user.domain.UserTermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {
    void deleteByUserId(Long userId);
}
