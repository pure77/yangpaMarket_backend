package com.example.yanpaMarket_backend.auction.controller;

import com.example.yanpaMarket_backend.auction.dto.BidHistoryResponse;
import com.example.yanpaMarket_backend.auction.dto.BidRequest;
import com.example.yanpaMarket_backend.auction.dto.BidResponse;
import com.example.yanpaMarket_backend.auction.service.BidService;
import com.example.yanpaMarket_backend.global.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 입찰 컨트롤러.
 * - POST /{auctionId}/bids : 인증 필요(입찰)
 * - GET  /{auctionId}/bids : 공개(입찰 내역)
 *
 * [이 클래스는 "번역기"다 — 의도적으로 얇다]
 *   컨트롤러가 하는 일은 네 가지뿐이다.
 *     (1) URL/HTTP 메서드 매핑       (2) HTTP 세계 → 자바 세계 번역(헤더/URL/JSON → 객체)
 *     (3) 서비스 호출                 (4) 자바 세계 → HTTP 세계 번역(반환값 → 공통 응답 포맷)
 *   비즈니스 로직은 단 한 줄도 없다. "현재가+최소 인상폭 이상인가", "본인 경매인가", "종료됐나",
 *   락, 트랜잭션은 전부 BidService와 Auction 도메인에 있다.
 *   → 덕분에 STOMP 핸들러나 배치 등 진입점이 늘어나도 BidService를 그대로 재호출하면 된다.
 *
 * [이 파일만 봐서는 알 수 없는 것 — 함께 봐야 하는 4개 파일]
 *   인증(누구인가)    : JwtAuthenticationFilter — Bearer 토큰 검증 후 publicId를 SecurityContext에 심는다
 *   인가(공개/비공개) : SecurityConfig          — GET만 permitAll, POST는 anyRequest().authenticated()
 *   에러 변환         : GlobalExceptionHandler  — ApiException → ErrorCode의 상태/코드로 변환
 *   도메인 규칙       : Auction.placeBid()      — 입찰가/본인/종료 검증
 *
 * [연결] BidService(비즈니스 로직) / ApiResponse(프론트와의 공유 응답 계약)
 */
@RestController // @Controller + @ResponseBody. 반환값을 뷰 이름이 아니라 응답 바디(JSON)로 직렬화한다.
                // 프론트가 별도 React 앱이라 서버는 JSON만 내보내면 되므로 이걸 쓴다.
// [중첩 리소스(nested resource) URL 설계]
//   "/api/v1/bids/{bidId}" 같은 독립 리소스가 아니라 경매 밑에 뒀다.
//   입찰은 경매 없이는 존재 의미가 없고("3번 입찰 보여줘"는 현실에 없는 요청),
//   조회는 항상 "이 경매의 입찰 내역" 단위다. 그 종속 관계를 URL에 그대로 표현한 것.
//   실용적 이점: {auctionId}를 클래스 레벨에 두면 두 메서드가 공유하고,
//   메서드 레벨 매핑은 @PostMapping / @GetMapping처럼 경로 없이 깔끔해진다.
// [왜 String auctionId인가] DB PK(Long id)가 아니라 publicId를 URL에 노출한다.
//   순차 증가 PK를 노출하면 전체 경매 개수를 추측할 수 있고 1,2,3...으로 전량 스크래핑이 가능하다.
//   외부 식별자(publicId)와 내부 PK를 분리하고, BidService가 findByPublicId로 내부 PK를 찾는다.
@RequestMapping("/api/v1/auctions/{auctionId}/bids")
// [생성자 주입] Lombok이 final 필드를 받는 생성자를 생성한다. 스프링은 생성자가 하나면 자동 주입.
//   필드 @Autowired 대신 생성자 주입을 쓰는 이유:
//     (1) 필드를 final로 만들어 불변 보장  (2) 순환 참조를 부팅 시점에 발견
//     (3) 테스트에서 new BidController(mockService)로 스프링 없이 생성 가능
// [@Validated] @RequestParam/@PathVariable에 건 제약(@Min/@Max)을 실제로 실행시키는 스위치.
//   @Valid(@RequestBody용)와 달리 파라미터 검증은 이 어노테이션이 클래스에 있어야 동작한다.
//   위반 시 ConstraintViolationException → GlobalExceptionHandler가 400 VALIDATION_ERROR로 변환.
@Validated
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    /**
     * 입찰 등록. 인증 필요. 성공 시 201 Created.
     *
     * [에러 — 전부 컨트롤러 코드 없이 GlobalExceptionHandler가 변환해 내려보낸다]
     *   400 BID_TOO_LOW / SELF_BID_NOT_ALLOWED / AUCTION_ENDED  ← Auction.placeBid
     *   409 ALREADY_BIDDING                                     ← BidService(낙관적 락 충돌)
     *   404 AUCTION_NOT_FOUND                                   ← BidService
     *   400 VALIDATION_ERROR                                    ← @Valid 실패
     *   401 UNAUTHORIZED                                        ← JwtAuthenticationFilter(컨트롤러 도달 전)
     *
     *   이 표 전체가 "입찰 쓰기를 STOMP가 아닌 REST로 받은 이유" 중 하나다.
     *   STOMP였다면 이 에러들을 클라이언트에 돌려줄 채널을 직접 설계해야 했다.
     */
    @PostMapping
    // [왜 201인가] 기본값은 200 OK. 입찰은 Bid 리소스를 새로 "생성"하는 행위라 201 Created가 의미상 맞다.
    //   GET에는 이 어노테이션이 없는데, 조회는 200이 기본값이라 명시할 필요가 없기 때문이다.
    //   (AuctionController.create()도 같은 규칙 — 프로젝트 전체가 일관됨)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BidResponse> placeBid(
            // [어노테이션이 없는 이유] 스프링 시큐리티가 SecurityContextHolder에서 꺼내 자동 주입한다.
            //   principal의 정체는 JwtAuthenticationFilter가 결정했다:
            //     new UsernamePasswordAuthenticationToken(publicId, null, 권한목록)
            //   즉 getPrincipal()이 publicId 문자열인 것은 필터와 컨트롤러 사이의 암묵적 계약이다.
            Authentication authentication,
            @PathVariable String auctionId,
            // [@RequestBody] JSON을 BidRequest 레코드로 역직렬화.
            // [@Valid] BidRequest의 @NotNull/@Positive를 실행. 실패 시 MethodArgumentNotValidException
            //   → GlobalExceptionHandler → 400 VALIDATION_ERROR + 그 message 문자열.
            //   덕분에 이 메서드 안에 if (amount == null) 같은 방어 코드가 한 줄도 없다.
            //
            // [검증 계층이 나뉜 이유]
            //   BidRequest(@Valid) : 형식 검증(null 아님, 양수) — DB 조회 없이 판단 가능하니 빨리 걸러낸다
            //   Auction.placeBid() : 도메인 규칙(최소 인상폭/본인/종료) — 경매의 "현재 상태"를 알아야 판단 가능
            //   도메인 규칙은 반드시 락 안에서 최신 상태를 재조회한 뒤 검사해야 한다.
            //   컨트롤러에서 미리 검사하면 그 사이 다른 사람이 입찰해버려 무의미해진다.
            @Valid @RequestBody BidRequest request
    ) {
        // [보안 핵심] 입찰자 신원을 요청 바디가 아니라 "검증된 토큰"에서 가져온다.
        //   BidRequest에 bidderId를 두면 클라이언트가 남의 ID를 적어 보내 위조할 수 있다.
        //   서버가 신뢰할 수 있는 신원은 서명이 검증된 토큰에서 뽑은 것뿐이다.
        //   → 그래서 BidRequest에는 amount 하나만 있다.
        // (String) 캐스팅이 필요한 이유: getPrincipal()의 반환 타입이 Object이기 때문.
        //   @AuthenticationPrincipal String publicId로 바꾸면 캐스팅을 없앨 수 있으나,
        //   AuctionController도 동일 스타일이라 지금은 일관성을 유지한다. 바꾸려면 함께 바꿀 것.
        return ApiResponse.success(
                bidService.placeBid((String) authentication.getPrincipal(), auctionId, request));
        // [try/catch가 없는 이유] BidService가 던진 ApiException은 GlobalExceptionHandler가 전역에서
        //   잡아 ApiResponse.failure(message, ErrorCode.name())로 변환한다. 컨트롤러마다 반복할 필요가 없다.
        // [ResponseEntity를 안 쓰는 이유] 상태코드는 @ResponseStatus(성공)나 ErrorCode(예외)가 이미
        //   결정하므로 감쌀 이유가 없다. ApiResponse를 직접 반환하면 코드가 짧아진다.
    }

    /**
     * 입찰 내역 조회. 비로그인 공개.
     *
     * [인증 비대칭 — 주의] POST에는 Authentication 파라미터가 있고 여기엔 없다.
     *   그런데 이 파일에는 인증/인가 설정이 전혀 없다. 규칙은 SecurityConfig에 있다.
     *     GET  /api/v1/auctions/(경매ID)/bids → permitAll (별도 매처로 명시)
     *     그 외                                → anyRequest().authenticated()  ← POST가 여기 걸린다
     *   "/api/v1/auctions/*" 매처는 한 세그먼트만 매칭하므로 bids 경로를 따로 적어준 것이다.
     *
     * [왜 GET은 공개인가] 비로그인 사용자도 "지금 12명이 입찰했고 현재가는 얼마"를 봐야 참여 유인이 생긴다.
     *   로그인 벽을 세우면 전환율이 떨어진다.
     *   대신 개인정보는 가린다 — BidHistoryResponse.from()이 NicknameMasker로 닉네임을 마스킹하고,
     *   bidderUserId는 응답에 아예 포함하지 않는다.
     */
    @GetMapping
    public ApiResponse<BidHistoryResponse> getBids(
            @PathVariable String auctionId,
            // [기본값] 쿼리 파라미터가 없으면 page=0, size=10.
            // [@Min(0)] page가 음수면 아래 PageRequest.of가 IllegalArgumentException을 던지고
            //   GlobalExceptionHandler의 Exception 최종 방어선에 걸려 500이 나갔다(400이어야 함).
            //   여기서 미리 막아 400 VALIDATION_ERROR로 응답한다.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // [@Min(1) @Max(100)] size 상한이 없으면 size=100000 요청 하나로 입찰 10만 건 +
            //   그 전원의 닉네임을 한 번에 조회한다. GET은 SecurityConfig에서 permitAll이라
            //   비로그인 사용자도 호출할 수 있어 부하 공격에 그대로 노출된다.
            //   프론트는 size=50으로 고정 호출하므로 상한 100은 여유가 있다.
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        // [계층 분리] HTTP 쿼리 파라미터를 Spring Data의 Pageable로 "번역"한다.
        //   덕분에 BidService는 HTTP를 전혀 모르고 "몇 번째 페이지, 몇 개씩"만 안다.
        //   스프링은 Pageable을 파라미터로 직접 받는 것도 지원하지만(PageableHandlerMethodArgumentResolver),
        //   기본값을 코드에서 눈에 보이게 통제하려고 명시적으로 만든다.
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(bidService.getBids(auctionId, pageable));
    }
}
