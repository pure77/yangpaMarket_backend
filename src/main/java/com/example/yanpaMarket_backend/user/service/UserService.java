package com.example.yanpaMarket_backend.user.service; // user.service = 사용자 비즈니스 로직 계층

import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor; // final 필드 생성자 주입
import org.springframework.stereotype.Service; // 서비스 빈 등록
import org.springframework.transaction.annotation.Transactional; // 트랜잭션 경계 설정

/**
 * [무엇] 사용자 조회 비즈니스 로직을 담당하는 서비스.
 * [어떻게 쓰임]
 *   - 컨트롤러(UserController)와 DB(UserRepository) 사이에서 조회 로직을 수행.
 * [연결]
 *   - @Transactional(readOnly=true): 이 클래스의 메서드는 읽기 전용 트랜잭션으로 실행(성능/안전).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository; // 사용자 DB 접근(생성자 주입)

    /**
     * [조회] publicId(=JWT subject)로 사용자를 찾는다.
     * 없으면 NOT_FOUND 비즈니스 예외를 던진다(→ GlobalExceptionHandler가 404 응답).
     */
    public User getByPublicId(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
