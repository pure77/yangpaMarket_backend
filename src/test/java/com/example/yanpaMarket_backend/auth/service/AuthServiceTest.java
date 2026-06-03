package com.example.yanpaMarket_backend.auth.service;

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

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
    private AuthService authService;

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

        verify(userRepository, never()).save(any(User.class));
        verify(userSocialAccountRepository, never()).save(any(UserSocialAccount.class));
    }

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

    private List<TermsAgreementRequest> defaultAgreements() {
        return List.of(
                new TermsAgreementRequest("TERMS_OF_SERVICE", true, true),
                new TermsAgreementRequest("PRIVACY_POLICY", true, true),
                new TermsAgreementRequest("MARKETING", false, false)
        );
    }
}
