package com.example.yanpaMarket_backend.global.api; // global.api 패키지 = 전 도메인이 공유하는 응답 표준

/**
 * [무엇] 프로젝트 전체가 공유하는 "공통 API 응답 래퍼".
 *        모든 REST 응답을 { success, data, message, code } 형태로 통일한다.
 * [어떻게 쓰임]
 *   - 성공: success=true, data 채움 (message/code 는 null)
 *   - 실패: success=false, message/code 채움 (data 는 null)
 * [연결]
 *   - 모든 Controller(AuthController, AuctionController, UserController, ImageController)가
 *     반환 타입으로 사용한다.
 *   - 실패 응답은 GlobalExceptionHandler 가 ApiResponse.failure(...) 로 만들어 내려보낸다.
 *   - 프론트엔드의 응답 포맷(success/data/message/code)과 1:1로 맞춰진 "공유 계약".
 *
 * record = 불변(immutable) 데이터 운반 객체. 생성자/게터/equals/hashCode 자동 생성.
 * <T> = data 의 타입을 호출하는 쪽에서 결정하는 제네릭(예: ApiResponse<UserMeResponse>).
 */
public record ApiResponse<T>(
        boolean success, // 요청 성공 여부 (true=성공, false=실패)
        T data,          // 성공 시 실제 응답 데이터 (실패면 null)
        String message,  // 실패 설명 메시지 (또는 단순 성공 메시지)
        String code      // 실패 시 도메인 에러 코드 문자열 (ErrorCode.name(), 성공이면 null)
) {

    /**
     * [성공+데이터] data 를 담아 성공 응답을 만든다.
     * 예) return ApiResponse.success(userResponse);
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null); // success=true, 나머지 메시지/코드는 비움
    }

    /**
     * [성공+메시지] 돌려줄 데이터가 없고 안내 메시지만 있을 때 사용.
     * 반환 데이터 타입이 없으므로 제네릭을 Void 로 고정.
     */
    public static ApiResponse<Void> successMessage(String message) {
        return new ApiResponse<>(true, null, message, null); // success=true, data 없음
    }

    /**
     * [실패] 에러 메시지와 에러 코드를 담아 실패 응답을 만든다.
     * 주로 GlobalExceptionHandler 에서 호출된다.
     */
    public static ApiResponse<Void> failure(String message, String code) {
        return new ApiResponse<>(false, null, message, code); // success=false, data 없음
    }
}
