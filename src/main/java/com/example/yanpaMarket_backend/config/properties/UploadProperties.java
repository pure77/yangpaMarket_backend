package com.example.yanpaMarket_backend.config.properties; // config.properties = 외부 설정값을 담는 클래스 모음

import org.springframework.boot.context.properties.ConfigurationProperties; // prefix로 묶인 설정값 바인딩

/**
 * [무엇] 로컬 디스크 이미지 업로드 설정 (로컬 개발 프로필에서 사용).
 *        application.properties 의 "app.upload.*" 값들이 자동 주입된다.
 * [어떻게 쓰임]
 *   - LocalImageStorage 가 이 값으로 저장 경로/서빙 URL/최대 용량을 결정한다.
 * [연결]
 *   - 운영에서는 대신 S3Properties(S3ImageStorage)를 사용.
 *   - 정적 서빙 매핑은 WebMvcConfig 에서 publicBaseUrl/dir 기준으로 설정.
 */
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
        String dir,            // 업로드 파일을 저장할 로컬 디렉터리 (기본: ./uploads)
        String publicBaseUrl,  // 브라우저가 접근할 서빙 URL 접두어 (기본: http://localhost:8080/uploads)
        long maxFileSizeBytes  // 허용 최대 파일 크기(바이트) (기본: 10MB)
) {
}
