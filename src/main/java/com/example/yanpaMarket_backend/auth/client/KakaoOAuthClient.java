package com.example.yanpaMarket_backend.auth.client; // auth.client = 외부(카카오) API 호출 패키지

import com.example.yanpaMarket_backend.config.properties.KakaoProperties; // 카카오 URL/키 설정
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap; // form 데이터 담는 맵
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;     // HTTP 요청을 보내는 클라이언트
import org.springframework.web.util.UriComponentsBuilder; // 쿼리 파라미터 붙여 URL 만들기

/**
 * [무엇] 카카오 OAuth 서버와 실제로 통신하는 클라이언트.
 * [역할 2가지]
 *   1) 카카오 로그인(인가) URL 생성.
 *   2) 인가코드 → 액세스토큰 교환 → 사용자 정보 조회.
 * [어떻게 쓰임]
 *   - AuthService 가 로그인 흐름에서 이 클라이언트를 호출한다.
 * [연결]
 *   - 설정값은 KakaoProperties(app.kakao.*)에서 가져온다.
 *   - 통신 실패는 모두 OAUTH_ERROR 예외로 변환한다.
 */
@Component
public class KakaoOAuthClient {

    private final KakaoProperties kakaoProperties; // 카카오 엔드포인트/키 설정
    private final RestClient restClient;           // HTTP 호출기

    public KakaoOAuthClient(KakaoProperties kakaoProperties) {
        this.kakaoProperties = kakaoProperties;
        this.restClient = RestClient.builder().build(); // 기본 설정으로 RestClient 생성
    }

    /**
     * [1) 인가 URL 생성] 프론트가 이 URL로 이동하면 카카오 로그인/동의 화면이 열린다.
     * @param state CSRF 방지용 상태값(OAuthStateService가 발급)
     */
    public String buildAuthorizeUrl(String state) {
        validateClientConfig(); // 설정 누락 시 예외
        URI uri = UriComponentsBuilder.fromUriString(kakaoProperties.getAuthUri())
                .queryParam("client_id", kakaoProperties.getClientId())       // 우리 앱 키
                .queryParam("redirect_uri", kakaoProperties.getRedirectUri()) // 콜백 주소
                .queryParam("response_type", "code")                          // 인가코드 방식 요청
                .queryParam("state", state)                                   // 상태값 동봉
                .build(true)                                                  // true=이미 인코딩됨 처리
                .toUri();
        return uri.toString();
    }

    /**
     * [2) 사용자 정보 조회] 전체 흐름:
     *   ① 인가코드(code)로 액세스토큰 교환
     *   ② 액세스토큰으로 카카오 사용자 정보 조회
     *   ③ 필요한 최소 정보(id/email/nickname/image)만 추출해 반환
     */
    @SuppressWarnings("unchecked") // 카카오 응답 Map 캐스팅 경고 무시
    public KakaoUserInfo getUserInfo(String code, String redirectUri) {
        validateClientConfig();

        // ① 토큰 교환에 보낼 form 파라미터 구성
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");                // 인가코드 그랜트
        formData.add("client_id", kakaoProperties.getClientId());        // 앱 키
        formData.add("client_secret", kakaoProperties.getClientSecret());// 앱 시크릿
        formData.add("redirect_uri", redirectUri);                       // 콜백 주소(발급 때와 동일해야 함)
        formData.add("code", code);                                      // 1회용 인가코드

        Map<String, Object> tokenResponse;
        try {
            // 카카오 토큰 엔드포인트로 POST (application/x-www-form-urlencoded)
            tokenResponse = restClient.post()
                    .uri(kakaoProperties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "카카오 토큰 교환에 실패했습니다.");
        }

        // 응답에 access_token이 없으면 실패 처리
        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "카카오 access token을 가져오지 못했습니다.");
        }

        String accessToken = String.valueOf(tokenResponse.get("access_token")); // 카카오 액세스토큰
        Map<String, Object> userResponse;
        try {
            // ② 액세스토큰으로 사용자 정보 GET (Authorization 헤더에 Bearer 토큰)
            userResponse = restClient.get()
                    .uri(kakaoProperties.getUserInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "카카오 사용자 조회에 실패했습니다.");
        }

        // 사용자 고유 id가 없으면 실패 처리
        if (userResponse == null || userResponse.get("id") == null) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "카카오 사용자 식별자를 확인할 수 없습니다.");
        }

        // ③ 중첩 JSON 구조에서 필요한 값들 안전하게 추출 (없으면 null)
        String providerUserId = String.valueOf(userResponse.get("id")); // 카카오 사용자 ID
        Map<String, Object> kakaoAccount = (Map<String, Object>) userResponse.get("kakao_account"); // 계정 정보 블록
        Map<String, Object> profile = kakaoAccount == null ? null : (Map<String, Object>) kakaoAccount.get("profile"); // 프로필 블록

        String email = kakaoAccount == null ? null : (String) kakaoAccount.get("email");           // 이메일(동의 시)
        String nickname = profile == null ? null : (String) profile.get("nickname");               // 닉네임
        String profileImage = profile == null ? null : (String) profile.get("profile_image_url");  // 프로필 이미지

        return new KakaoUserInfo(providerUserId, email, nickname, profileImage); // 우리 형태로 묶어 반환
    }

    /**
     * [내부] 카카오 통신에 필요한 필수 설정값이 모두 있는지 확인. 누락 시 OAUTH_ERROR.
     */
    private void validateClientConfig() {
        if (isBlank(kakaoProperties.getClientId())
                || isBlank(kakaoProperties.getRedirectUri())
                || isBlank(kakaoProperties.getAuthUri())
                || isBlank(kakaoProperties.getTokenUri())
                || isBlank(kakaoProperties.getUserInfoUri())) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "카카오 OAuth 설정값이 누락되었습니다.");
        }
    }

    /** 문자열이 null이거나 공백인지 확인하는 헬퍼 */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
