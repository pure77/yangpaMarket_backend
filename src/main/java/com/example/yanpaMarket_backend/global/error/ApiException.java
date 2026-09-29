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
     * 실패 응답의 data 로 그대로 나간다. 대부분의 예외는 null 이다.
     * [왜 Object 인가] 이 클래스는 "운반"만 한다. 타입은 실제 payload 쪽(예: BidTooLowData)이 갖는다.
     */
    private final Object data;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null);
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    private ApiException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    /**
     * [부가 정보를 실어 던진다] 거절 사유의 숫자를 클라이언트에게 전달해야 할 때만 쓴다.
     *
     * [왜 생성자가 아니라 정적 팩토리인가]
     *   ApiException(ErrorCode, String)이 이미 있다. 여기에 (ErrorCode, Object)를 더하면
     *   문자열 인자가 어느 쪽으로 가는지 읽는 사람이 헷갈린다(자바는 더 구체적인 String을 고른다).
     *   이름을 붙여 의도를 드러내는 편이 안전하다.
     */
    public static ApiException withData(ErrorCode errorCode, Object data) {
        return new ApiException(errorCode, errorCode.getDefaultMessage(), data);
    }
}
