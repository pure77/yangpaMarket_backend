package com.example.yanpaMarket_backend.config.properties; // config.properties = 외부 설정값을 담는 클래스 모음

import org.springframework.boot.context.properties.ConfigurationProperties; // prefix로 묶인 설정값 바인딩

/**
 * [무엇] AWS S3 이미지 저장 설정 (운영 prod 프로필에서만 사용).
 *        application.properties 의 "app.s3.*" 값들이 자동 주입된다.
 * [어떻게 쓰임]
 *   - S3Config / S3ImageStorage 가 이 값으로 버킷/리전/URL을 결정한다.
 * [연결]
 *   - 로컬 개발에서는 대신 UploadProperties(LocalImageStorage)를 사용.
 *   - record = 불변 설정 객체(생성자 바인딩).
 */
@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
        String bucket,        // 업로드 대상 S3 버킷 이름
        String region,        // 버킷 리전 (예: ap-northeast-2 = 서울)
        String keyPrefix,     // 객체 키 접두어 (예: "images/"). null/빈값이면 접두어 없음
        String publicBaseUrl  // 서빙용 도메인. 비우면 S3 기본 URL, 값 있으면 그 도메인(CloudFront 등) 사용
) {
}
