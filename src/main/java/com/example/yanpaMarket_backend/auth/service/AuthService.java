package com.example.yanpaMarket_backend.auth.service; // auth.service = 인증 비즈니스 로직 계층

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
 * [무엇] 인증 도메인의 "핵심 지휘(오케스트레이션) 서비스".
 *        여러 협력 객체를 조합해 로그인/회원가입/토큰관리 전체 흐름을 처리한다.
 * [담당 흐름]
 *   - 카카오 로그인 URL 발급 → 콜백 처리(로그인 or 회원가입 분기)
 *   - 회원가입 완료(프로필/약관 확정)
 *   - 토큰 재발급(refresh) / 로그아웃
 * [연결]
 *   - 호출: AuthController 의 각 엔드포인트.
 *   - 협력: KakaoOAuthClient, OAuthStateService, JwtProvider, RefreshTokenStore,
 *           UserRepository, UserSocialAccountRepository, UserTermsAgreementRepository.
 *   - @Transactional: 메서드 전체를 하나의 DB 트랜잭션으로 묶어, 중간 실패 시 롤백되게 함.
 */
@Service
@RequiredArgsConstructor
@Transactional // 클래스 전체에 쓰기 트랜잭션 적용 (조회 전용 메서드는 readOnly로 오버라이드)
public class AuthService {

    // 인증 흐름에 필요한 협력 객체들 (생성자 주입)
    private final KakaoOAuthClient kakaoOAuthClient;                        // 카카오 인가 URL 생성 / 사용자 정보 조회
    private final OAuthStateService oAuthStateService;                      // CSRF 방어용 state 발급·검증
    private final UserRepository userRepository;                           // 사용자 조회·저장
    private final UserSocialAccountRepository userSocialAccountRepository; // 소셜 계정 연결 정보
    private final UserTermsAgreementRepository userTermsAgreementRepository; // 약관 동의 이력
    private final JwtProvider jwtProvider;                                 // access/refresh/signup 토큰 발급·파싱
    private final RefreshTokenStore refreshTokenStore;                     // refresh 토큰 저장·검증·폐기

    /**
     * 프론트가 카카오 로그인 페이지로 이동할 수 있도록 인가 URL + state를 발급합니다.
     */
    @Transactional(readOnly = true)
    public KakaoLoginUrlResponse getKakaoLoginUrl() {
        String state = oAuthStateService.issueState();                  // 위조 방지용 1회성 state 발급
        String authorizeUrl = kakaoOAuthClient.buildAuthorizeUrl(state); // state를 포함한 카카오 로그인 URL 생성
        return new KakaoLoginUrlResponse(authorizeUrl, state);          // 프론트로 URL + state 반환
    }

    /**
     * 카카오 콜백(code/state)을 처리해 로그인 완료 또는 추가 정보 입력 단계로 분기합니다.
     * - 기존 완성 사용자: access/refresh 토큰 즉시 발급
     * - 신규/미완성 사용자: signupToken 발급 후 프로필 입력 유도
     */
    public KakaoCallbackResponse handleKakaoCallback(KakaoCallbackRequest request) {
        // 1) state 검증: 우리가 발급한 값인지 확인 후 즉시 소비(재사용 차단)
        if (!oAuthStateService.validateAndConsume(request.state())) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "유효하지 않은 state 값입니다.");
        }

        // 2) 카카오에 code를 넘겨 실제 사용자 정보(이메일/닉네임 등) 조회
        KakaoUserInfo kakaoUserInfo = kakaoOAuthClient.getUserInfo(request.code(), request.redirectUri());

        // 3) 이 카카오 계정에 이미 연결된 사용자가 있는지 조회
        User linkedUser = userSocialAccountRepository
                .findByProviderAndProviderUserId(AuthProvider.KAKAO, kakaoUserInfo.providerUserId())
                .map(UserSocialAccount::getUser)
                .orElse(null);

        // 3-a) 연결된 사용자가 있으면: 프로필 완성 여부로 분기
        if (linkedUser != null) {
            if (isCompleteUser(linkedUser)) {
                return KakaoCallbackResponse.authenticated(issueTokens(linkedUser)); // 완성 → 즉시 로그인 토큰 발급
            }
            // 미완성 → 추가 정보 입력용 signupToken 발급 (이미 가진 값은 fallback으로 채움)
            return KakaoCallbackResponse.profileSetupRequired(
                    createKakaoSignupToken(kakaoUserInfo),
                    linkedUser.getPublicId(),
                    defaultString(linkedUser.getEmail(), kakaoUserInfo.email()),
                    defaultString(linkedUser.getNickname(), kakaoUserInfo.nickname())
            );
        }

        // 3-b) 소셜 연결은 없지만 같은 이메일의 기존 사용자가 있으면: 카카오 계정을 연결(account linking)
        if (!isBlank(kakaoUserInfo.email())) {
            User existingUserByEmail = userRepository.findByEmail(kakaoUserInfo.email()).orElse(null);
            if (existingUserByEmail != null) {
                linkKakaoAccountIfAbsent(existingUserByEmail, kakaoUserInfo.providerUserId(), kakaoUserInfo.email());
                if (isCompleteUser(existingUserByEmail)) {
                    return KakaoCallbackResponse.authenticated(issueTokens(existingUserByEmail)); // 완성 → 로그인
                }
                return KakaoCallbackResponse.profileSetupRequired( // 미완성 → 프로필 입력 유도
                        createKakaoSignupToken(kakaoUserInfo),
                        existingUserByEmail.getPublicId(),
                        existingUserByEmail.getEmail(),
                        defaultString(existingUserByEmail.getNickname(), kakaoUserInfo.nickname())
                );
            }
        }

        // 3-c) 완전 신규 사용자: signupToken만 발급해 회원가입 단계로 유도
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
        // 1) signupToken을 파싱해 카카오 사용자 식별 정보 복원 (위조/만료 시 예외)
        KakaoSignupTokenPayload tokenPayload = jwtProvider.getKakaoSignupTokenPayload(request.signupToken());
        if (isBlank(tokenPayload.providerUserId())) {
            throw new ApiException(ErrorCode.OAUTH_ERROR, "유효하지 않은 회원가입 토큰입니다.");
        }

        // 2) 소셜 연결 기준으로 기존 사용자 조회
        User existingUserBySocial = userSocialAccountRepository
                .findByProviderAndProviderUserId(AuthProvider.KAKAO, tokenPayload.providerUserId())
                .map(UserSocialAccount::getUser)
                .orElse(null);

        // 3) 이메일 기준으로도 기존 사용자 조회 (이메일이 있을 때만)
        User existingUserByEmail = isBlank(tokenPayload.email())
                ? null
                : userRepository.findByEmail(tokenPayload.email()).orElse(null);

        // 4) 보완 대상 결정: 소셜 연결 사용자 우선, 없으면 이메일 일치 사용자
        User targetExistingUser = existingUserBySocial != null ? existingUserBySocial : existingUserByEmail;

        validatePhoneUniqueness(request.phone(), targetExistingUser); // 전화번호 중복 검증(자기 자신 제외)
        validateRequiredTerms(request.agreements());                  // 필수 약관 동의 검증

        try {
            User user = upsertUserFromSignupRequest(request, tokenPayload, targetExistingUser); // 신규 생성 or 기존 보완
            linkKakaoAccountIfAbsent(user, tokenPayload.providerUserId(), tokenPayload.email()); // 소셜 연결 보장
            replaceTermsAgreements(user, request.agreements());                                 // 약관 동의 이력 교체
            return issueTokens(user);                                                           // 최종 로그인 토큰 발급
        } catch (DataIntegrityViolationException exception) {
            // 동시 가입 등으로 유니크 제약 위반 시 충돌로 변환
            throw new ApiException(ErrorCode.CONFLICT, "이미 가입된 회원 정보입니다.");
        }
    }

    /**
     * [테스트 전용] 카카오 없이 닉네임 기반으로 테스트 계정을 확보한 뒤 실제 로그인 토큰을 발급합니다.
     * - 같은 닉네임(→ 같은 email)이면 기존 계정을 재사용하므로 반복 호출해도 계정이 중복 생성되지 않습니다.
     * - 생성되는 계정은 status=ACTIVE + nickname/phone 보유 → isCompleteUser 조건을 만족(정상 로그인 사용자).
     * - dev 프로필의 DevAuthController에서만 호출되며, 운영 환경에서는 진입 경로 자체가 없습니다.
     */
    public TokenResponse devLogin(String nickname) {
        String email = nickname + "@test.local"; // 닉네임을 유일 이메일로 매핑 → 재호출 시 동일 계정 조회
        User user = userRepository.findByEmail(email).orElseGet(() ->
                userRepository.save(
                        User.builder()
                                .publicId(generatePublicId())           // 외부 노출용 식별자(JWT subject)
                                .email(email)
                                .passwordHash(null)                     // 소셜/테스트 계정 → 비밀번호 없음
                                .nickname(nickname)
                                .phone(generateTestPhone(nickname))     // phone 유니크 제약 회피용 결정적 번호
                                .isAdmin(false)
                                .status(UserStatus.ACTIVE)
                                .marketingOptIn(false)
                                .build()
                )
        );
        return issueTokens(user); // 기존 토큰 발급 로직 그대로 재사용(진짜 access/refresh 발급)
    }

    /**
     * refresh token을 검증한 뒤 기존 refresh를 폐기하고 새 access/refresh를 재발급합니다.
     */
    public TokenResponse refresh(RefreshTokenRequest request) {
        String publicId = jwtProvider.getPublicId(request.refreshToken(), TokenType.REFRESH); // 토큰에서 사용자 식별
        User user = getUserByPublicId(publicId);
        // 저장소 기준으로 실제 유효한(폐기 안 된) 토큰인지 확인
        if (!refreshTokenStore.isValid(user, request.refreshToken())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "유효하지 않은 refresh token입니다.");
        }
        refreshTokenStore.revoke(request.refreshToken()); // 기존 refresh 폐기 (토큰 회전: 재사용 차단)
        try {
            return issueTokens(user);                     // 새 access/refresh 한 쌍 발급
        } catch (DataIntegrityViolationException exception) {
            // 동시에 여러 번 갱신 요청이 들어와 토큰 저장이 충돌한 경우.
            // 여기서 잡지 않으면 DB 예외가 그대로 500으로 새어 나간다.
            throw new ApiException(ErrorCode.CONFLICT, "토큰 갱신이 동시에 요청되었습니다. 다시 시도해주세요.");
        }
    }

    /**
     * 사용자에게 발급된 활성 refresh token들을 모두 폐기해 세션을 종료합니다.
     */
    public void logout(String publicId) {
        User user = getUserByPublicId(publicId);
        refreshTokenStore.revokeAll(user); // 해당 사용자의 모든 활성 refresh 토큰 폐기 → 전 기기 로그아웃
    }

    /**
     * 회원가입 완료 요청을 기준으로 사용자 정보를 신규 생성하거나 기존 미완성 계정을 보완합니다.
     */
    private User upsertUserFromSignupRequest(
            SignupCompleteRequest request,
            KakaoSignupTokenPayload tokenPayload,
            User existingUserByEmail
    ) {
        // 기존 사용자가 없으면: 신규 가입 (소셜 가입이라 passwordHash는 null, 상태는 ACTIVE)
        if (existingUserByEmail == null) {
            return userRepository.save(
                    User.builder()
                            .publicId(generatePublicId())            // 외부 노출용 식별자
                            .email(tokenPayload.email())
                            .passwordHash(null)                      // 소셜 로그인 → 비밀번호 없음
                            .nickname(request.nickname())
                            .phone(request.phone())
                            .isAdmin(false)
                            .status(UserStatus.ACTIVE)
                            .marketingOptIn(request.marketingOptIn())
                            .profileImageUrl(tokenPayload.profileImageUrl())
                            .build()
            );
        }

        // 기존 사용자가 이미 완성 상태면 중복 가입 → 충돌 처리
        if (isCompleteUser(existingUserByEmail)) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 가입된 이메일 계정입니다.");
        }

        // 미완성 계정이면 프로필 정보를 채워 완성시킴 (보완)
        existingUserByEmail.completeProfile(request.nickname(), request.phone(), request.marketingOptIn());
        // 프로필 이미지가 비어있고 카카오가 준 이미지가 있으면 채워줌
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
        userTermsAgreementRepository.deleteByUserId(user.getId()); // 기존 동의 이력 전체 삭제
        userTermsAgreementRepository.saveAll(                      // 요청받은 최신 동의 목록으로 재저장
                agreements.stream()
                        .map(agreement -> UserTermsAgreement.builder()
                                .user(user)
                                .termCode(agreement.termCode())
                                .required(agreement.isRequired())
                                .agreed(agreement.agreed())
                                .agreedAt(agreement.agreed() ? LocalDateTime.now() : null) // 동의했으면 동의 시각 기록
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
        // 신규 가입: 전화번호가 이미 존재하면 충돌
        if (existingUserByEmail == null) {
            if (userRepository.existsByPhone(phone)) {
                throw new ApiException(ErrorCode.CONFLICT, "이미 사용 중인 전화번호입니다.");
            }
            return;
        }

        // 기존 계정 보완: 자기 자신(id 제외)을 빼고 같은 전화번호가 있으면 충돌
        if (userRepository.existsByPhoneAndIdNot(phone, existingUserByEmail.getId())) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 사용 중인 전화번호입니다.");
        }
    }

    /**
     * 사용자에 카카오 소셜 계정 연결이 없을 때만 연결 레코드를 생성합니다.
     */
    private void linkKakaoAccountIfAbsent(User user, String providerUserId, String email) {
        // 이미 카카오 연결이 있으면 중복 생성 방지
        boolean alreadyLinked = userSocialAccountRepository
                .findByUserIdAndProvider(user.getId(), AuthProvider.KAKAO)
                .isPresent();
        if (alreadyLinked) {
            return;
        }

        try {
            userSocialAccountRepository.save( // 새 소셜 연결 레코드 생성
                    UserSocialAccount.builder()
                            .user(user)
                            .provider(AuthProvider.KAKAO)
                            .providerUserId(providerUserId)
                            .email(email)
                            .linkedAt(LocalDateTime.now())
                            .build()
            );
        } catch (DataIntegrityViolationException exception) {
            // 같은 카카오 계정이 다른 사용자에 이미 연결된 경우 (유니크 제약 위반)
            throw new ApiException(ErrorCode.CONFLICT, "이미 다른 계정에 연결된 카카오 계정입니다.");
        }
    }

    // 카카오 사용자 정보를 회원가입 단계용 단기 토큰(signupToken)으로 포장
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
        String accessToken = jwtProvider.createAccessToken(user.getPublicId(), user.isAdmin()); // 단기 access 토큰
        String refreshToken = jwtProvider.createRefreshToken(user.getPublicId());               // 장기 refresh 토큰
        refreshTokenStore.replace(user, refreshToken, jwtProvider.getRefreshExpiryInstant());   // 저장소의 refresh 교체
        return new TokenResponse(
                user.getPublicId(),
                accessToken,
                refreshToken,
                jwtProvider.getAccessExpirationSeconds() // 프론트가 갱신 타이밍 계산에 쓰는 만료(초)
        );
    }

    // publicId로 사용자 조회, 없으면 404 예외
    private User getUserByPublicId(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    /**
     * 요청된 약관 목록에서 필수 약관이 모두 동의되었는지 검증합니다.
     */
    private void validateRequiredTerms(List<TermsAgreementRequest> agreements) {
        boolean hasRequiredTerms = agreements.stream().anyMatch(TermsAgreementRequest::isRequired); // 필수 약관 존재 여부
        boolean allRequiredAgreed = agreements.stream()
                .filter(TermsAgreementRequest::isRequired)
                .allMatch(TermsAgreementRequest::agreed); // 모든 필수 약관에 동의했는지
        // 필수 약관이 아예 없거나, 하나라도 미동의면 검증 실패
        if (!hasRequiredTerms || !allRequiredAgreed) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "필수 약관 동의가 필요합니다.");
        }
    }

    /**
     * 사용자 프로필 완성 상태를 판별합니다.
     * (ACTIVE 상태 + nickname/phone 존재)
     */
    private boolean isCompleteUser(User user) {
        // ACTIVE 상태이면서 닉네임/전화번호가 모두 채워졌을 때만 '완성된' 사용자로 판단
        return user.getStatus() == UserStatus.ACTIVE
                && !isBlank(user.getNickname())
                && !isBlank(user.getPhone());
    }

    // primary가 비어있으면 fallback 값을 사용 (null/공백 대비)
    private String defaultString(String primary, String fallback) {
        if (!isBlank(primary)) {
            return primary;
        }
        return fallback;
    }

    // 외부 노출용 publicId 생성 (UUID에서 하이픈 제거)
    private String generatePublicId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // [테스트 전용] 닉네임에서 결정적(deterministic) 테스트용 전화번호 생성.
    // phone 유니크 제약을 피하려고 닉네임 해시로 8자리 뒷번호를 만든다("010" + 8자리).
    private String generateTestPhone(String nickname) {
        int suffix = Math.abs(nickname.hashCode()) % 100_000_000; // 0 ~ 99,999,999 범위
        return "010" + String.format("%08d", suffix);
    }

    // null 또는 공백 문자열 여부
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

