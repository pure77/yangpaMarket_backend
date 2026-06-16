package com.example.yanpaMarket_backend.auth.service; // 테스트 대상(AuthService)과 같은 패키지

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.yanpaMarket_backend.auth.client.KakaoOAuthClient;
import com.example.yanpaMarket_backend.auth.client.KakaoUserInfo;
import com.example.yanpaMarket_backend.auth.domain.AuthProvider;
import com.example.yanpaMarket_backend.auth.domain.UserSocialAccount;
import com.example.yanpaMarket_backend.auth.dto.KakaoCallbackRequest;
import com.example.yanpaMarket_backend.auth.dto.KakaoCallbackResponse;
import com.example.yanpaMarket_backend.auth.dto.SignupCompleteRequest;
import com.example.yanpaMarket_backend.auth.dto.TermsAgreementRequest;
import com.example.yanpaMarket_backend.auth.dto.TokenResponse;
import com.example.yanpaMarket_backend.auth.repository.UserSocialAccountRepository;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import com.example.yanpaMarket_backend.security.JwtProvider;
import com.example.yanpaMarket_backend.security.KakaoSignupTokenPayload;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import com.example.yanpaMarket_backend.user.repository.UserTermsAgreementRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [무엇] AuthService 의 로그인/회원가입 흐름을 검증하는 "단위 테스트".
 * [방식] 협력 객체(카카오 클라이언트, DB Repository, JwtProvider 등)를 모두 가짜(@Mock)로 대체해
 *        DB/네트워크 없이 AuthService 의 분기 로직만 빠르게 검증한다(Mockito).
 * [핵심 검증 포인트]
 *   - 신규 사용자는 회원가입 완료 전까지 DB에 저장되지 않는다.
 *   - 회원가입 완료 시 User/소셜연결/약관/리프레시토큰이 모두 저장된다.
 *   - 전화번호 중복 시 CONFLICT 예외, 그리고 아무것도 저장하지 않는다.
 *   - 이미 연결된 사용자는 즉시 토큰을 발급한다.
 */
@ExtendWith(MockitoExtension.class) // Mockito가 @Mock/@InjectMocks를 처리하도록 확장
class AuthServiceTest {

    // === 협력 객체들을 가짜(Mock)로 준비 ===
    @Mock
    private KakaoOAuthClient kakaoOAuthClient;
    @Mock
    private OAuthStateService oAuthStateService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;
    @Mock
    private UserTermsAgreementRepository userTermsAgreementRepository;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private AuthService authService; // 위 가짜들이 주입된, 실제 테스트 대상

    /** [콜백-신규] 신규 사용자는 signupToken만 받고, 회원가입 완료 전엔 DB 저장이 일어나지 않아야 한다. */
    @Test
    void handleKakaoCallback_NewUser_DoesNotPersistBeforeSignupComplete() {
        KakaoCallbackRequest request = new KakaoCallbackRequest("code", "state", "http://localhost/callback");
        KakaoUserInfo kakaoUserInfo = new KakaoUserInfo("kakao-user-1", "new@yangpa.com", "newbie", "http://image");

        when(oAuthStateService.validateAndConsume("state")).thenReturn(true);
        when(kakaoOAuthClient.getUserInfo("code", "http://localhost/callback")).thenReturn(kakaoUserInfo);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-user-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@yangpa.com")).thenReturn(Optional.empty());
        when(jwtProvider.createKakaoSignupToken(any(KakaoSignupTokenPayload.class))).thenReturn("signup-token");

        KakaoCallbackResponse response = authService.handleKakaoCallback(request);

        assertThat(response.requiresProfileSetup()).isTrue();
        assertThat(response.signupToken()).isEqualTo("signup-token");
        assertThat(response.userId()).isNull();
        assertThat(response.email()).isEqualTo("new@yangpa.com");
        assertThat(response.nickname()).isEqualTo("newbie");

        // 검증: 회원가입 완료 전이므로 어떤 저장도 호출되지 않아야 함
        verify(userRepository, never()).save(any(User.class));
        verify(userSocialAccountRepository, never()).save(any(UserSocialAccount.class));
    }

    /** [가입완료-신규] 회원가입 완료 시 User/소셜연결/약관/리프레시토큰이 모두 저장되고 토큰이 발급된다. */
    @Test
    void completeSignup_NewUser_PersistsUserSocialAndTerms() {
        SignupCompleteRequest request = new SignupCompleteRequest(
                "signup-token",
                "nickname-final",
                "010-1234-5678",
                true,
                defaultAgreements()
        );
        KakaoSignupTokenPayload payload = new KakaoSignupTokenPayload(
                "kakao-user-1",
                "new@yangpa.com",
                "kakao-nickname",
                "http://image"
        );

        when(jwtProvider.getKakaoSignupTokenPayload("signup-token")).thenReturn(payload);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-user-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@yangpa.com")).thenReturn(Optional.empty());
        when(userRepository.existsByPhone("010-1234-5678")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        when(userSocialAccountRepository.findByUserIdAndProvider(1L, AuthProvider.KAKAO)).thenReturn(Optional.empty());
        when(jwtProvider.createAccessToken(anyString(), anyBoolean())).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(anyString())).thenReturn("refresh-token");
        when(jwtProvider.getRefreshExpiryInstant()).thenReturn(Instant.now().plusSeconds(3600));
        when(jwtProvider.getAccessExpirationSeconds()).thenReturn(1800L);

        TokenResponse response = authService.completeSignup(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.userId()).isNotBlank();

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUserCaptor.capture());
        User savedUser = savedUserCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("new@yangpa.com");
        assertThat(savedUser.getNickname()).isEqualTo("nickname-final");
        assertThat(savedUser.getPhone()).isEqualTo("010-1234-5678");
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.isMarketingOptIn()).isTrue();

        verify(userSocialAccountRepository).save(any(UserSocialAccount.class));
        verify(userTermsAgreementRepository).deleteByUserId(1L);
        verify(userTermsAgreementRepository).saveAll(any(List.class));
        verify(refreshTokenStore).replace(any(User.class), eq("refresh-token"), any(Instant.class));
    }

    /** [가입완료-전화중복] 전화번호가 이미 존재하면 CONFLICT 예외를 던지고 아무것도 저장하지 않는다. */
    @Test
    void completeSignup_WhenPhoneAlreadyExists_ThrowsConflictAndDoesNotPersist() {
        SignupCompleteRequest request = new SignupCompleteRequest(
                "signup-token",
                "nickname-final",
                "010-1234-5678",
                false,
                defaultAgreements()
        );
        KakaoSignupTokenPayload payload = new KakaoSignupTokenPayload(
                "kakao-user-1",
                "new@yangpa.com",
                "kakao-nickname",
                "http://image"
        );

        when(jwtProvider.getKakaoSignupTokenPayload("signup-token")).thenReturn(payload);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-user-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@yangpa.com")).thenReturn(Optional.empty());
        when(userRepository.existsByPhone("010-1234-5678")).thenReturn(true);

        assertThatThrownBy(() -> authService.completeSignup(request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(userRepository, never()).save(any(User.class));
        verify(userSocialAccountRepository, never()).save(any(UserSocialAccount.class));
        verify(userTermsAgreementRepository, never()).saveAll(any(List.class));
    }

    /** [콜백-기존] 이미 카카오에 연결된 완성 사용자는 회원가입 없이 즉시 토큰을 발급받는다. */
    @Test
    void handleKakaoCallback_AlreadyLinkedUser_IssuesTokensImmediately() {
        KakaoCallbackRequest request = new KakaoCallbackRequest("code", "state", "http://localhost/callback");
        KakaoUserInfo kakaoUserInfo = new KakaoUserInfo("kakao-user-1", "existing@yangpa.com", "existing", "http://image");

        User linkedUser = User.builder()
                .publicId("public-id-1")
                .email("existing@yangpa.com")
                .nickname("existing")
                .phone("010-1111-2222")
                .isAdmin(false)
                .status(UserStatus.ACTIVE)
                .marketingOptIn(false)
                .profileImageUrl("http://image")
                .build();
        ReflectionTestUtils.setField(linkedUser, "id", 10L);

        UserSocialAccount socialAccount = UserSocialAccount.builder()
                .user(linkedUser)
                .provider(AuthProvider.KAKAO)
                .providerUserId("kakao-user-1")
                .email("existing@yangpa.com")
                .linkedAt(LocalDateTime.now())
                .build();

        when(oAuthStateService.validateAndConsume("state")).thenReturn(true);
        when(kakaoOAuthClient.getUserInfo("code", "http://localhost/callback")).thenReturn(kakaoUserInfo);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-user-1"))
                .thenReturn(Optional.of(socialAccount));
        when(jwtProvider.createAccessToken("public-id-1", false)).thenReturn("access-token");
        when(jwtProvider.createRefreshToken("public-id-1")).thenReturn("refresh-token");
        when(jwtProvider.getRefreshExpiryInstant()).thenReturn(Instant.now().plusSeconds(3600));
        when(jwtProvider.getAccessExpirationSeconds()).thenReturn(1800L);

        KakaoCallbackResponse response = authService.handleKakaoCallback(request);

        assertThat(response.requiresProfileSetup()).isFalse();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        verify(jwtProvider, never()).createKakaoSignupToken(any(KakaoSignupTokenPayload.class));
    }

    /** [헬퍼] 필수 약관 2개(동의) + 선택 약관 1개(미동의)로 구성된 기본 약관 동의 목록. */
    private List<TermsAgreementRequest> defaultAgreements() {
        return List.of(
                new TermsAgreementRequest("TERMS_OF_SERVICE", true, true),
                new TermsAgreementRequest("PRIVACY_POLICY", true, true),
                new TermsAgreementRequest("MARKETING", false, false)
        );
    }
}
