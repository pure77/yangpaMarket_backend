package com.example.yanpaMarket_backend.image.storage; // image.storage = 이미지 파일 저장 전략 패키지

/**
 * [무엇] 저장소에 파일을 저장한 "결과"를 담는 데이터 묶음.
 * [어떻게 쓰임]
 *   - ImageStorage.store() 가 반환하고, ImageService 가 Image 엔티티에 옮겨 담는다.
 *
 * @param objectKey 저장소 내 실제 객체 키 (로컬: 파일명, S3: prefix 포함 키)
 * @param fileUrl   외부에서 이미지를 조회할 수 있는 서빙 URL
 */
public record StoredObject(String objectKey, String fileUrl) {
}
