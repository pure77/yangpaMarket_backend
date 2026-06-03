package com.example.yanpaMarket_backend.auth.client;

import com.example.yanpaMarket_backend.config.properties.KakaoProperties;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KakaoOAuthClient {

    private final KakaoProperties kakaoProperties;
    private final RestClient restClient;

    public KakaoOAuthClient(KakaoProperties kakaoProperties) {
        this.kakaoProperties = kakaoProperties;
        this.restClient = RestClient.builder().build();
    }

    /**
     * [카카오 인가 URL 생성]
     * 프론트가 이 URL로 이동하면 카카오 로그인/동의 화면이 열립니다.
     */
    public String buildAuthorizeUrl(String state) {
        validateClientConfig();
        URI uri = UriComponentsBuilder.fromUriString(kakaoProperties.getAuthUri())
                .queryParam("client_id", kakaoProperties.getClientId())
                .queryParam("redirect_uri", kakaoProperties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build(true)
                .toUri();
        return uri.toString();
    }

    /**
     * [카카오 사용자 정보 조회]
     * 1) 인가 코드(code)로 access token 교환
     * 2) access token으로 사용자 정보 조회
     * 3) 내부에서 필요한 최소 사용자 정보(id/email/nickname/image) 추출
     */
    @SuppressWarnings("unchecked")
    public KakaoUserInfo getUserInfo(String code, String redirectUri) {
        validateClientConfig();

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", kakaoProperties.getClientId());
        formData.add("client_secret", kakaoProperties.getClientSecret());
        formData.add("redirect_uri", redirectUri);
        formData.add("code", code);

        Map<String, Object> tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(kakaoProperties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "카카오 토큰 교환에 실패했습니다.");
        }

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "카카오 access token을 가져오지 못했습니다.");
        }

        String accessToken = String.valueOf(tokenResponse.get("access_token"));
        Map<String, Object> userResponse;
        try {
            userResponse = restClient.get()
                    .uri(kakaoProperties.getUserInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "카카오 사용자 조회에 실패했습니다.");
        }

        if (userResponse == null || userResponse.get("id") == null) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "카카오 사용자 식별자를 확인할 수 없습니다.");
        }

        String providerUserId = String.valueOf(userResponse.get("id"));
        Map<String, Object> kakaoAccount = (Map<String, Object>) userResponse.get("kakao_account");
        Map<String, Object> profile = kakaoAccount == null ? null : (Map<String, Object>) kakaoAccount.get("profile");

        String email = kakaoAccount == null ? null : (String) kakaoAccount.get("email");
        String nickname = profile == null ? null : (String) profile.get("nickname");
        String profileImage = profile == null ? null : (String) profile.get("profile_image_url");

        return new KakaoUserInfo(providerUserId, email, nickname, profileImage);
    }

    /**
     * 카카오 통신에 필요한 필수 설정값 존재 여부를 확인합니다.
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
