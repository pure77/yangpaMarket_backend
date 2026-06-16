package com.example.yanpaMarket_backend.global.error; // global.error = 예외/에러코드 공통 패키지

import lombok.Getter;                  // @Getter: status, defaultMessage 의 getter 자동 생성
import lombok.RequiredArgsConstructor; // @RequiredArgsConstructor: final 필드를 받는 생성자 자동 생성(enum 상수 정의에 사용)
import org.springframework.http.HttpStatus; // HTTP 상태 코드(401,403,404 ...) 표준 enum

/**
 * [무엇] 프로젝트에서 발생할 수 있는 "모든 에러의 목록"을 한곳에 정의한 enum.
 *        각 상수는 (HTTP 상태코드 + 기본 메시지) 한 쌍을 갖는다.
 * [어떻게 쓰임]
 *   - 서비스에서 throw new ApiException(ErrorCode.XXX) 로 사용한다.
 *   - 상수 이름(name())이 그대로 프론트로 내려가는 에러 코드 문자열이 된다.
 * [연결]
 *   - ApiException 이 이 enum 값을 들고 다닌다.
 *   - GlobalExceptionHandler 가 status() 로 HTTP 응답 코드를, name() 으로 code 문자열을 만든다.
 *   - 코드/메시지 정의 기준은 docs/auction-project-spec.md (프론트와 공유하는 계약).
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 형식: 상수명(HTTP 상태, 기본 메시지)  // 발생 상황 설명
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),          // 토큰 없음/만료/타입 불일치 (401)
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),                  // 인증은 됐지만 자원 접근 불가 (403)
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),         // 일반 404
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."), // @Valid 실패, 입력값 오류 (400)
    CONFLICT(HttpStatus.CONFLICT, "중복 또는 충돌이 발생했습니다."),        // 이메일/전화번호 중복, 동시 충돌 (409)
    OAUTH_ERROR(HttpStatus.BAD_REQUEST, "소셜 인증 처리에 실패했습니다."),   // 카카오 API 오류, state 위조 등 (400)
    PROFILE_SETUP_REQUIRED(HttpStatus.BAD_REQUEST, "프로필 추가 입력이 필요합니다."), // 회원가입 2단계 필요 (400)
    AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND, "경매를 찾을 수 없습니다."),    // 경매 전용 404
    CANNOT_MODIFY(HttpStatus.BAD_REQUEST, "입찰자가 있어 수정/삭제할 수 없습니다."), // Auction.isModifiable() 실패 (400)
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "이미지 파일이 올바르지 않습니다."),  // 형식/크기/존재 오류 (400)
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."); // 예상치 못한 예외 (500)

    private final HttpStatus status;      // 이 에러를 응답할 때 사용할 HTTP 상태 코드
    private final String defaultMessage;  // 별도 메시지를 주지 않았을 때 쓰는 기본 설명 문구
}
