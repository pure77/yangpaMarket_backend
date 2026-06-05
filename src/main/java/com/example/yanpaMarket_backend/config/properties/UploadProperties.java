package com.example.yanpaMarket_backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로컬 이미지 업로드 설정.
 *
 * - dir: 업로드 파일 저장 디렉터리 (기본: ./uploads)
 * - publicBaseUrl: 정적 서빙 URL 접두어 (기본: http://localhost:8080/uploads)
 * - maxFileSizeBytes: 허용 최대 파일 크기 바이트 (기본: 10MB)
 */
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
        String dir,
        String publicBaseUrl,
        long maxFileSizeBytes
) {
}
