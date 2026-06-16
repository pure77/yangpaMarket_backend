package com.example.yanpaMarket_backend.image.dto; // image.dto = 이미지 API 요청/응답 객체 패키지

import com.example.yanpaMarket_backend.image.domain.Image;

/**
 * [무엇] 이미지 업로드 결과 응답 DTO.
 * [어떻게 쓰임]
 *   - POST /api/v1/images 의 응답 데이터.
 *   - 프론트는 받은 imageId 를 경매 등록 요청의 imageIds 에 넣고, url 로 미리보기를 띄운다.
 * [연결]
 *   - ImageService 가 저장된 Image 엔티티를 from()으로 변환해 반환.
 */
public record ImageUploadResponse(
        String imageId, // 외부 노출 식별자(public_id, ULID 26자)
        String url      // 정적 서빙 절대 URL(미리보기용)
) {

    /**
     * [변환 팩토리] 저장된 Image 엔티티 → 업로드 응답으로 매핑.
     */
    public static ImageUploadResponse from(Image image) {
        return new ImageUploadResponse(image.getPublicId(), image.getFileUrl());
    }
}
