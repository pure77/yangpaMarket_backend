package com.example.yanpaMarket_backend.auth.service;

import com.example.yanpaMarket_backend.auth.client.KakaoOAuthClient;
import com.example.yanpaMarket_backend.auth.client.KakaoUserInfo;
import com.example.yanpaMarket_backend.auth.domain.AuthProvider;
import com.example.yanpaMarket_backend.auth.domain.UserSocialAccount;
import com.example.yanpaMarket_backend.auth.dto.KakaoCallbackRequest;
import com.example.yanpaMarket_backend.auth.dto.KakaoCallbackResponse;
import com.example.yanpaMarket_backend.auth.dto.KakaoLoginUrlResponse;
import com.example.yanpaMarket_backend.auth.dto.RefreshTokenRequest;
import com.example.yanpaMarket_backend.auth.dto.SignupCompleteRequest;
import com.example.yanpaMarket_backend.auth.dto.TermsAgreementRequest;
import com.example.yanpaMarket_backend.auth.dto.TokenResponse;
import com.example.yanpaMarket_backend.auth.repository.UserSocialAccountRepository;
import com.example.yanpaMarket_backend.global.error.ApiException;
import com.example.yanpaMarket_backend.global.error.ErrorCode;
import com.example.yanpaMarket_backend.security.JwtProvider;
import com.example.yanpaMarket_backend.security.KakaoSignupTokenPayload;
import com.example.yanpaMarket_backend.security.TokenType;
import com.example.yanpaMarket_backend.user.domain.User;
import com.example.yanpaMarket_backend.user.domain.UserStatus;
import com.example.yanpaMarket_backend.user.domain.UserTermsAgreement;
import com.example.yanpaMarket_backend.user.repository.UserRepository;
import com.example.yanpaMarket_backend.user.repository.UserTermsAgreementRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 도메인의 핵심 오케스트레이션 서비스.
 * 카카오 OAuth 콜백 처리, 회원가입 완료, 토큰 재발급/로그아웃 흐름을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final OAuthStateService oAuthStateService;
    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final UserTermsAgreementRepository userTermsAgreementRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;

    /**
     * 프론트가 카카오 로그인 페이지로 이동할 수 있도록 인가 URL + state를 발급합니다.
     */
    @Transactional(readOnly = true)
    public KakaoLoginUrlResponse getKakaoLoginUrl() {
        String state = oAuthStateService.issueState();
        String authorizeUrl = kakaoOAuthClient.buildAuthorizeUrl(state);
        return new KakaoLoginUrlResponse(authorizeUrl, state);
    }

    /**
     * 카카오 콜백(code/state)을 처리해 로그인 완료 또는 추가 정보 입력 단계로 분기합니다.
     * - 기존 완성 사용자: access/refresh 토큰 즉시 발급
     * - 신규/미완성 사용자: signupToken 발급 후 프로필 입력 유도
     */
    public KakaoCallbackResponse handleKakaoCallback(KakaoCallbackRequest request) {
        if (!oAuthStateService.validateAndConsume(request.state())) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "유효하지 않은 state 값입니다.");
        }

        KakaoUserInfo kakaoUserInfo = kakaoOAuthClient.getUserInfo(request.code(), request.redirectUri());

        User linkedUser = userSocialAccountRepository
                .findByProviderAndProviderUserId(AuthProvider.KAKAO, kakaoUserInfo.providerUserId())
                .map(UserSocialAccount::getUser)
                .orElse(null);

        if (linkedUser != null) {
            if (isCompleteUser(linkedUser)) {
                return KakaoCallbackResponse.authenticated(issueTokens(linkedUser));
            }
            return KakaoCallbackResponse.profileSetupRequired(
                    createKakaoSignupToken(kakaoUserInfo),
                    linkedUser.getPublicId(),
                    defaultString(linkedUser.getEmail(), kakaoUserInfo.email()),
                    defaultString(linkedUser.getNickname(), kakaoUserInfo.nickname())
            );
        }

        if (!isBlank(kakaoUserInfo.email())) {
            User existingUserByEmail = userRepository.findByEmail(kakaoUserInfo.email()).orElse(null);
            if (existingUserByEmail != null) {
                linkKakaoAccountIfAbsent(existingUserByEmail, kakaoUserInfo.providerUserId(), kakaoUserInfo.email());
                if (isCompleteUser(existingUserByEmail)) {
                    return KakaoCallbackResponse.authenticated(issueTokens(existingUserByEmail));
                }
                return KakaoCallbackResponse.profileSetupRequired(
                        createKakaoSignupToken(kakaoUserInfo),
                        existingUserByEmail.getPublicId(),
                        existingUserByEmail.getEmail(),
                        defaultString(existingUserByEmail.getNickname(), kakaoUserInfo.nickname())
                );
            }
        }

        return KakaoCallbackResponse.profileSetupRequired(
                createKakaoSignupToken(kakaoUserInfo),
                null,
                kakaoUserInfo.email(),
                kakaoUserInfo.nickname()
        );
    }

    /**
     * signupToken 기반으로 사용자 프로필/약관 동의를 확정하고 최종 로그인 토큰을 발급합니다.
     * 기존 이메일/소셜 계정이 있으면 갱신(upsert), 없으면 신규 생성합니다.
     */
    public TokenResponse completeSignup(SignupCompleteRequest request) {
        KakaoSignupTokenPayload tokenPayload = jwtProvider.getKakaoSignupTokenPayload(request.signupToken());
        if (isBlank(tokenPayload.providerUserId())) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "유효하지 않은 회원가입 토큰입니다.");
        }

        User existingUserBySocial = userSocialAccountRepository
                .findByProviderAndProviderUserId(AuthProvider.KAKAO, tokenPayload.providerUserId())
                .map(UserSocialAccount::getUser)
                .orElse(null);

        User existingUserByEmail = isBlank(tokenPayload.email())
                ? null
                : userRepository.findByEmail(tokenPayload.email()).orElse(null);

        User targetExistingUser = existingUserBySocial != null ? existingUserBySocial : existingUserByEmail;

        validatePhoneUniqueness(request.phone(), targetExistingUser);
        validateRequiredTerms(request.agreements());

        try {
            User user = upsertUserFromSignupRequest(request, tokenPayload, targetExistingUser);
            linkKakaoAccountIfAbsent(user, tokenPayload.providerUserId(), tokenPayload.email());
            replaceTermsAgreements(user, request.agreements());
            return issueTokens(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 가입된 회원 정보입니다.");
        }
    }

    /**
     * refresh token을 검증한 뒤 기존 refresh를 폐기하고 새 access/refresh를 재발급합니다.
     */
    public TokenResponse refresh(RefreshTokenRequest request) {
        String publicId = jwtProvider.getPublicId(request.refreshToken(), TokenType.REFRESH);
        User user = getUserByPublicId(publicId);
        if (!refreshTokenStore.isValid(user, request.refreshToken())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "유효하지 않은 refresh token입니다.");
        }
        refreshTokenStore.revoke(request.refreshToken());
        return issueTokens(user);
    }

    /**
     * 사용자에게 발급된 활성 refresh token들을 모두 폐기해 세션을 종료합니다.
     */
    public void logout(String publicId) {
        User user = getUserByPublicId(publicId);
        refreshTokenStore.revokeAll(user);
    }

    /**
     * 회원가입 완료 요청을 기준으로 사용자 정보를 신규 생성하거나 기존 미완성 계정을 보완합니다.
     */
    private User upsertUserFromSignupRequest(
            SignupCompleteRequest request,
            KakaoSignupTokenPayload tokenPayload,
            User existingUserByEmail
    ) {
        if (existingUserByEmail == null) {
            return userRepository.save(
                    User.builder()
                            .publicId(generatePublicId())
                            .email(tokenPayload.email())
                            .passwordHash(null)
                            .nickname(request.nickname())
                            .phone(request.phone())
                            .isAdmin(false)
                            .status(UserStatus.ACTIVE)
                            .marketingOptIn(request.marketingOptIn())
                            .profileImageUrl(tokenPayload.profileImageUrl())
                            .build()
            );
        }

        if (isCompleteUser(existingUserByEmail)) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 가입된 이메일 계정입니다.");
        }

        existingUserByEmail.completeProfile(request.nickname(), request.phone(), request.marketingOptIn());
        if (isBlank(existingUserByEmail.getProfileImageUrl()) && !isBlank(tokenPayload.profileImageUrl())) {
            existingUserByEmail.updateProfileImageUrl(tokenPayload.profileImageUrl());
        }
        return existingUserByEmail;
    }

    /**
     * 약관 동의는 최신 입력으로 전체 교체합니다.
     * (기존 동의 이력 삭제 후 요청 목록 재저장)
     */
    private void replaceTermsAgreements(User user, List<TermsAgreementRequest> agreements) {
        userTermsAgreementRepository.deleteByUserId(user.getId());
        userTermsAgreementRepository.saveAll(
                agreements.stream()
                        .map(agreement -> UserTermsAgreement.builder()
                                .user(user)
                                .termCode(agreement.termCode())
                                .required(agreement.isRequired())
                                .agreed(agreement.agreed())
                                .agreedAt(agreement.agreed() ? LocalDateTime.now() : null)
                                .revokedAt(null)
                                .build())
                        .toList()
        );
    }

    /**
     * 전화번호 중복을 검사합니다.
     * 기존 계정 보완 시에는 자기 자신을 제외하고 중복을 확인합니다.
     */
    private void validatePhoneUniqueness(String phone, User existingUserByEmail) {
        if (existingUserByEmail == null) {
            if (userRepository.existsByPhone(phone)) {
                throw new ApiException(ErrorCode.CONFLICT, "이미 사용 중인 전화번호입니다.");
            }
            return;
        }

        if (userRepository.existsByPhoneAndIdNot(phone, existingUserByEmail.getId())) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 사용 중인 전화번호입니다.");
        }
    }

    /**
     * 사용자에 카카오 소셜 계정 연결이 없을 때만 연결 레코드를 생성합니다.
     */
    private void linkKakaoAccountIfAbsent(User user, String providerUserId, String email) {
        boolean alreadyLinked = userSocialAccountRepository
                .findByUserIdAndProvider(user.getId(), AuthProvider.KAKAO)
                .isPresent();
        if (alreadyLinked) {
            return;
        }

        try {
            userSocialAccountRepository.save(
                    UserSocialAccount.builder()
                            .user(user)
                            .provider(AuthProvider.KAKAO)
                            .providerUserId(providerUserId)
                            .email(email)
                            .linkedAt(LocalDateTime.now())
                            .build()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 다른 계정에 연결된 카카오 계정입니다.");
        }
    }

    private String createKakaoSignupToken(KakaoUserInfo kakaoUserInfo) {
        return jwtProvider.createKakaoSignupToken(
                new KakaoSignupTokenPayload(
                        kakaoUserInfo.providerUserId(),
                        kakaoUserInfo.email(),
                        kakaoUserInfo.nickname(),
                        kakaoUserInfo.profileImageUrl()
                )
        );
    }

    /**
     * access/refresh token을 한 쌍으로 발급하고 refresh 저장소 상태를 교체합니다.
     */
    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getPublicId(), user.isAdmin());
        String refreshToken = jwtProvider.createRefreshToken(user.getPublicId());
        refreshTokenStore.replace(user, refreshToken, jwtProvider.getRefreshExpiryInstant());
        return new TokenResponse(
                user.getPublicId(),
                accessToken,
                refreshToken,
                jwtProvider.getAccessExpirationSeconds()
        );
    }

    private User getUserByPublicId(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    /**
     * 요청된 약관 목록에서 필수 약관이 모두 동의되었는지 검증합니다.
     */
    private void validateRequiredTerms(List<TermsAgreementRequest> agreements) {
        boolean hasRequiredTerms = agreements.stream().anyMatch(TermsAgreementRequest::isRequired);
        boolean allRequiredAgreed = agreements.stream()
                .filter(TermsAgreementRequest::isRequired)
                .allMatch(TermsAgreementRequest::agreed);
        if (!hasRequiredTerms || !allRequiredAgreed) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "필수 약관 동의가 필요합니다.");
        }
    }

    /**
     * 사용자 프로필 완성 상태를 판별합니다.
     * (ACTIVE 상태 + nickname/phone 존재)
     */
    private boolean isCompleteUser(User user) {
        return user.getStatus() == UserStatus.ACTIVE
                && !isBlank(user.getNickname())
                && !isBlank(user.getPhone());
    }

    private String defaultString(String primary, String fallback) {
        if (!isBlank(primary)) {
            return primary;
        }
        return fallback;
    }

    private String generatePublicId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

