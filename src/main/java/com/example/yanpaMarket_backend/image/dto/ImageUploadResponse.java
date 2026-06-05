package com.example.yanpaMarket_backend.image.dto;

import com.example.yanpaMarket_backend.image.domain.Image;

/**
 * 이미지 업로드 결과 DTO.
 *
 * - imageId: 외부 노출용 public_id (ULID 26자 문자열)
 * - url: 정적 서빙 절대 URL (프론트에서 미리보기에 사용)
 */
public record ImageUploadResponse(String imageId, String url) {

    /**
     * Image 엔티티로부터 응답 DTO를 생성한다.
     *
     * @param image 저장 완료된 Image 엔티티
     * @return imageId + url 응답 객체
     */
    public static ImageUploadResponse from(Image image) {
        return new ImageUploadResponse(image.getPublicId(), image.getFileUrl());
    }
}
