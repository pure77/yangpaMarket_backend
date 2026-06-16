package com.example.yanpaMarket_backend.user.repository; // user.repository = 사용자 DB 접근 계층

import com.example.yanpaMarket_backend.user.domain.User;
import java.util.Optional; // 결과가 없을 수 있음을 표현(null 대신)
import org.springframework.data.jpa.repository.JpaRepository; // 기본 CRUD 자동 제공

/**
 * [무엇] User 엔티티의 DB 접근(조회/저장/삭제)을 담당하는 Repository.
 * [어떻게 쓰임]
 *   - 인터페이스만 선언하면 Spring Data JPA가 구현체를 자동 생성한다.
 *   - 메서드 이름 규칙(findBy.../existsBy...)에 맞추면 쿼리도 자동 생성된다.
 * [연결]
 *   - JpaRepository<User, Long>: User 엔티티, PK 타입은 Long.
 *   - UserService, AuthService 등이 주입받아 사용한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPublicId(String publicId); // 외부 식별자(publicId)로 사용자 1건 조회
    Optional<User> findByEmail(String email);       // 이메일로 사용자 1건 조회(로그인 매칭 등)

    boolean existsByPhone(String phone);                       // 해당 전화번호 사용 사용자 존재 여부(중복 체크)
    boolean existsByPhoneAndIdNot(String phone, Long id);      // 나(id)를 제외하고 같은 전화번호 사용자 존재 여부(수정 시 중복 체크)
}
