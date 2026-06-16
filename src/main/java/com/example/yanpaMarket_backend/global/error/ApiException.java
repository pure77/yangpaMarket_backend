package com.example.yanpaMarket_backend.global.error; // global.error = 예외/에러코드 공통 패키지

import lombok.Getter; // @Getter: errorCode 필드의 getter(getErrorCode())를 자동 생성

/**
 * [무엇] 서비스 계층에서 "의도적으로" 던지는 비즈니스 예외.
 *        어떤 에러인지(ErrorCode)를 함께 들고 다닌다.
 * [어떻게 쓰임]
 *   - 서비스 로직 도중 잘못된 상황을 만나면 throw new ApiException(ErrorCode.XXX) 로 던진다.
 *     예) 경매를 못 찾으면 throw new ApiException(ErrorCode.AUCTION_NOT_FOUND);
 * [연결]
 *   - GlobalExceptionHandler.handleApiException() 가 이 예외를 잡아서
 *     ErrorCode 의 HTTP 상태/코드로 ApiResponse.failure 응답을 만든다.
 *   - RuntimeException 상속 → 체크 예외가 아니므로 throws 선언 없이 던질 수 있고,
 *     던지면 진행 중이던 @Transactional 트랜잭션이 롤백된다.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode; // 이 예외가 의미하는 에러 종류(HTTP 상태/기본 메시지 포함)

    /**
     * [기본 메시지 사용] ErrorCode 에 정의된 defaultMessage 를 그대로 예외 메시지로 사용.
     */
    public ApiException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage()); // RuntimeException 의 message 를 기본 메시지로 설정
        this.errorCode = errorCode;           // 어떤 에러인지 저장
    }

    /**
     * [커스텀 메시지] 상황에 맞는 구체적 메시지를 직접 지정하고 싶을 때 사용.
     */
    public ApiException(ErrorCode errorCode, String message) {
        super(message);             // 전달받은 메시지를 예외 메시지로 설정
        this.errorCode = errorCode; // 에러 종류는 따로 저장(HTTP 상태 결정에 사용)
    }
}
