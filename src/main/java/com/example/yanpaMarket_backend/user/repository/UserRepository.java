package com.example.yanpaMarket_backend.user.repository;

import com.example.yanpaMarket_backend.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPublicId(String publicId);
    Optional<User> findByEmail(String email);

    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, Long id);
}
