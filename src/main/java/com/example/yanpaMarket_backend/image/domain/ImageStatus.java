package com.example.yanpaMarket_backend.image.domain; // image.domain = 이미지 도메인 패키지

/**
 * [무엇] 업로드된 이미지의 "수명주기 상태"를 나타내는 enum.
 * [흐름] 업로드 직후 UPLOADED → 경매에 연결되면 ATTACHED.
 * [연결]
 *   - Image.uploadStatus 컬럼에 문자열로 저장.
 *   - AuctionService 가 경매에 이미지를 붙일 때 Image.markAttached()로 ATTACHED 전환.
 */
public enum ImageStatus {
    UPLOADED, // 업로드만 된 상태(아직 어떤 경매에도 연결 안 됨)
    ATTACHED, // 경매에 연결된 상태
    DELETED   // 삭제된 상태
}
