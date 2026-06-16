package com.example.yanpaMarket_backend.image.domain; // image.domain = 이미지 도메인 패키지

/**
 * [무엇] 이미지 실제 파일이 저장되는 "저장소 종류"를 나타내는 enum.
 * [연결]
 *   - Image.storageProvider 컬럼에 저장.
 *   - 구현체: LocalImageStorage(LOCAL), S3ImageStorage(S3).
 *   - 현재 구현 범위에서는 LOCAL 만 사용(S3는 prod 확장용).
 */
public enum StorageProvider {
    S3,   // AWS S3 (운영 확장용)
    LOCAL // 로컬 디스크 (현재 사용)
}
